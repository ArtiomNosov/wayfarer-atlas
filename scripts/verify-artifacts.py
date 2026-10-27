#!/usr/bin/env python3
"""Verify public release signatures and ELF/ZIP alignment; never reads signing secrets."""
import hashlib
import json
import os
from pathlib import Path
import struct
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
APK = ROOT / "app/build/outputs/apk/release/app-release.apk"
AAB = ROOT / "app/build/outputs/bundle/release/app-release.aab"
SDK = Path(os.environ.get("ANDROID_HOME", ROOT / ".tooling/android-sdk"))
JDK = Path(os.environ.get("JAVA_HOME", ROOT / ".tooling/jdk/jdk-17.0.21+9/Contents/Home"))
PAGE = 16384


def elf_segments(data):
    if data[:4] != b"\x7fELF" or data[4] not in (1, 2) or data[5] not in (1, 2):
        raise ValueError("Invalid ELF header")
    wide = data[4] == 2
    order = "<" if data[5] == 1 else ">"
    word = "Q" if wide else "I"
    offset = struct.unpack_from(order + word, data, 32 if wide else 28)[0]
    size, count = struct.unpack_from(order + "HH", data, 54 if wide else 42)
    result = []
    for index in range(count):
        position = offset + index * size
        kind = struct.unpack_from(order + "I", data, position)[0]
        result.append({
            "type": kind,
            "flags": struct.unpack_from(order + "I", data, position + (4 if wide else 24))[0],
            "start": struct.unpack_from(order + word, data, position + (16 if wide else 8))[0],
            "size": struct.unpack_from(order + word, data, position + (40 if wide else 20))[0],
            "alignment": struct.unpack_from(order + word, data, position + (48 if wide else 28))[0],
        })
    return result


def relro_safe(segment, loads):
    # A partial final page is safe when no writable data follows RELRO in that page.
    # See the separate-LOAD example in developer.android.com/guide/practices/page-sizes.
    end = segment["start"] + segment["size"]
    if end % PAGE == 0:
        return True
    protected_start = segment["start"] // PAGE * PAGE
    protected_end = (end + PAGE - 1) // PAGE * PAGE
    for load in loads:
        if not load["flags"] & 2:
            continue
        start, stop = load["start"], load["start"] + load["size"]
        if start >= protected_end or stop <= protected_start:
            continue
        # Every overlapping writable LOAD byte must belong to the RELRO range.
        if max(start, protected_start) < segment["start"] or min(stop, protected_end) > end:
            return False
    return True


def main():
    environment = os.environ.copy()
    environment["JAVA_HOME"] = str(JDK)
    checks = {}
    commands = {
        "apkSignature": [str(SDK / "build-tools/36.0.0/apksigner"), "verify", "--verbose", str(APK)],
        "apkZipAlignment16k": [str(SDK / "build-tools/36.0.0/zipalign"), "-c", "-P", "16", "4", str(APK)],
        "aabSignature": [str(JDK / "bin/jarsigner"), "-verify", str(AAB)],
    }
    for label, arguments in commands.items():
        result = subprocess.run(arguments, capture_output=True, text=True, env=environment)
        passed = result.returncode == 0
        if label == "aabSignature":
            passed = passed and "jar verified." in result.stdout
        checks[label] = {"passed": passed, "exitCode": result.returncode}
        print(label, "PASS" if passed else "FAIL")
    checks["aabSignature"]["notes"] = (
        "Self-signed upload certificate, no timestamp. AGP ZIP ordering causes a JarInputStream "
        "warning; signed entries verify using JarFile. Run bundletool validate separately."
    )
    libraries = []
    with zipfile.ZipFile(APK) as archive:
        for name in archive.namelist():
            if not name.endswith(".so"):
                continue
            segments = elf_segments(archive.read(name))
            loads = [value for value in segments if value["type"] == 1]
            relros = [value for value in segments if value["type"] == 0x6474E552]
            passed = bool(loads) and all(value["alignment"] >= PAGE for value in loads)
            passed = passed and all(relro_safe(value, loads) for value in relros)
            libraries.append({"name": name, "passed": passed, "segments": segments})
            print("ELF16k", name, "PASS" if passed else "FAIL")
    checks["nativeLibraries"] = libraries
    checks["runtime16kDeviceTested"] = False
    checks["artifacts"] = {
        path.name: {"bytes": path.stat().st_size, "sha256": hashlib.sha256(path.read_bytes()).hexdigest()}
        for path in (APK, AAB)
    }
    (ROOT / "docs/release/artifact-verification.json").write_text(json.dumps(checks, indent=2) + "\n")
    passed = all(checks[label]["passed"] for label in commands)
    if not passed or any(not library["passed"] for library in libraries):
        raise SystemExit(1)


if __name__ == "__main__":
    main()
