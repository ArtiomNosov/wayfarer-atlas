#!/usr/bin/env python3
"""Normalize already downloaded public datasets. No network or credential access."""
import argparse
from datetime import date
import json
from pathlib import Path
from urllib.parse import urlparse


CATEGORIES = {
    "visa free": "VISA_FREE", "visa on arrival": "VISA_ON_ARRIVAL",
    "eta": "ETA", "e-visa": "E_VISA", "visa required": "VISA_REQUIRED",
    "no admission": "RESTRICTED",
}
COMMUNITY_SOURCE = "https://github.com/maxix7/visa-requirements-dataset"
COMMUNITY_CATEGORIES = {
    "visa_free": "VISA_FREE", "visa_on_arrival": "VISA_ON_ARRIVAL", "eta": "ETA",
    "evisa": "E_VISA", "visa_required": "VISA_REQUIRED", "no_admission": "RESTRICTED",
}


def optional_date(value):
    if value is None:
        return None
    if not isinstance(value, str) or len(value) != 10:
        raise ValueError("Invalid reference date")
    date.fromisoformat(value)
    return value


def normalize_community(source, country_codes):
    """VisaChecker's sourced layer, preserving missing verification as unknown."""
    if source.get("name") != "visa-requirements" or source.get("license") != "CC-BY-4.0":
        raise ValueError("Unexpected community source or license")
    matrix = source.get("rules")
    if not isinstance(matrix, dict):
        raise ValueError("Community source has no matrix")
    rules = []
    for citizenship, destinations in sorted(matrix.items()):
        if citizenship not in country_codes or not isinstance(destinations, dict):
            raise ValueError("Unknown community citizenship")
        for destination, raw in sorted(destinations.items()):
            if destination not in country_codes or not isinstance(raw, dict):
                raise ValueError("Unknown community destination")
            if citizenship == destination:
                continue
            days = raw.get("max_stay_days")
            if days is not None and (not isinstance(days, int) or isinstance(days, bool) or not 1 <= days <= 3660):
                raise ValueError("Invalid community stay duration")
            checked = optional_date(raw.get("verified"))
            primary = raw.get("source")
            if primary is not None and (not isinstance(primary, str) or len(primary) > 2048):
                raise ValueError("Invalid community source URL")
            notes = "Community reference; ordinary passport tourism; check official conditions."
            if checked is None:
                notes += " Not individually verified."
            url = urlparse(primary) if primary else None
            if url and url.scheme == "https" and url.hostname and not url.username and not url.password:
                reference = primary
            else:
                reference = COMMUNITY_SOURCE
                if primary:
                    notes += " Upstream reference: " + primary
            rules.append({"citizenship": citizenship, "destination": destination,
                "category": COMMUNITY_CATEGORIES.get(raw.get("requirement"), "UNKNOWN"),
                "maxStayDays": days, "notes": notes, "sourceUrl": reference, "checkedAt": checked})
    return {"version": 1, "updatedAt": optional_date(source.get("updated")),
            "source": COMMUNITY_SOURCE, "rules": rules}


def normalize_requirement(raw):
    value = str(raw).strip().lower()
    if value.isdigit() and 1 <= int(value) <= 366:
        return "VISA_FREE", int(value)
    return CATEGORIES.get(value, "UNKNOWN"), None


def normalize_matrix(matrix, country_codes, source_url):
    """Domestic cells are omitted; absent pairs remain unknown in the app."""
    rules = []
    for citizenship, destinations in sorted(matrix.items()):
        citizenship = citizenship.upper()
        if citizenship not in country_codes:
            raise ValueError(f"Unknown citizenship code: {citizenship}")
        for destination, raw in sorted(destinations.items()):
            destination = destination.upper()
            if destination not in country_codes:
                raise ValueError(f"Unknown destination code: {destination}")
            if citizenship == destination:
                continue
            value = raw.get("status") if isinstance(raw, dict) else raw
            category, days = normalize_requirement(value)
            if isinstance(raw, dict) and category == "VISA_FREE":
                candidate = raw.get("days")
                if isinstance(candidate, int) and not isinstance(candidate, bool) and 1 <= candidate <= 366:
                    days = candidate
            rules.append({
                "citizenship": citizenship, "destination": destination,
                "category": category, "maxStayDays": days,
                "notes": "Community dataset; check official entry conditions before travel.",
                "sourceUrl": source_url,
                # A fetch date is not evidence that somebody verified a visa rule.
                "checkedAt": None,
            })
    return rules


def normalize_geometry(source, country_codes):
    features = []
    for feature in source.get("features", []):
        props = feature.get("properties") or {}
        code = next((str(props.get(key, "")).upper() for key in (
            "ISO_A2_EH", "ISO_A2", "ISO3166-1-Alpha-2", "iso_a2", "code"
        ) if str(props.get(key, "")).upper() in country_codes), None)
        if code is None:
            continue
        geometry = feature.get("geometry") or {}
        if geometry.get("type") not in {"Polygon", "MultiPolygon"}:
            raise ValueError(f"Unsupported country geometry for {code}")
        features.append({"type": "Feature", "properties": {"code": code}, "geometry": geometry})
    return {"type": "FeatureCollection", "features": features}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--countries", type=Path, default=Path(__file__).with_name("countries.json"))
    parser.add_argument("--geometry", type=Path, help="Natural Earth source GeoJSON")
    parser.add_argument("--visa", type=Path, help="Downloaded VisaChecker JSON, or legacy imorte community matrix")
    parser.add_argument("--output", type=Path, default=Path(__file__).parent)
    parser.add_argument("--source-url", default="https://github.com/imorte/passport-index-data")
    parser.add_argument("--source-updated-at", default=None, help="Actual upstream publication date; never today's date by default")
    args = parser.parse_args()
    countries = json.loads(args.countries.read_text())
    codes = {item["code"] for item in countries["countries"]}
    args.output.mkdir(parents=True, exist_ok=True)
    if args.geometry:
        data = normalize_geometry(json.loads(args.geometry.read_text()), codes)
        (args.output / "world.geojson").write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":")) + "\n")
    if args.visa:
        matrix = json.loads(args.visa.read_text())
        data = normalize_community(matrix, codes) if matrix.get("name") == "visa-requirements" else {
            "version": 1, "updatedAt": args.source_updated_at, "source": args.source_url,
            "rules": normalize_matrix(matrix, codes, args.source_url)}
        (args.output / "rules.json").write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":")) + "\n")


if __name__ == "__main__":
    main()
