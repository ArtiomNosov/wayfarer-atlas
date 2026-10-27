#!/usr/bin/env bash
set -eu

TASK_APP_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
TASK_REPO_ROOT=$(CDPATH= cd -- "$TASK_APP_ROOT/../.." && pwd)
TASK_JAVA="$TASK_REPO_ROOT/research/passport-index/.tools/jre/jdk-21.0.13+9-jre/Contents/Home/bin/java"
TASK_ECJ="$TASK_REPO_ROOT/research/passport-index/.tools/ecj-3.38.0.jar"
TASK_OUTPUT="$TASK_APP_ROOT/app/build/offline-storage"
TASK_MAIN="$TASK_APP_ROOT/app/src/main/java/org/wayfarer/atlas"
TASK_TEST="$TASK_APP_ROOT/app/src/test/java/org/wayfarer/atlas/storage/StorageBehaviorChecks.java"

if [ ! -x "$TASK_JAVA" ] || [ ! -f "$TASK_ECJ" ]; then
    echo "Offline Java runtime or ECJ compiler is unavailable." >&2
    exit 1
fi

mkdir -p "$TASK_OUTPUT"
find "$TASK_MAIN/domain" "$TASK_MAIN/storage" -name '*.java' -type f | sort > "$TASK_OUTPUT/sources.txt"
printf '%s\n' "$TASK_TEST" >> "$TASK_OUTPUT/sources.txt"
"$TASK_JAVA" -jar "$TASK_ECJ" -17 -encoding UTF-8 -d "$TASK_OUTPUT" @"$TASK_OUTPUT/sources.txt"
"$TASK_JAVA" -cp "$TASK_OUTPUT" org.wayfarer.atlas.storage.StorageBehaviorChecks
