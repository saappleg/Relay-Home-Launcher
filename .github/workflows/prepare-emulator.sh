#!/bin/sh

set -eu

# android-emulator-runner invokes this helper as one shell command. Keep the
# action input itself one line because the action splits multiline inputs before
# executing them.
avd_home="${ANDROID_AVD_HOME:-${HOME}/.android/avd}"
avd_config="$avd_home/test.avd/config.ini"

test -f "$avd_config"
if grep -q '^hw.initialOrientation=' "$avd_config"; then
  sed -i 's/^hw.initialOrientation=.*/hw.initialOrientation=landscape/' "$avd_config"
else
  printf '\nhw.initialOrientation=landscape\n' >> "$avd_config"
fi
grep -F 'hw.initialOrientation=landscape' "$avd_config"
