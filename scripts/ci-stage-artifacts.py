#!/usr/bin/env python3
"""Collect unsigned CI builds separately from the publisher's signed releases."""
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import zipfile


ROOT = Path(__file__).resolve().parents[1]


def main():
    configuration = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
    version_match = re.search(r'versionName\s*=\s*"([0-9A-Za-z.+-]+)"', configuration)
    if version_match is None:
        raise SystemExit("Cannot determine the application version")
    version = version_match.group(1)
    target = ROOT / "build/ci-artifacts"
    target.mkdir(parents=True, exist_ok=True)
    files = [
        ("app/build/outputs/apk/debug/app-debug.apk", f"wayfarer-atlas-{version}-debug.apk", "debug-key"),
        ("app/build/outputs/apk/release/app-release-unsigned.apk", f"wayfarer-atlas-{version}-unsigned-release.apk", "unsigned"),
        ("app/build/outputs/bundle/release/app-release.aab", f"wayfarer-atlas-{version}-unsigned-release.aab", "unsigned"),
    ]
    entries = []
    for relative, name, signing in files:
        source = ROOT / relative
        if not source.is_file():
            raise SystemExit(f"Missing expected CI output: {relative}")
        with zipfile.ZipFile(source) as archive:
            if archive.testzip() is not None:
                raise SystemExit(f"Damaged build archive: {relative}")
            if signing == "unsigned" and any(
                re.fullmatch(r"META-INF/[^/]+\.(RSA|DSA|EC)", item, re.IGNORECASE)
                for item in archive.namelist()
            ):
                raise SystemExit(f"Refusing to label a signed archive as unsigned: {relative}")
        shutil.copyfile(source, target / name)
        entries.append({
            "file": name,
            "bytes": source.stat().st_size,
            "sha256": hashlib.sha256(source.read_bytes()).hexdigest(),
            "signing": signing,
        })
    note = (
        "CI outputs for testing and build verification. The debug APK uses an ephemeral "
        "debug key and cannot update the signed public release. Release APK/AAB files "
        "are unsigned and require the publisher's existing upload key before distribution "
        "or submission to Google Play. Installable public builds are in GitHub Releases.\n"
    )
    (target / "README.txt").write_text(note, encoding="utf-8")
    (target / "manifest.json").write_text(json.dumps({
        "version": version,
        "commit": os.environ.get("GITHUB_SHA"),
        "files": entries,
    }, indent=2) + "\n", encoding="utf-8")
    (target / "SHA256SUMS.txt").write_text(
        "".join(f"{entry['sha256']}  {entry['file']}\n" for entry in entries), encoding="utf-8"
    )
    print(f"Prepared {len(entries)} clearly labelled CI outputs in build/ci-artifacts")


if __name__ == "__main__":
    main()
