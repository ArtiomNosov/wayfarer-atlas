#!/usr/bin/env bash
set -euo pipefail
APP_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPO_ROOT="$(cd "$APP_ROOT/../.." && pwd)"
TASK_JAVA="${WAYFARER_TEST_JAVA:-$REPO_ROOT/research/passport-index/.tools/jre/jdk-21.0.13+9-jre/Contents/Home/bin/java}"
TASK_ECJ="${WAYFARER_TEST_ECJ:-$REPO_ROOT/research/passport-index/.tools/ecj-3.38.0.jar}"
OUTPUT="$APP_ROOT/app/build/offline-domain"
mkdir -p "$OUTPUT"
"$TASK_JAVA" -jar "$TASK_ECJ" -17 -warn:none -d "$OUTPUT" \
  "$APP_ROOT"/app/src/main/java/org/wayfarer/atlas/domain/*.java \
  "$APP_ROOT/app/src/test/java/org/wayfarer/atlas/domain/DomainBehaviorChecks.java" \
  "$APP_ROOT/app/src/test/java/org/wayfarer/atlas/domain/ReminderBehaviorChecks.java"
"$TASK_JAVA" -cp "$OUTPUT" org.wayfarer.atlas.domain.DomainBehaviorChecks
"$TASK_JAVA" -cp "$OUTPUT" org.wayfarer.atlas.domain.ReminderBehaviorChecks
