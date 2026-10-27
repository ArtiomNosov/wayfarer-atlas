#!/usr/bin/env bash
set -euo pipefail
APP_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
if [[ -d "$APP_ROOT/.tooling/jdk" ]]; then
  JAVAC_PATH="$(find "$APP_ROOT/.tooling/jdk" -path '*/bin/javac' -type f -print -quit)"
  if [[ -n "$JAVAC_PATH" ]]; then
    export JAVA_HOME="${JAVAC_PATH%/bin/javac}"
    export PATH="$JAVA_HOME/bin:$PATH"
  fi
fi
export ANDROID_HOME="${ANDROID_HOME:-$APP_ROOT/.tooling/android-sdk}"
export GRADLE_USER_HOME="$APP_ROOT/.tooling/gradle-cache"
export ANDROID_USER_HOME="$APP_ROOT/.tooling/android-user"
cd "$APP_ROOT"
if [[ -x .tooling/gradle-8.13/bin/gradle ]]; then
  exec .tooling/gradle-8.13/bin/gradle "$@"
fi
if [[ -f ./gradlew ]]; then
  exec bash ./gradlew "$@"
fi
echo "No Gradle installation. Run python3 scripts/bootstrap.py or open this project in Android Studio." >&2
exit 2
