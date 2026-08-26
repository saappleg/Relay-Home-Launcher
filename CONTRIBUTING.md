# Contributing to Relay Home

Relay Home is an Android TV launcher designed for D-pad-first media discovery.

## Before opening a pull request

1. Build with JDK 17: `./gradlew :app:assembleDebug`.
2. Test the changed flow with a TV emulator or Android TV device.
3. Verify Up, Down, Left, Right, Back, and Select focus behavior.
4. Do not commit `local.properties`, API keys, provider tokens, or user library data.

## Issues

Use the bug template for reproducible behavior and include Android TV device/API level, launcher version, and D-pad steps. Use feature requests for product ideas.
