#!/usr/bin/env python3
"""Package only public source/release outputs. Signing secrets are never included."""
import hashlib
import json
import re
from pathlib import Path
import shutil
import zipfile

ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / "dist"
DEST.mkdir(exist_ok=True)
version = re.search(r'versionName\s*=\s*"([0-9]+\.[0-9]+\.[0-9]+)"', (ROOT / "app/build.gradle.kts").read_text()).group(1)
artifacts = {
    f"wayfarer-atlas-{version}-debug.apk": ROOT / "app/build/outputs/apk/debug/app-debug.apk",
    f"wayfarer-atlas-{version}-release.apk": ROOT / "app/build/outputs/apk/release/app-release.apk",
    f"wayfarer-atlas-{version}.aab": ROOT / "app/build/outputs/bundle/release/app-release.aab",
}
for name, source in artifacts.items():
    if not source.is_file():
        raise SystemExit("Required verified build output is missing: " + source.name)
    shutil.copyfile(source, DEST / name)

excluded = {".git", ".gradle", ".kotlin", ".tooling", "build", "dist", "release-private", "__pycache__"}
source_zip = DEST / f"wayfarer-atlas-{version}-source.zip"
with zipfile.ZipFile(source_zip, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=6) as output:
    for path in sorted(ROOT.rglob("*")):
        relative = path.relative_to(ROOT)
        if any(part in excluded for part in relative.parts) or not path.is_file() or path.is_symlink():
            continue
        if path.name in {"local.properties", "keystore.properties", ".DS_Store"} or path.name.startswith(".env") or path.suffix in {".jks", ".keystore", ".pyc"}:
            continue
        output.write(path, Path("wayfarer-atlas") / relative)

manifest = {}
for path in [*(DEST / name for name in artifacts), source_zip]:
    if path.is_file():
        manifest[path.name] = {"bytes": path.stat().st_size, "sha256": hashlib.sha256(path.read_bytes()).hexdigest()}
(DEST / "manifest.json").write_text(json.dumps({"version": version, "artifacts": manifest}, indent=2) + "\n")
(DEST / "SHA256SUMS.txt").write_text("".join(value["sha256"] + "  " + name + "\n" for name, value in manifest.items()))
print("Public release artifacts packaged; private signing files excluded.")
