#!/usr/bin/env bash
set -euo pipefail
APP_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
KEY_DIRECTORY="$APP_ROOT/release-private"
if [[ -f "$KEY_DIRECTORY/upload.jks" ]]; then
  echo "An upload key already exists. It has not been changed." >&2
  exit 1
fi
mkdir -p "$KEY_DIRECTORY"
chmod 700 "$KEY_DIRECTORY"
umask 077
KEYTOOL="${JAVA_HOME:+$JAVA_HOME/bin/}keytool"
echo "Enter the upload-key password and publisher details interactively. Do not paste passwords into chat."
"$KEYTOOL" -genkeypair -v -keystore "$KEY_DIRECTORY/upload.jks" -storetype JKS \
  -alias upload -keyalg RSA -keysize 4096 -validity 10000
chmod 600 "$KEY_DIRECTORY/upload.jks"
echo "Key created in release-private/upload.jks. Keep a separate secure backup."
