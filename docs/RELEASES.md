# Relay release checklist

Relay Home reads published releases from `saappleg/Relay-Home-Launcher`. Stable
mode ignores prereleases; Beta mode includes them. Every release must contain a
signed APK and use a SemVer-style tag such as `v0.1.0-alpha.1`.

The **Publish Relay Home release** workflow requires these repository secrets:

- `SIGNING_KEY`: Base64-encoded permanent PKCS12/JKS keystore
- `KEY_STORE_PASSWORD`
- `ALIAS`
- `KEY_PASSWORD`
- `TMDB_API_KEY`

Use a version code larger than every prior APK. Never replace the signing key
after the first signed beta.

The workflow validates the requested alpha, beta, or stable name, refuses to
continue when any previously published APK cannot be inspected, and checks
that the built APK's SHA-256 signing certificate matches the configured
keystore. It passes signing values to Gradle through ephemeral job environment
variables; they are not written into `local.properties`.

The initial `v0.1.0-beta.1` asset was debug-signed. Testers must uninstall that
one build before installing the first permanently signed beta. Future in-app
updates will then preserve app data normally.

## v0.1.0-alpha.1 release notes

Version code 7 adds icon-first Google TV navigation, the persisted Automatic
(Material You) appearance with an orbital fallback, and five-card compact rail
sizing. It also hardens Nuvio session expiry/profile isolation, Stremio public
deep-link handoff, and RelayTube/SmartTube public-data fallback behavior.

Stremio remains handoff-only: Relay does not read Stremio's merged catalog or
Continue Watching state because no supported launcher-facing API is available.
Full RelayTube feeds still require the maintained RelayTube bridge and a
compatible signing permission; stock SmartTube is limited to public media
session/notification data.

## v0.1.0-alpha.2 release notes

Version code 8 fixes the TV search layout at narrow widths, adds deterministic
first focus and scroll-to-top behavior across settings and provider pages, uses
Google TV-style circular treatment for opaque legacy app artwork, and hardens
GitHub alpha, beta, and stable update discovery and installation.
It also returns cleanly to Home after RelayTube provider, handoff, and playback
navigation instead of leaving RelayTube selected in the underlying launcher.

## v0.1.0-alpha.3 release notes

Version code 9 fixes a stale App Peek focus callback that could leave a previous
RelayTube, Nuvio, or Stremio peek visible after moving focus to Home, Calendar,
Apps, Search, or Settings.

## v0.1.0-alpha.4 release notes

Version code 10 fixes the in-app GitHub update check, which was requesting a
misspelled repository URL and returning HTTP 404.

## v0.1.0-alpha.5 release notes

Version code 11 accepts RelayTube alpha, beta, stable, and F-Droid bridge
broadcasts, discovers the installed RelayTube profile provider dynamically, and
keeps the package-specific bridge permissions aligned across flavors.

## v0.1.0-alpha.6 release notes

Version code 12 preserves Nuvio provider context through re-authentication,
hardens RelayTube return navigation, and validates GitHub update metadata,
downloaded APK identity, and signing-certificate continuity.

## v0.1.0-alpha.7 release notes

Version code 13 adds Nuvio TV QR sign-in with a scannable approval flow and
manual code fallback, improves search, details, Apps, and settings focus on
Android TV, and gives installed apps consistent circular icon treatment.
RelayTube bridge payloads, profile isolation, YouTube handoff validation, and
TMDB metadata normalization are also hardened for safer provider fallbacks.

## v0.1.0-alpha.8 release notes

Version code 14 fixes the Shizuku launcher override on Google TV by disabling
the higher-priority stock Home app before selecting and verifying Relay, and
restoring it if verification fails.

## v0.1.0-alpha.9 release notes

Version code 15 hardens the Google TV launcher override by disabling the exact
resolved stock Home activity, verifying that it disappears from Home
resolution, and falling back to the package when the OEM keeps the activity
available.

## v0.1.0-alpha.10 release notes

Version code 16 makes Shizuku recreate Relay's privileged launcher service when
the APK changes, preventing an older cached service implementation from being
reused after an update.

Before publishing:

1. Build and smoke-test both 1080p and 4K layouts.
2. Verify Nuvio and RelayTube data, direct playback, D-pad focus, and Home replacement.
3. Verify Settings > Updates on both Stable and Beta channels.
4. Run the workflow with the next version code and concise release notes.
5. Install the published asset on a clean TV, then test an update from that signed baseline.
