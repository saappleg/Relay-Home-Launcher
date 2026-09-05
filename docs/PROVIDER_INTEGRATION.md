# Provider and metadata boundaries

Relay keeps provider ownership explicit. It may enrich display metadata and
open a provider's public handoff URI, but it does not scrape private provider
databases or replace provider playback.

## RelayTube / SmartTube

The maintained RelayTube companion can expose the following package-targeted,
permission-protected broadcasts:

- `com.relaytube.action.PLAYBACK` for an exact YouTube video id and public
  playback metadata.
- `com.relaytube.action.SUBSCRIPTIONS` and
  `com.relaytube.action.CONTINUE_WATCHING` for profile-scoped feed snapshots.
- `com.relaytube.action.PROFILES` for the available profile list.

Relay accepts a raw 11-character YouTube id or a validated public YouTube URL.
Malformed JSON, missing profile ids, unsafe artwork URLs, invalid video ids,
and oversized bridge payloads are ignored so they cannot erase the last valid
profile snapshot. Delayed feed reads are tied to the current refresh/profile
generation to prevent a previous profile from repopulating Home after a
profile switch.

The optional public content provider is discovered only for the maintained
RelayTube flavors and supports `profiles`, `select`, and `feeds` calls. Stock
SmartTube builds remain limited to Android's public media-session and
notification metadata. Relay never reads private SmartTube history.

## TMDB metadata

TMDB is a read-only supplement. Nuvio remains the progress and library
authority. Search and episode enrichment require an exact normalized title
match (including accent normalization), valid TMDB ids, and HTTPS artwork
paths. TMDB genre ids are converted to display labels when available. Network
failures leave the provider item unchanged.

## Stremio

Stremio remains handoff-only. Relay creates validated `stremio:///` board,
search, and supported detail links, but does not present a synthetic Stremio
catalog or claim access to Stremio's private Continue Watching data.
