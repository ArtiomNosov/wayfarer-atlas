#!/usr/bin/env bash
set -euo pipefail
APP_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPO_ROOT="$(cd "$APP_ROOT/../.." && pwd)"
TASK_JAVA="${WAYFARER_TEST_JAVA:-$REPO_ROOT/research/passport-index/.tools/jre/jdk-21.0.13+9-jre/Contents/Home/bin/java}"
TASK_ECJ="${WAYFARER_TEST_ECJ:-$REPO_ROOT/research/passport-index/.tools/ecj-3.38.0.jar}"
TASK_GSON="${WAYFARER_TEST_GSON:-$REPO_ROOT/research/passport-index/.tools/jadx/lib/jadx-1.5.6-all.jar}"
OUTPUT="$APP_ROOT/app/build/offline-data"
mkdir -p "$OUTPUT"
"$TASK_JAVA" -jar "$TASK_ECJ" -17 -warn:none -cp "$TASK_GSON" -d "$OUTPUT" \
  "$APP_ROOT"/app/src/main/java/org/wayfarer/atlas/domain/*.java \
  "$APP_ROOT"/app/src/main/java/org/wayfarer/atlas/data/*.java \
  "$APP_ROOT/app/src/test/java/org/wayfarer/atlas/data/DatasetBehaviorChecks.java" \
  "$APP_ROOT/app/src/test/java/org/wayfarer/atlas/data/UpdatePolicyBehaviorChecks.java" \
  "$APP_ROOT/app/src/test/java/org/wayfarer/atlas/data/SnapshotSelectionChecks.java" \
  "$APP_ROOT/app/src/test/java/org/wayfarer/atlas/data/CommunityMatrixBehaviorChecks.java"
"$TASK_JAVA" -cp "$OUTPUT:$TASK_GSON" org.wayfarer.atlas.data.DatasetBehaviorChecks "$APP_ROOT/app/src/main/assets"
"$TASK_JAVA" -cp "$OUTPUT:$TASK_GSON" org.wayfarer.atlas.data.UpdatePolicyBehaviorChecks
"$TASK_JAVA" -cp "$OUTPUT:$TASK_GSON" org.wayfarer.atlas.data.SnapshotSelectionChecks
"$TASK_JAVA" -cp "$OUTPUT:$TASK_GSON" org.wayfarer.atlas.data.CommunityMatrixBehaviorChecks \
  "$APP_ROOT/data/sources/visa-requirements.json" "$APP_ROOT/data/countries.json" "$APP_ROOT/data/rules.json"
