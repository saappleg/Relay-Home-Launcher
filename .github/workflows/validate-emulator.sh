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

# Pixel 2's default profile is portrait and exposes phone system bars. Keep
# rotation locked at zero because the launch skin is already landscape, force
# the logical display size/density, and apply the same immersive policy used by
# a fullscreen TV surface. The instrumentation Compose activity comes from the
# test harness (not the app theme), so the policy must be applied globally
# before connectedCheck creates that activity.
adb -s "$serial" shell settings put system accelerometer_rotation 0
adb -s "$serial" shell settings put system user_rotation 0
adb -s "$serial" shell cmd window user-rotation lock 0 >/dev/null 2>&1 || true
adb -s "$serial" shell settings put secure immersive_mode_confirmations confirmed
adb -s "$serial" shell settings put global policy_control 'immersive.full=*'
adb -s "$serial" shell wm size 1920x1080
adb -s "$serial" shell wm density 320
geometry_started="$(date +%s)"
geometry_deadline=$((geometry_started + 45))
now="$(date +%s)"
physical_size=""
logical_size=""
display_size=""
wm_size=""
wm_density=""
physical_density=""
override_density=""
effective_density=""
window_display_size=""
window_content_size=""
status_bars_visible=""
navigation_bars_visible=""
policy_control=""
window_dump=""
while [ "$now" -lt "$geometry_deadline" ]; do
  wm_size="$(adb -s "$serial" shell wm size 2>/dev/null | tr -d '\r' || true)"
  physical_size="$(printf '%s\n' "$wm_size" | sed -n 's/^[[:space:]]*Physical size:[[:space:]]*//p' | tail -1)"
  logical_size="$(printf '%s\n' "$wm_size" | sed -n 's/^[[:space:]]*Override size:[[:space:]]*//p' | tail -1)"
  if [ -z "$logical_size" ]; then
    logical_size="$physical_size"
  fi
  wm_density="$(adb -s "$serial" shell wm density 2>/dev/null | tr -d '\r' || true)"
  physical_density="$(printf '%s\n' "$wm_density" | sed -n 's/^[[:space:]]*Physical density:[[:space:]]*//p' | tail -1)"
  override_density="$(printf '%s\n' "$wm_density" | sed -n 's/^[[:space:]]*Override density:[[:space:]]*//p' | tail -1)"
  effective_density="$override_density"
  if [ -z "$effective_density" ]; then
    effective_density="$physical_density"
  fi
  display_dump="$(adb -s "$serial" shell dumpsys display 2>/dev/null | tr -d '\r' || true)"
  display_size="$(printf '%s\n' "$display_dump" | sed -n \
    -e 's/.*real \([0-9][0-9]*\) x \([0-9][0-9]*\).*/\1x\2/p' \
    -e 's/.*DisplayDeviceInfo{[^,]*, \([0-9][0-9]*\) x \([0-9][0-9]*\),.*/\1x\2/p' | head -1)"
  printf 'Emulator display geometry: physical=%s logical=%s display=%s density=%s (physical=%s override=%s)\n' \
    "${physical_size:-unavailable}" "${logical_size:-unavailable}" "${display_size:-unavailable}" \
    "${effective_density:-unavailable}" "${physical_density:-unavailable}" "${override_density:-none}"
  if [ "$physical_size" = "1920x1080" ] && [ "$logical_size" = "1920x1080" ] \
    && [ "$display_size" = "1920x1080" ] && [ "$effective_density" = "320" ]; then
    break
  fi
  adb -s "$serial" shell wm size 1920x1080 || true
  adb -s "$serial" shell wm density 320 || true
  sleep 2
  now="$(date +%s)"
done

printf 'Final emulator wm size:\n%s\n' "$wm_size"
printf 'Final emulator wm density:\n%s\n' "$wm_density"
if [ "$physical_size" != "1920x1080" ] || [ "$logical_size" != "1920x1080" ] \
  || [ "$display_size" != "1920x1080" ] || [ "$effective_density" != "320" ]; then
  echo "Expected a 1920x1080 physical/logical/display surface at effective density 320." >&2
  adb devices -l || true
  adb -s "$serial" shell wm size || true
  adb -s "$serial" shell wm density || true
  adb -s "$serial" shell dumpsys display | tail -120 || true
  adb -s "$serial" shell dumpsys window displays | tail -80 || true
  exit 1
fi

# Confirm that the system bars are actually hidden and that the window policy
# gives the test activity the full display. A matching wm size alone is not
# sufficient: a phone AVD can report 1920x1080 while reserving top/bottom
# insets for status/navigation bars, which changes Compose focus and layout.
fullscreen_started="$(date +%s)"
fullscreen_deadline=$((fullscreen_started + 30))
now="$(date +%s)"
window_display_size=""
window_content_size=""
status_bars_visible=""
navigation_bars_visible=""
policy_control=""
window_dump=""
while [ "$now" -lt "$fullscreen_deadline" ]; do
  policy_control="$(adb -s "$serial" shell settings get global policy_control 2>/dev/null | tr -d '\r' || true)"
  window_dump="$(adb -s "$serial" shell dumpsys window displays 2>/dev/null | tr -d '\r' || true)"
  window_display_size="$(printf '%s\n' "$window_dump" | sed -n \
    's/.*mDisplayFrame=Rect(0, 0 - \([0-9][0-9]*\), \([0-9][0-9]*\)).*/\1x\2/p' | head -1)"
  window_content_size="$(printf '%s\n' "$window_dump" | sed -n \
    's/.*mContent=Rect(0, 0 - \([0-9][0-9]*\), \([0-9][0-9]*\)).*/\1x\2/p' | head -1)"
  status_line="$(printf '%s\n' "$window_dump" | grep -m 1 'type=statusBars' || true)"
  navigation_line="$(printf '%s\n' "$window_dump" | grep -m 1 'type=navigationBars' || true)"
  status_bars_visible="$(printf '%s\n' "$status_line" | sed -n \
    's/.*visible=\([^[:space:]]*\).*/\1/p')"
  navigation_bars_visible="$(printf '%s\n' "$navigation_line" | sed -n \
    's/.*visible=\([^[:space:]]*\).*/\1/p')"
  printf 'Emulator fullscreen state: policy_control=%s displayFrame=%s contentFrame=%s statusBarsVisible=%s navigationBarsVisible=%s\n' \
    "${policy_control:-unavailable}" "${window_display_size:-unavailable}" \
    "${window_content_size:-unavailable}" "${status_bars_visible:-unavailable}" \
    "${navigation_bars_visible:-unavailable}"
  if [ "$policy_control" = "immersive.full=*" ] \
    && [ "$window_display_size" = "1920x1080" ] \
    && [ "$window_content_size" = "1920x1080" ] \
    && [ "$status_bars_visible" = "false" ] \
    && [ "$navigation_bars_visible" = "false" ]; then
    break
  fi
  adb -s "$serial" shell settings put secure immersive_mode_confirmations confirmed
  adb -s "$serial" shell settings put global policy_control 'immersive.full=*'
  sleep 2
  now="$(date +%s)"
done

if [ "$policy_control" != "immersive.full=*" ] \
  || [ "$window_display_size" != "1920x1080" ] \
  || [ "$window_content_size" != "1920x1080" ] \
  || [ "$status_bars_visible" != "false" ] \
  || [ "$navigation_bars_visible" != "false" ]; then
  echo "Hosted emulator did not expose a fullscreen 1920x1080 test window." >&2
  echo "Expected immersive.full=*, full display/content frames, and hidden status/navigation bars." >&2
  printf 'Observed policy_control=%s displayFrame=%s contentFrame=%s statusBarsVisible=%s navigationBarsVisible=%s\n' \
    "${policy_control:-unavailable}" "${window_display_size:-unavailable}" \
    "${window_content_size:-unavailable}" "${status_bars_visible:-unavailable}" \
    "${navigation_bars_visible:-unavailable}" >&2
  adb devices -l || true
  adb -s "$serial" shell settings get global policy_control || true
  adb -s "$serial" shell settings get secure immersive_mode_confirmations || true
  adb -s "$serial" shell dumpsys window displays | grep -E \
    'mDisplayFrame|mContent|mStable|nonDecorInsets|configInsets|type=(statusBars|navigationBars)' | tail -120 || true
  adb -s "$serial" shell dumpsys window policy | tail -160 || true
  adb -s "$serial" shell dumpsys statusbar | tail -120 || true
  exit 1
fi

printf 'Verified fullscreen emulator window: displayFrame=%s contentFrame=%s statusBarsVisible=%s navigationBarsVisible=%s\n' \
  "$window_display_size" "$window_content_size" "$status_bars_visible" "$navigation_bars_visible"

./gradlew :app:connectedCheck --stacktrace --console=plain
