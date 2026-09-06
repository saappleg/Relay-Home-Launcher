#!/bin/sh

set -eu

# android-emulator-runner invokes this helper as one shell command. Keep the
# action input itself one line because the action splits multiline inputs before
# executing them.
avd_home="${ANDROID_AVD_HOME:-${HOME}/.android/avd}"
avd_config="$avd_home/test.avd/config.ini"

test -f "$avd_config"

set_config_entry() {
  key="$1"
  value="$2"
  if grep -q "^${key}=" "$avd_config"; then
    sed -i "s|^${key}=.*|${key}=${value}|" "$avd_config"
  else
    printf '%s=%s\n' "$key" "$value" >> "$avd_config"
  fi
}

# Keep the AVD itself TV-shaped as a fallback, and pair it with the explicit
# -skin 1920x1080 launch option in publish-release.yml.
set_config_entry hw.lcd.width 1920
set_config_entry hw.lcd.height 1080
set_config_entry hw.initialOrientation landscape
set_config_entry skin.dynamic yes
set_config_entry skin.name 1920x1080

grep -E '^(hw\.lcd\.(width|height)|hw\.initialOrientation|skin\.(dynamic|name))=' "$avd_config"
