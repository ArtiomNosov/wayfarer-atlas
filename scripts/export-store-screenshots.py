#!/usr/bin/env python3
"""Export opaque native captures as Play-compatible RGB PNGs, without changing RGB pixels.

Requires Pillow in the developer environment. Original captures stay in ignored build/.
"""
import hashlib
import json
from pathlib import Path
from PIL import Image

root = Path(__file__).resolve().parents[1]
source = root / "build/screenshot-captures"
destination = root / "docs/release/store-assets/screenshots"
destination.mkdir(parents=True, exist_ok=True)
paths = sorted(source.glob("*.png"))
if len(paths) != 8:
    raise SystemExit("Expected exactly eight native RU/EN screen captures")
manifest = []
for path in paths:
    with Image.open(path) as capture:
        if capture.size != (1080, 1920):
            raise SystemExit("Unexpected capture size: " + path.name)
        if "A" in capture.getbands() and capture.getchannel("A").getextrema() != (255, 255):
            raise SystemExit("Transparent screenshot cannot be exported without compositing: " + path.name)
        rgb = capture.convert("RGB")
        output = destination / path.name
        rgb.save(output, format="PNG", optimize=True)
        with Image.open(output) as exported:
            if exported.mode != "RGB" or exported.tobytes() != rgb.tobytes():
                raise SystemExit("RGB pixel verification failed: " + path.name)
    manifest.append({
        "file": path.name, "width": 1080, "height": 1920, "format": "24-bit RGB PNG; no alpha",
        "bytes": output.stat().st_size, "sha256": hashlib.sha256(output.read_bytes()).hexdigest(),
        "nativeCaptureSha256": hashlib.sha256(path.read_bytes()).hexdigest(),
        "language": path.name[:2], "screen": path.stem.split("-", 2)[2],
    })
metadata = json.loads((source / "capture-metadata.json").read_text())
metadata["storeExport"] = "Opaque alpha channel removed; all RGB pixels verified unchanged; no retouching"
(destination / "capture-metadata.json").write_text(json.dumps(metadata, indent=2) + "\n")
(destination / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
print("Exported and pixel-verified eight 24-bit RGB native screenshots")
