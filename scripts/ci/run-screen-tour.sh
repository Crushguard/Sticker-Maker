#!/usr/bin/env bash
# Runs the on-device screen tour on the booted emulator and pulls its output.
# Called by .github/workflows/screens.yml inside android-emulator-runner.
set -euo pipefail

APP=com.piptechnologies.stickermaker
OUT=${TOUR_OUT:-build/screen-tour}
mkdir -p "$OUT"

adb wait-for-device
# The 2-core runner is saturated right after boot (Play services, System UI);
# "isn't responding" dialogs would steal focus from the app mid-tour. Hide
# them and let the first-boot work settle before the tour starts.
adb shell settings put global hide_error_dialogs 1
sleep 60
adb install -r -t app/build/outputs/apk/debug/app-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
# The tour installs the WhatsApp test double itself, after the "not installed" frame.
adb push testing/whatsapp-stub/build/outputs/apk/debug/whatsapp-stub-debug.apk /data/local/tmp/whatsapp-stub.apk
adb uninstall com.whatsapp >/dev/null 2>&1 || true
adb logcat -c || true

set +e
adb shell am instrument -w \
  -e class "$APP.tour.ScreenTourTest,$APP.tour.LocaleTourTest" \
  "$APP.test/androidx.test.runner.AndroidJUnitRunner" 2>&1 | tee "$OUT/instrument.txt"
set -e

adb logcat -d > "$OUT/logcat.txt" 2>/dev/null || true
# Warnings and errors only: small enough to publish next to the screenshots.
adb logcat -d -v time '*:W' > "$OUT/logcat-warnings.txt" 2>/dev/null || true
rm -rf "$OUT/tour"
if adb shell run-as "$APP" ls files/tour >/dev/null 2>&1; then
  adb exec-out run-as "$APP" tar -cf - -C files tour | tar -xf - -C "$OUT"
  echo "Pulled $(ls "$OUT/tour" | wc -l) files from the device."
else
  echo "The tour wrote no output on the device."
fi

# am instrument exits 0 even when tests fail; read its verdict instead.
if ! grep -q "^OK (" "$OUT/instrument.txt"; then
  echo "::error::Screen tour failed; see $OUT/instrument.txt"
  exit 1
fi
