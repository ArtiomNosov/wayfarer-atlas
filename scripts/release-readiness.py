#!/usr/bin/env python3
"""Report concrete release blockers. Does not read keys or publish anything."""
import json
import os
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
blockers = []
def require(condition, message):
    if not condition:
        blockers.append(message)

assets = root / "app/src/main/assets"
try:
    rules = json.loads((assets / "rules.json").read_text())
    require(len(rules.get("rules", [])) > 0, "A verified, attributable visa-rule snapshot is missing.")
except (OSError, ValueError):
    blockers.append("The packaged visa-rule file is missing or invalid.")
require((assets / "world.geojson").exists(), "Country map geometry has not been packaged.")
require((assets / "update-source.json").exists(), "The real reference-update endpoint has not been verified/configured.")
policy = (root / "docs/privacy-policy.html").read_text()
require(not re.search(r"\[(?:PUBLISHER|SUPPORT|PRIVACY|EFFECTIVE)[A-Z_]*\]", policy),
        "Publisher/support/effective-date fields in the privacy policy are unresolved.")
require((root / "app/build/outputs/apk/debug/app-debug.apk").exists(), "No successfully built debug APK exists.")
require((root / "app/build/outputs/bundle/release/app-release.aab").exists(), "No successfully built release AAB exists.")
screenshots = list((root / "docs/release/store-assets/screenshots").glob("*.png"))
require(len(screenshots) >= 2, "At least two real application screenshots are required.")
require(all(bool(os.environ.get(name)) for name in ("WAYFARER_KEYSTORE", "WAYFARER_STORE_PASSWORD", "WAYFARER_KEY_PASSWORD")),
        "Upload signing has not been configured in the local environment (only presence is checked; no values are printed).")
if blockers:
    print("NOT RELEASE READY")
    for blocker in blockers:
        print("- " + blocker)
    raise SystemExit(1)
print("Local prerequisites present. This check does not prove tests passed, signatures are valid, or Play Console approval.")
