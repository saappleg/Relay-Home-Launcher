# Relay Home

Relay Home is an Android TV / Google TV media launcher. It owns discovery and
D-pad navigation; providers retain responsibility for their catalogs, profiles,
and playback.

> Beta software — expect rough edges and report reproducible issues through
> GitHub Issues.

## Current beta

The latest published build is [v0.1.0-beta.7](https://github.com/saappleg/Relay-Home-Launcher/releases/tag/v0.1.0-beta.7), a GitHub prerelease with version code 30 and the signed APK `relay-home-0.1.0-beta.7.apk`. Its published SHA-256 digest is
`116bf035affa2870d66786dccb24ce5fc51a78f93f0e7261e1163b8c95eafbcb`.

Beta.7 packages the signed navigation, focus, accessibility, baseline-profile,
and Android TV interception hardening described in
[the release checklist](docs/RELEASES.md). The `feature/focus-info-preview`
branch is currently ahead of the beta.7 tag with follow-up fixes; those branch
changes are not a published release until a later signed APK is created.

## Features

- Media-first Home with a rotating hero, full-page focused-artwork backdrop,
  Continue Watching, subscriptions, recommendations, Coming Up, Favorite Apps,
  and App Peek.
- D-pad navigation across Home, details, Search, All Apps, provider hubs,
  Calendar, and paginated Settings. Root transitions reject stale focus and
  scroll callbacks.
- Opt-in provider tabs that persist between launches. Available providers are
  Nuvio, RelayTube, and Stremio; only enabled providers appear in Home
  navigation and hero candidates.
- Nuvio sign-in, encrypted session persistence, Nuvio profile switching,
  watch-progress sync, season/episode context, and title/resume handoff.
- Nuvio TV QR sign-in with explicit approval polling, expiry handling, and a
  manual code fallback. See [Nuvio authentication](docs/NUVIO_AUTH.md).
- RelayTube/SmartTube public-data integration for profile-scoped Continue
  Watching, subscriptions, active playback, rich video details, and direct
  video handoff. Full feed support requires the maintained RelayTube companion.
- Stremio board, search, and supported detail handoff through its public URI
  scheme; Relay does not synthesize a private Stremio catalog.
- TMDB artwork, title matching, recommendations, episode metadata, and
  calendar data, plus optional OMDb ratings, Fanart.tv artwork, and TheTVDB
  episode/season metadata.
- All Apps with alphabetical, recently-used, and recently-installed sorting;
  hidden-app controls; favorites; and configurable icon treatment. Relay itself
  is excluded from All Apps. Nuvio and RelayTube remain launchable there and
  can be favorited directly.
- Settings for appearance, date format, Home row order/visibility, minimal
  wallpaper Home, wallpaper image selection, hero source/item limits and
  rotation, weather and clock, app customization, provider limits, profile
  image, Data Sources, launcher setup, and update channel.
- Local crash evidence and recoverable-operation diagnostics under Settings >
  Device Settings > Show advanced diagnostics. Crash records contain bounded
  exception/device context and are not uploaded.
- In-app GitHub Releases updater with Stable and Beta & pre-releases channels,
  trusted HTTPS redirect validation, package/signature/version checks, and
  Android's standard install confirmation.

## Provider boundaries

Nuvio is authoritative for its library, profiles, progress, and playback
handoff. RelayTube profile IDs scope subscriptions and Continue Watching; a
selected Nuvio profile can be paired to a RelayTube profile in Settings >
Providers & Accounts > Profile pairing. Relay uses a unique normalized name
match until the user chooses a manual pairing. See
[Provider integration](docs/PROVIDER_INTEGRATION.md) for the bridge contract
and [Nuvio authentication](docs/NUVIO_AUTH.md) for login details.

Stock SmartTube is limited to Android public media-session and notification
metadata. Relay never reads private SmartTube history. Stremio remains
handoff-only because no supported launcher-facing catalog or Continue Watching
API is available here.

## Known limits

- Nuvio supplies the authoritative resume position and episode number. Its
  public Android TV URI opens the title or saved resume target; it does not
  expose a direct arbitrary-episode playback URI.
- Episode picker choices require an exact TV-series metadata match from the
  configured lookup service.
- Launcher replacement varies by firmware. Shizuku must already be running
  and authorized before Advanced Mode can apply its assisted override;
  Compatibility Mode uses the documented Accessibility auto-start fallback.
- A baseline-profile module is wired, but generation is explicit rather than
  part of every build. It currently profiles startup and a small Home D-pad
  traversal; no standalone before/after Macrobenchmark report is checked in.
- The Gradle connected-test guard refuses to run connected Android tests while
  a physical ADB target is attached. Use an Android TV emulator for
  `connectedCheck`; test a physical TV with a deliberately installed build and
  a direct `adb shell am instrument` invocation instead. See
  [Contributing](CONTRIBUTING.md).

## Build and test

Use JDK 17 and Android SDK Platform 35. A TMDB v3 key is optional for local
builds; put it in `local.properties` as `tmdb.apiKey` for the packaged default
metadata client, or configure user keys later in Settings > Data Sources.

Debug build and install:

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The debug APK uses the isolated application ID `com.relayhome.launcher.debug`.
Run the JVM tests, app lint, and debug packaging with:

```bash
./gradlew test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

For connected instrumentation, disconnect physical ADB targets first and make
sure an Android TV emulator is the only connected device. The build refuses
physical targets because Android's connected-test cleanup can alter launcher
state or remove a package on a real TV.

```bash
adb devices -l
./gradlew :app:connectedCheck
```

When the physical-target guard itself makes a direct emulator run preferable,
build and install both debug APKs, then invoke the runner explicitly:

```bash
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5554 shell am instrument -w -r \
  com.relayhome.launcher.debug.test/androidx.test.runner.AndroidJUnitRunner
```

Baseline-profile collection is also emulator-only and can install or launch
the production package. Disconnect physical ADB targets first, then run:

```bash
./gradlew :baselineprofile:generateRelayBaselineProfile
```

Normal app builds do not collect a profile automatically. Keep generated
profile output under the source-controlled app profile location when a
collection is intentionally accepted.

Normal local builds do not need release credentials. Production release
packaging does: configure the five `relay.signing.*` values shown in
[`signing.properties.example`](signing.properties.example) in ignored
`local.properties`, or provide the corresponding `RELAY_SIGNING_*` environment
variables. Then set an explicit release identity and run:

```bash
export RELAY_VERSION_NAME=0.1.0-beta.8
export RELAY_VERSION_CODE=31
./gradlew :app:verifyRelayReleaseVersion :app:lintRelease \
  :app:testReleaseUnitTest :app:assembleRelease
```

Release builds fail closed without explicit version values and a permanent
PKCS12 signing key. Do not commit API keys, signing credentials, provider
tokens, `local.properties`, or user library data. The complete release gate is
documented in [docs/RELEASES.md](docs/RELEASES.md).

## Beta smoke test

1. Enable one provider in Settings and confirm its tab remains after restart.
2. Connect Nuvio, switch profiles, and confirm the selected profile's Continue
   Watching and episode context are shown.
3. If RelayTube is installed, select a RelayTube profile and confirm both
   subscriptions and Continue Watching belong to that profile.
4. Open a media card, verify its details and provider handoff, and test resume
   from both Home and Details.
5. Exercise Left, Right, Up, Down, Back, and Select through Home, hero, rails,
   App Peek, details, Search, All Apps, Calendar, and Settings.
6. In Settings, verify Data Sources, Hero Banner controls, profile pairing,
  launcher diagnostics, and Settings > Launcher Updates.
7. Set Relay as the device Home app only after the above flows pass.

## License

Relay Home is licensed under GPL-3.0-only. See [LICENSE](LICENSE).
