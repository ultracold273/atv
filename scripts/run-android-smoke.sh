#!/usr/bin/env bash

set +e

SMOKE_CLASSES="$(bash scripts/android-smoke-classes.sh)"
./gradlew app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class="$SMOKE_CLASSES" \
  --no-daemon
status=$?

mkdir -p app/build/reports/android-smoke-diagnostics
adb logcat -d > app/build/reports/android-smoke-diagnostics/logcat.txt || true

if [ "$status" -ne 0 ]; then
  adb exec-out screencap -p > app/build/reports/android-smoke-diagnostics/failure-screenshot.png || true
fi

exit "$status"
