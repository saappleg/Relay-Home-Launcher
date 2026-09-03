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

Before publishing:

1. Build and smoke-test both 1080p and 4K layouts.
2. Verify Nuvio and RelayTube data, direct playback, D-pad focus, and Home replacement.
3. Verify Settings > Updates on both Stable and Beta channels.
4. Run the workflow with the next version code and concise release notes.
5. Install the published asset on a clean TV, then test an update from that signed baseline.
