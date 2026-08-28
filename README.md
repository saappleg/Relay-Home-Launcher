# Relay Home

Relay Home is an Android TV / Google TV media launcher. It is the discovery and navigation layer; providers retain responsibility for their catalogs, profiles, and playback.

> Beta software — expect rough edges and report reproducible issues through GitHub Issues.

## Beta features

- A media-first 10-foot Home view with a provider-powered rotating hero, Continue Watching, and Recommended TV Shows.
- D-pad focus behavior across Home, App Peek, details, Search, All Apps, provider hubs, and Settings.
- Provider tabs are opt-in and persist between launches. Home shows only the providers enabled in Settings.
- Nuvio sign-in, encrypted session persistence, profile switching, watch-progress sync, season/episode context, and title/resume deep-link handoff.
- RelayTube/SmartTube Continue Watching, subscriptions, active playback, rich video details, and direct video handoff.
- Stremio board and search handoff through its public URI scheme.
- TMDB-backed artwork, recommendations, episode metadata, and calendar data.
- Weekly/monthly calendar, installed-app launcher, profile image overrides, provider limits, and paginated settings.
- In-app GitHub Releases updater with Stable-only and Include-betas channels, signed APK download, and Android's standard install confirmation.

## Known beta limits

- Nuvio supplies the authoritative resume position and episode number. Its public Android TV URI opens the title or saved resume target; it does not expose a direct arbitrary-episode playback URI.
- Full SmartTube integration requires RelayTube, Relay's compatibility build of SmartTube. Stock SmartTube is limited to data Android exposes publicly.
- Stremio’s live catalog/continue-watching feed is not yet available through a supported launcher-facing API.
- Episode picker choices are populated only when the configured metadata lookup can make an exact TV-series match.
- Launcher replacement varies by device firmware. Shizuku must already be running and authorized before Relay can apply the assisted switch.

## Build

1. Open the project in Android Studio with JDK 17 and Android SDK Platform 35.
2. Add a TMDB v3 key as `tmdb.apiKey` in `local.properties` to enable exact metadata-backed episode choices.
3. Build `app:assembleDebug` and install the APK on an Android TV emulator or test device.

For a distributable APK, create a permanent PKCS12 signing key and add the four `relay.signing.*`
entries from [`signing.properties.example`](signing.properties.example) to the ignored
`local.properties` file. Relay intentionally refuses release builds until these values are set.
Release publishing and updater requirements are documented in [`docs/RELEASES.md`](docs/RELEASES.md).

Command line:

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`local.properties`, signing keys, Gradle caches, and build outputs are intentionally excluded from Git. Never commit API keys, signing credentials, or provider session data.

## Beta smoke test

1. Enable one provider in Settings; confirm its tab remains after an app restart.
2. Connect Nuvio, change profiles, and confirm Continue Watching reflects the selected profile.
3. Open a Nuvio card and verify its season/episode line and provider handoff.
4. Install RelayTube, play a video, and confirm Continue Watching, subscriptions, App Peek, details, and direct resume handoff.
5. Use D-pad navigation through Home, App Peek, details, Search, All Apps, and Settings.
6. Set Relay as the device Home app only after the above flows pass.
7. Open Settings > Updates and confirm GitHub reports the installed version as current.

## License

Relay Home is licensed under GPL-3.0-only. See [LICENSE](LICENSE).
