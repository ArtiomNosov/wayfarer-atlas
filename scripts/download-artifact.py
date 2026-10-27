#!/usr/bin/env python3
"""Resumable, bounded public artifact download with optional parallel byte ranges."""
import argparse
from concurrent.futures import ThreadPoolExecutor
import hashlib
from pathlib import Path
import shutil
import subprocess
import time

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("url")
parser.add_argument("destination", type=Path)
parser.add_argument("--size", type=int, required=True)
parser.add_argument("--sha256")
parser.add_argument("--sha1", help="Official Android SDK repository checksum")
parser.add_argument("--metadata", action="store_true", help="HTTPS metadata only; validate JSON/XML instead of a supplied checksum")
parser.add_argument("--workers", type=int, default=6)
parser.add_argument("--chunk-size", type=int, default=8 * 1024 * 1024)
args = parser.parse_args()
if not args.url.startswith("https://") or args.size < 1 or args.chunk_size < 1024:
    parser.error("HTTPS URL and positive size required")
checksum = args.sha256 or args.sha1
algorithm = "sha256" if args.sha256 else "sha1"
if not checksum and not (args.metadata and args.destination.suffix in (".xml", ".json") and args.size < 5_000_000):
    parser.error("Artifacts require --sha256 or official --sha1; only small XML/JSON metadata may omit it")
args.destination.parent.mkdir(parents=True, exist_ok=True)
if checksum and args.destination.exists() and hashlib.new(algorithm, args.destination.read_bytes()).hexdigest() == checksum:
    print("Artifact already verified")
    raise SystemExit(0)
pieces = args.destination.with_name(args.destination.name + ".chunks")
pieces.mkdir(exist_ok=True)
chunk_size = args.chunk_size
ranges = [(i, start, min(start + chunk_size, args.size) - 1) for i, start in enumerate(range(0, args.size, chunk_size))]

def fetch(item):
    index, start, end = item
    output = pieces / f"{index:04d}"
    expected = end - start + 1
    if output.exists() and output.stat().st_size == expected:
        return
    for attempt in range(4):
        partial = output.stat().st_size if output.exists() else 0
        if partial >= expected:
            output.unlink()
            partial = 0
        temporary = output.with_suffix(".next")
        result = subprocess.run(["curl", "--fail", "--location", "--proto", "=https", "--proto-redir", "=https",
            "--connect-timeout", "15", "--max-time", "240", "--silent", "--show-error",
            "--range", f"{start + partial}-{end}", "--max-filesize", str(expected - partial),
            "--output", str(temporary), args.url], capture_output=True)
        if temporary.exists():
            with output.open("ab") as combined, temporary.open("rb") as source:
                while block := source.read(1024 * 1024):
                    combined.write(block)
            temporary.unlink()
        if result.returncode == 0 and output.exists() and output.stat().st_size == expected:
            print(f"Downloaded chunk {index + 1}/{len(ranges)}", flush=True)
            return
        if result.returncode == 63:
            raise RuntimeError("Server did not honor the bounded range request")
        time.sleep(attempt + 1)
    raise RuntimeError(f"Chunk {index} download failed; partial chunks retained")

with ThreadPoolExecutor(max_workers=min(args.workers, 8)) as pool:
    list(pool.map(fetch, ranges))
temporary = args.destination.with_suffix(args.destination.suffix + ".assembling")
digest = hashlib.new(algorithm)
with temporary.open("wb") as combined:
    for index, _, _ in ranges:
        with (pieces / f"{index:04d}").open("rb") as source:
            while block := source.read(1024 * 1024):
                digest.update(block)
                combined.write(block)
if checksum and digest.hexdigest() != checksum:
    temporary.unlink()
    shutil.rmtree(pieces)
    raise SystemExit("Artifact checksum mismatch; not installed")
if args.metadata:
    if args.destination.suffix == ".xml":
        import xml.etree.ElementTree as ET
        ET.parse(temporary)
    else:
        import json
        json.loads(temporary.read_text())
temporary.replace(args.destination)
shutil.rmtree(pieces)
print("Artifact " + algorithm.upper() + " verified" if checksum else "HTTPS metadata structure validated", flush=True)
