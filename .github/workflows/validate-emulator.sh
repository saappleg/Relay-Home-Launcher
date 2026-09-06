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

# Pixel 2's default profile is portrait. Keep rotation locked at zero because
# the launch skin is already landscape, then force the logical display size and
# poll the actual physical/logical geometry instead of relying on a
# SurfaceOrientation line that API 34 may not expose.
adb -s "$serial" shell settings put system accelerometer_rotation 0
adb -s "$serial" shell settings put system user_rotation 0
adb -s "$serial" shell cmd window user-rotation lock 0 >/dev/null 2>&1 || true
adb -s "$serial" shell wm size 1920x1080
geometry_started="$(date +%s)"
geometry_deadline=$((geometry_started + 45))
now="$(date +%s)"
physical_size=""
logical_size=""
display_size=""
wm_size=""
while [ "$now" -lt "$geometry_deadline" ]; do
  wm_size="$(adb -s "$serial" shell wm size 2>/dev/null | tr -d '\r' || true)"
  physical_size="$(printf '%s\n' "$wm_size" | sed -n 's/^[[:space:]]*Physical size:[[:space:]]*//p' | tail -1)"
  logical_size="$(printf '%s\n' "$wm_size" | sed -n 's/^[[:space:]]*Override size:[[:space:]]*//p' | tail -1)"
  if [ -z "$logical_size" ]; then
    logical_size="$physical_size"
  fi
  display_dump="$(adb -s "$serial" shell dumpsys display 2>/dev/null | tr -d '\r' || true)"
  display_size="$(printf '%s\n' "$display_dump" | sed -n \
    -e 's/.*real \([0-9][0-9]*\) x \([0-9][0-9]*\).*/\1x\2/p' \
    -e 's/.*DisplayDeviceInfo{[^,]*, \([0-9][0-9]*\) x \([0-9][0-9]*\),.*/\1x\2/p' | head -1)"
  printf 'Emulator display geometry: physical=%s logical=%s display=%s\n' \
    "${physical_size:-unavailable}" "${logical_size:-unavailable}" "${display_size:-unavailable}"
  if [ "$physical_size" = "1920x1080" ] && [ "$logical_size" = "1920x1080" ]; then
    break
  fi
  adb -s "$serial" shell wm size 1920x1080 || true
  sleep 2
  now="$(date +%s)"
done

printf 'Final emulator wm size:\n%s\n' "$wm_size"
if [ "$physical_size" != "1920x1080" ] || [ "$logical_size" != "1920x1080" ] || [ "$display_size" != "1920x1080" ]; then
  echo "Expected a 1920x1080 physical, logical, and display-service surface." >&2
  adb devices -l || true
  adb -s "$serial" shell wm size || true
  adb -s "$serial" shell wm density || true
  adb -s "$serial" shell dumpsys display | tail -120 || true
  adb -s "$serial" shell dumpsys window displays | tail -80 || true
  exit 1
fi

./gradlew :app:connectedCheck --stacktrace --console=plain
