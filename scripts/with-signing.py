#!/usr/bin/env python3
"""Create/use an owner-only local upload key without printing signing credentials."""
import json
import os
from pathlib import Path
import secrets
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
PRIVATE = ROOT / "release-private"
CONFIG = PRIVATE / "signing.json"
KEYSTORE = PRIVATE / "upload.jks"


def main():
    PRIVATE.mkdir(mode=0o700, exist_ok=True)
    PRIVATE.chmod(0o700)
    if sys.argv[1:] == ["--create"]:
        if KEYSTORE.exists():
            if not CONFIG.exists():
                raise SystemExit("An upload key already exists without managed settings. It was not changed.")
            print("Local upload key already exists; unchanged.")
            return
        if not CONFIG.exists():
            credentials = {"storePassword": secrets.token_urlsafe(48), "keyPassword": secrets.token_urlsafe(48), "alias": "upload"}
            descriptor = os.open(CONFIG, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
            with os.fdopen(descriptor, "w") as output:
                json.dump(credentials, output)
        else:
            credentials = json.loads(CONFIG.read_text())
        environment = dict(os.environ, WAYFARER_STORE_PASSWORD=credentials["storePassword"], WAYFARER_KEY_PASSWORD=credentials["keyPassword"])
        tools = list((ROOT / ".tooling/jdk").glob("**/bin/keytool"))
        keytool = str(tools[0]) if tools else "keytool"
        process = subprocess.run([keytool, "-genkeypair", "-keystore", str(KEYSTORE), "-storetype", "JKS",
            "-storepass:env", "WAYFARER_STORE_PASSWORD", "-keypass:env", "WAYFARER_KEY_PASSWORD",
            "-alias", "upload", "-keyalg", "RSA", "-keysize", "4096", "-validity", "10000",
            "-dname", "CN=Wayfarer Atlas Upload", "-noprompt"], env=environment, capture_output=True)
        if process.returncode:
            raise SystemExit("Upload key generation failed; no signing credentials were printed.")
        KEYSTORE.chmod(0o600)
        print("Upload key created in ignored owner-only release-private/. Keep a secure backup of that directory.")
        return
    if len(sys.argv) < 2 or not CONFIG.is_file() or not KEYSTORE.is_file():
        raise SystemExit("Run with --create first, or provide a child build command after this script.")
    if CONFIG.stat().st_mode & 0o077:
        raise SystemExit("Signing settings must have owner-only permissions.")
    credentials = json.loads(CONFIG.read_text())
    environment = dict(os.environ, WAYFARER_KEYSTORE=str(KEYSTORE), WAYFARER_STORE_PASSWORD=credentials["storePassword"],
                       WAYFARER_KEY_PASSWORD=credentials["keyPassword"], WAYFARER_KEY_ALIAS=credentials["alias"])
    raise SystemExit(subprocess.call(sys.argv[1:], cwd=ROOT, env=environment))


if __name__ == "__main__":
    main()
