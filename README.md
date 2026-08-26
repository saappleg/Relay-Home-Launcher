# Relay Home — alpha

Relay Home is an Android TV / Google TV media launcher. It is the discovery and navigation layer; providers retain responsibility for their catalogs, profiles, and playback.

> Alpha software — test builds are not yet published as GitHub Releases.

## Alpha behavior

- A media-first 10-foot Home view with a provider-powered rotating hero, Continue Watching, and Recommended TV Shows.
- D-pad focus behavior across Home, App Peek, details, Search, All Apps, provider hubs, and Settings.
- Provider tabs are opt-in and persist between launches. Home shows only the providers enabled in Settings.
- Nuvio sign-in, encrypted session persistence, profile switching, watch-progress sync, season/episode context, and title/resume deep-link handoff.
- SmartTube active Now Playing via an optional notification-listener permission; direct YouTube-video handoff to the installed SmartTube variant.
- Stremio board and search handoff through its public URI scheme.
- Android TV launcher role request, a separate All Apps screen, and real installed-app discovery.

## Known alpha limits

- Nuvio supplies the authoritative resume position and episode number. Its public Android TV URI opens the title or saved resume target; it does not expose a direct arbitrary-episode playback URI.
- SmartTube does not expose its private watch history to third-party launchers. Relay can show only its active media session.
- Stremio’s live catalog/continue-watching feed is not yet available through a supported launcher-facing API, so its Home cards remain curated preview content.
- Episode picker choices are populated only when the configured metadata lookup can make an exact TV-series match.

## Build

1. Open the project in Android Studio with JDK 17 and Android SDK Platform 35.
2. Add a TMDB v3 key as `tmdb.apiKey` in `local.properties` to enable exact metadata-backed episode choices.
3. Build `app:assembleDebug` and install the APK on an Android TV emulator or test device.

Command line:

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`local.properties`, Gradle caches, and build outputs are intentionally excluded from Git. Before publishing, create an empty GitHub repository and add it as the `origin` remote; do not commit API keys or provider session data.

## Alpha smoke test

1. Enable one provider in Settings; confirm its tab remains after an app restart.
2. Connect Nuvio, change profiles, and confirm Continue Watching reflects the selected profile.
3. Open a Nuvio card and verify its season/episode line and provider handoff.
4. Enable SmartTube Now Playing, play a video in SmartTube, and return to Relay.
5. Use D-pad navigation through Home, App Peek, details, Search, All Apps, and Settings.
6. Set Relay as the device Home app only after the above flows pass.

## License

Relay Home is intended to be released under GPL-3.0-only. The full license text will be included before the first public push.
