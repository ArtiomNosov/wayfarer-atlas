#!/usr/bin/env python3
"""Fetch public reference sources explicitly; no accounts, keys, or user travel data.

See ../docs/data-sources.md for attribution and known coverage/freshness limits.
"""
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import tempfile
from urllib.parse import urlparse
from urllib.request import HTTPRedirectHandler, Request, build_opener

from normalize import normalize_geometry, normalize_community

ROOT = Path(__file__).resolve().parent
ALLOWED_HOSTS = {"raw.githubusercontent.com", "api.github.com", "data.iana.org", "unicode.org", "www.unicode.org"}
NATURAL_EARTH = "https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_110m_admin_0_countries.geojson"
IANA_CODES = "https://data.iana.org/time-zones/tzdb/iso3166.tab"
CLDR_ROOT = "https://raw.githubusercontent.com/unicode-org/cldr-json/main/cldr-json/cldr-localenames-full/main"
UNICODE_LICENSE = "https://www.unicode.org/license.txt"
COMMUNITY_REPO = "https://api.github.com/repos/maxix7/visa-requirements-dataset"


def check_url(url):
    parsed = urlparse(url)
    if parsed.scheme != "https" or parsed.hostname not in ALLOWED_HOSTS or parsed.username or parsed.password:
        raise ValueError("Source URL must be HTTPS on the explicit public-source allowlist")


class SourceRedirects(HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, message, headers, newurl):
        check_url(newurl)
        return super().redirect_request(request, fp, code, message, headers, newurl)


def download(url, limit=5_000_000):
    check_url(url)
    request = Request(url, headers={"User-Agent": "WayfarerAtlas-ReferenceBuilder/0.1", "Accept": "application/json, text/plain, */*"})
    with build_opener(SourceRedirects()).open(request, timeout=25) as response:
        content = response.read(limit + 1)
    if len(content) > limit:
        raise ValueError("Public source exceeds expected size; existing data preserved")
    return content


def save(path, content):
    path.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(dir=path.parent, delete=False) as temp:
        temp.write(content)
        temporary = Path(temp.name)
    temporary.replace(path)


def json_bytes(value):
    return (json.dumps(value, ensure_ascii=False, separators=(",", ":")) + "\n").encode()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--geometry", action="store_true")
    parser.add_argument("--country-names", action="store_true")
    parser.add_argument("--community-visa", action="store_true", help="Download CC BY 4.0 community reference; not an official border decision service")
    args = parser.parse_args()
    if not (args.geometry or args.country_names or args.community_visa):
        parser.error("Choose --geometry, --country-names and/or --community-visa")
    manifest_path = ROOT / "download-manifest.json"
    manifest = json.loads(manifest_path.read_text()) if manifest_path.exists() else {"sources": []}
    manifest["retrievedAt"] = datetime.now(timezone.utc).isoformat()

    def source(url, filename, limit=5_000_000):
        content = download(url, limit)
        save(ROOT / "sources" / filename, content)
        manifest["sources"] = [item for item in manifest["sources"] if item["file"] != "sources/" + filename]
        manifest["sources"].append({"url": url, "file": "sources/" + filename,
            "retrievedAt": manifest["retrievedAt"], "sha256": hashlib.sha256(content).hexdigest(), "bytes": len(content)})
        return content

    if args.country_names:
        codes = source(IANA_CODES, "iso3166.tab", 100_000).decode("utf-8")
        names = {}
        for language in ("en", "ru"):
            data = json.loads(source(f"{CLDR_ROOT}/{language}/territories.json", f"territories-{language}.json", 500_000))
            names[language] = data["main"][language]["localeDisplayNames"]["territories"]
        source(UNICODE_LICENSE, "Unicode-3.0.txt", 50_000)
        country_codes = [line.split("\t")[0] for line in codes.splitlines() if line and not line.startswith("#")]
        if not 240 <= len(country_codes) <= 260:
            raise ValueError("Unexpected IANA country list size")
        country_codes = sorted(set(country_codes) | {"XK"})
        countries = [{"code": code, "nameEn": names["en"][code], "nameRu": names["ru"][code],
                      "centroidLon": None, "centroidLat": None, "isoAssigned": code != "XK"} for code in country_codes]
        save(ROOT / "countries.json", json_bytes({"version": 1, "updatedAt": manifest["retrievedAt"],
             "source": "IANA tzdata iso3166.tab; Unicode CLDR (see download-manifest.json)", "countries": countries}))

    countries = json.loads((ROOT / "countries.json").read_text())
    codes = {country["code"] for country in countries["countries"]}
    if args.geometry:
        metadata = json.loads(source("https://api.github.com/repos/nvkelso/natural-earth-vector/commits/master", "natural-earth-commit.json", 1_000_000))
        geometry_url = NATURAL_EARTH.replace("/master/", "/" + metadata["sha"] + "/")
        geometry = json.loads(source(geometry_url, "ne_110m_admin_0_countries.geojson", 5_000_000))
        normalized = normalize_geometry(geometry, codes)
        if len(normalized["features"]) < 150:
            raise ValueError("Natural Earth geometry is unexpectedly incomplete")
        save(ROOT / "world.geojson", json_bytes(normalized))
    if args.community_visa:
        commit = json.loads(source(f"{COMMUNITY_REPO}/commits/main", "visa-commit.json", 1_000_000))["sha"]
        base = f"https://raw.githubusercontent.com/maxix7/visa-requirements-dataset/{commit}"
        source(base + "/LICENSE", "visa-requirements-CC-BY-4.0.txt", 100_000)
        package = json.loads(source(base + "/data/datapackage.json", "visa-datapackage.json", 100_000))
        if not any(item.get("name") == "CC-BY-4.0" for item in package.get("licenses", [])):
            raise ValueError("Community source license changed")
        source("https://raw.githubusercontent.com/imorte/passport-index-data/master/LICENSE", "passport-index-MIT.txt", 100_000)
        matrix = json.loads(source(base + "/data/visa-requirements.json", "visa-requirements.json", 10_000_000))
        normalized = normalize_community(matrix, codes)
        known = sum(rule["category"] != "UNKNOWN" for rule in normalized["rules"])
        if len(matrix["rules"]) < 190 or len(normalized["rules"]) < 35_000 or known < 35_000:
            raise ValueError("Community visa matrix is unexpectedly incomplete")
        save(ROOT / "rules.json", json_bytes(normalized))
        manifest["upstreamCommit"] = commit
        manifest["visaRuleVerificationDates"] = "Copied only from upstream row verified; missing dates remain null"
    save(ROOT / "download-manifest.json", json_bytes(manifest))
    print(json.dumps({"downloadedSources": len(manifest["sources"]), "manifest": str(ROOT / "download-manifest.json")}))


if __name__ == "__main__":
    main()
