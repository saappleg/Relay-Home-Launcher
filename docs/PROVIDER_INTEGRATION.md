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
Bridge payloads are bounded to 256 KiB and strict profile/feed snapshots are
bounded to 24 entries. Every entry must have a valid id and title; duplicate
ids, invalid numeric ranges, unsafe artwork URLs, malformed JSON, missing
profile ids, and oversized payloads are ignored so they cannot erase the last
valid snapshot. Feed responses from the content-provider bridge must echo the
requested profile id. Delayed feed reads are tied to the current
refresh/profile generation to prevent a previous profile from repopulating
Home after a profile switch. An explicit empty JSON array is a valid empty
snapshot; a partially malformed array is not.

The optional public content provider is discovered only for the maintained
RelayTube flavors and supports `profiles`, `select`, and `feeds` calls. Stock
SmartTube builds remain limited to Android's public media-session and
notification metadata. Relay never reads private SmartTube history. Nuvio to
RelayTube profile pairing requires one unique normalized profile-name match;
stored ids and selected-profile order are never used as a guess.

## TMDB metadata

TMDB is a read-only supplement. Nuvio remains the progress and library
authority. TMDB title matching has an explicit confidence boundary:

- `EXACT` is the highest-confidence match: titles are trimmed, accent-folded,
  lowercased, and compared after punctuation/spacing removal.
- `FUZZY` is considered only for normalized titles at least eight characters
  long. It requires at least 0.90 normalized edit similarity, or the same
  normalized title words in a different order, and a 0.08 score margin over
  the next candidate. If the threshold or margin is not met, Relay treats the
  lookup as unmatched and attaches no TMDB metadata.

The same boundary protects episode enrichment, season choices, upcoming
episodes, calendar entries, and recommendation seeds. Valid TMDB ids and
HTTPS artwork paths are still required. TMDB genre ids are converted to
display labels when available.

Nuvio requests use a 12-second connect/read timeout; TMDB uses 8 seconds. Both
boundaries retry transient network failures and HTTP 408/425/429/500/502/503/
504 responses at most three total attempts, with 300 ms then 600 ms backoff.
Non-idempotent Nuvio library writes and one-time QR exchanges are not retried
after a transport failure. Exhausted transient requests return typed provider
failures, while the existing list-returning compatibility methods expose an
empty result and leave already-enriched/provider-owned items unchanged.

## Stremio

Stremio remains handoff-only. Relay creates validated `stremio:///` board,
search, and supported detail links, but does not present a synthetic Stremio
catalog or claim access to Stremio's private Continue Watching data.

## Known boundaries

Nuvio remains the authority for library membership, profiles, progress, and
provider handoff. TMDB supplies metadata only and cannot establish that a
stream is playable. Stremio has no supported launcher-facing catalog or
Continue Watching API here, so Relay opens its public URI handoffs. Stock
SmartTube builds expose only Android public media-session/notification
metadata; profile-scoped feeds, subscriptions, and exact YouTube ids require
the maintained RelayTube companion bridge.
