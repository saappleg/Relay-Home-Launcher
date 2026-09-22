# Contributing to Relay Home

Relay Home is an Android TV launcher built around Compose and D-pad-first
navigation. Keep provider ownership, UI focus behavior, and the fail-closed
release boundary explicit when changing the project.

## Local setup

- Use JDK 17 and Android SDK Platform 35.
- Copy `local.example.properties` to the ignored `local.properties` file when a
  local TMDB build key is useful. User-configurable metadata keys belong in
  Settings > Data Sources and must not be committed.
- Never commit `local.properties`, signing properties, keystores, API keys,
  provider tokens, crash records, or user library data.

## Validation

Run the JVM tests, debug unit tests, lint, and debug packaging before opening a
pull request:

```bash
./gradlew test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

For Android instrumentation, use an Android TV emulator. Before invoking a
connected task, check `adb devices -l` and disconnect every physical target.
The Gradle build intentionally refuses `connectedCheck` and connected Android
test tasks when a physical ADB target is present: the Android test harness
owns package cleanup and can change launcher state or remove a Relay package
on a real TV.

```bash
adb devices -l
./gradlew :app:connectedCheck
```

For a known emulator serial, the test APKs can also be installed and run
without the Gradle connected-task wrapper:

```bash
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5554 shell am instrument -w -r \
  com.relayhome.launcher.debug.test/androidx.test.runner.AndroidJUnitRunner
```

The optional baseline-profile module uses a connected emulator and the signed
production package. It is not part of ordinary builds and must never be run
with a physical TV attached:

```bash
./gradlew :baselineprofile:generateRelayBaselineProfile
```

The current profile covers startup and a short Home D-pad traversal. There is
not yet a standalone Macrobenchmark before/after report; treat collection as
an explicit profiling exercise and review generated profile changes before
committing them.

The debug application ID is `com.relayhome.launcher.debug`, keeping test
installations separate from the signed production launcher. A physical TV is
still useful for a non-destructive smoke test after the emulator suite has
passed; install a deliberate APK with `adb install -r` and do not clear data or
run the connected-test cleanup against it.

## UI and provider changes

Verify Left, Right, Up, Down, Back, and Select in every changed surface. Check
first focus, return focus after Back, scrolling, loading/empty/error states,
and rapid D-pad input. New focus requesters must point to mounted interactive
content, and custom controls need visible labels/content descriptions.

Provider changes must preserve the ownership boundaries described in
[docs/PROVIDER_INTEGRATION.md](docs/PROVIDER_INTEGRATION.md). In particular,
RelayTube subscriptions and Continue Watching are scoped by the echoed
profile ID; delayed responses must not repopulate a previous profile. Nuvio
remains the authority for its own library and resume state.

When adding a recoverable provider or Android-framework operation, keep the
last known-good UI state where possible and record a bounded diagnostic rather
than throwing through the launcher. Process-level crash evidence is available
under Settings > Device Settings > Show advanced diagnostics and is local-only.

Debug builds install StrictMode thread and VM policies that log disk/network-on-
main-thread, leaked-resource, and activity-leak violations without crashing
the process. New violations are bugs: fix the underlying work or lifecycle
issue instead of adding a suppression.

LeakCanary is not currently included. There is no existing version-catalog
entry or verified local artifact for it, so the project currently relies on
the dependency-free StrictMode safety net.

## Release contributions

Release packaging is fail-closed. It requires an explicit
`RELAY_VERSION_NAME`, an increasing `RELAY_VERSION_CODE`, and a permanent
PKCS12 signing key configured through the five `RELAY_SIGNING_*` environment
variables or the five `relay.signing.*` entries in
[`signing.properties.example`](signing.properties.example). Validate locally
with:

```bash
export RELAY_VERSION_NAME=0.1.0-beta.8
export RELAY_VERSION_CODE=31
./gradlew :app:verifyRelayReleaseVersion :app:lintRelease \
  :app:testReleaseUnitTest :app:assembleRelease
```

Do not use the local non-release fallback identity as evidence for a published
build. GitHub publication is performed by the manually dispatched
**Publish Relay Home release** workflow, which validates all prior APK version
codes, runs the isolated Android TV emulator suite, verifies package/version
and signer continuity, and creates the tag/release. The exact inputs and
post-publication checks are in [docs/RELEASES.md](docs/RELEASES.md).

## Issues and pull requests

Use the bug template for reproducible behavior. Include Relay version/build,
OEM/model, Android TV/API level, D-pad steps, expected/observed behavior, and
relevant local diagnostics. Remove account tokens, provider data, URLs that
contain credentials, and unrelated personal data before posting logs.
