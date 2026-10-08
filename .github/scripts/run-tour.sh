#!/usr/bin/env bash
# Runs inside the emulator step: the screenshot tour, then copies the screenshots
# off the device even if the tour failed halfway (so we can see where it stopped).
set -u
cd android
adb shell rm -rf /sdcard/lime-shots
./gradlew --no-daemon connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.receiver="$RECEIVER"
status=$?
mkdir -p ../shots
adb pull /sdcard/lime-shots/. ../shots/ || true
ls -la ../shots
exit $status
