#!/usr/bin/env python3
"""Copy only normalized, attributable reference assets into the Android app."""
import json
from pathlib import Path
import shutil

root = Path(__file__).resolve().parents[1]
source = root / "data"
target = root / "app/src/main/assets"
target.mkdir(parents=True, exist_ok=True)
for name in ("countries.json", "rules.json", "world.geojson", "update-source.json", "provenance.json", "official-sources.json", "official-hong-kong.json", "official-new-zealand.json", "official-macao.json", "hong-kong-mapping.json"):
    path = source / name
    if path.exists():
        json.loads(path.read_text())
        shutil.copyfile(path, target / name)
    elif name == "countries.json":
        raise SystemExit("countries.json is required")
notices = ["Wayfarer Atlas — data attribution\nCountry names: Unicode CLDR / ICU. Country codes: IANA tzdata (public domain).\n"]
for path in (source / "data-attribution.txt", source / "Unicode-3.0.txt",
             source / "official-attribution.txt", source / "sources/passport-index-MIT.txt", source / "sources/visa-requirements-CC-BY-4.0.txt"):
    if path.exists():
        notices.append(path.read_text())
(target / "licenses.txt").write_text("\n\n".join(notices))
rules = json.loads((target / "rules.json").read_text()) if (target / "rules.json").exists() else {"rules": []}
geometry = json.loads((target / "world.geojson").read_text()) if (target / "world.geojson").exists() else {"features": []}
print(f"Packaged {len(rules['rules'])} rules and {len(geometry['features'])} country outlines; notices included.")
