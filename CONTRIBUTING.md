# Contributing to Relay Home

Relay Home is an Android TV launcher designed for D-pad-first media discovery.

## Before opening a pull request

1. Build with JDK 17: `./gradlew :app:assembleDebug`.
2. Run the JVM test suite: `./gradlew test`.
3. For a focused app-only run, use `./gradlew :app:testDebugUnitTest`.
4. Run connected instrumentation on an Android TV emulator or device with
   `./gradlew :app:connectedCheck`.
5. Test the changed flow with a TV emulator or Android TV device.
6. Verify Up, Down, Left, Right, Back, and Select focus behavior.
7. Do not commit `local.properties`, signing properties, keystores, API keys,
   provider tokens, or user library data.

Debug builds install StrictMode thread and VM policies that log disk/network-on-main-thread,
leaked-resource, and activity-leak violations without crashing the process. New StrictMode
violations are bugs: fix the underlying work or lifecycle issue instead of adding a suppression.

LeakCanary is not currently included. The project has no existing version-catalog entry or
resolvable local artifact for it, so this change keeps the dependency-free StrictMode safety net
and avoids making the build depend on an unverified external artifact.

Release packaging is intentionally fail-closed and requires the permanent
PKCS12 signing values from `signing.properties.example`. It is not required for
normal tests or debug builds. After configuring signing, validate a release with
`./gradlew :app:lintRelease :app:assembleRelease`.

## Issues

Use the bug template for reproducible behavior and include Android TV device/API level, launcher version, and D-pad steps. Use feature requests for product ideas.
