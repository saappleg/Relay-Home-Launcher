#!/bin/sh

set -eu

# android-emulator-runner invokes its script input one line at a time. This
# helper is intentionally called as a single line from the workflow so the
# control flow below is parsed by a real shell as one complete script.
serial="${ANDROID_SERIAL:-emulator-5554}"

boot_started="$(date +%s)"
boot_deadline=$((boot_started + 120))
now="$(date +%s)"
while [ "$now" -lt "$boot_deadline" ]; do
  state="$(adb -s "$serial" get-state 2>/dev/null | tr -d '\r' || true)"
  boot_completed="$(adb -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
  boot_complete_prop="$(adb -s "$serial" shell getprop dev.bootcomplete 2>/dev/null | tr -d '\r' || true)"
  printf 'Emulator readiness: state=%s sys.boot_completed=%s dev.bootcomplete=%s\n' \
    "${state:-unavailable}" "${boot_completed:-unset}" "${boot_complete_prop:-unset}"
  if [ "$state" = "device" ]; then
    if [ "$boot_completed" = "1" ] || [ "$boot_complete_prop" = "1" ]; then
      break
    fi
  fi
  sleep 2
  now="$(date +%s)"
done

state="$(adb -s "$serial" get-state 2>/dev/null | tr -d '\r' || true)"
boot_completed="$(adb -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
boot_complete_prop="$(adb -s "$serial" shell getprop dev.bootcomplete 2>/dev/null | tr -d '\r' || true)"
if [ "$state" != "device" ] || {
  [ "$boot_completed" != "1" ] && [ "$boot_complete_prop" != "1" ];
}; then
  echo "Emulator did not reach a usable ADB state within 120 seconds." >&2
  adb devices -l || true
  adb -s "$serial" get-state || true
  adb -s "$serial" shell getprop sys.boot_completed || true
  adb -s "$serial" shell getprop dev.bootcomplete || true
  exit 1
fi

# Pixel 2's default profile is portrait. Lock rotation after boot and poll the
# actual input surface so focus/display tests run against a TV-shaped landscape
# surface without assuming that one settings write took effect immediately.
adb -s "$serial" shell settings put system accelerometer_rotation 0
adb -s "$serial" shell settings put system user_rotation 1
adb -s "$serial" shell cmd window user-rotation lock 1 >/dev/null 2>&1 || true
orientation_started="$(date +%s)"
orientation_deadline=$((orientation_started + 45))
now="$(date +%s)"
orientation=""
while [ "$now" -lt "$orientation_deadline" ]; do
  orientation="$(adb -s "$serial" shell dumpsys input 2>/dev/null | sed -n 's/.*SurfaceOrientation: \([0-3]\).*/\1/p' | tr -d '\r' | tail -1 || true)"
  printf 'Emulator surface orientation: %s\n' "${orientation:-unavailable}"
  if [ "$orientation" = "1" ] || [ "$orientation" = "3" ]; then
    break
  fi
  adb -s "$serial" shell settings put system user_rotation 1 || true
  sleep 2
  now="$(date +%s)"
done

adb -s "$serial" shell wm size | tr -d '\r'
if [ "$orientation" != "1" ] && [ "$orientation" != "3" ]; then
  echo "Expected a landscape emulator surface, got orientation ${orientation:-unavailable}." >&2
  adb devices -l || true
  adb -s "$serial" shell dumpsys input | tail -80 || true
  adb -s "$serial" shell dumpsys window displays | tail -80 || true
  exit 1
fi

./gradlew :app:connectedCheck --stacktrace --console=plain
