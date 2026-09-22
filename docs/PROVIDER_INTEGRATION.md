# Provider integration

Relay keeps provider ownership explicit. It may normalize display metadata,
cache bounded public feed snapshots, and open a provider's public handoff URI;
it does not scrape private provider databases or replace provider playback.

The user-facing provider names are Nuvio, RelayTube, and Stremio. The source
model calls RelayTube `Provider.SMARTTUBE` for compatibility with the
SmartTube media-session integration.

## Nuvio

Nuvio is authoritative for its library membership, profiles, progress, episode
context, and playback handoff. Relay supports password login and the TV QR
flow described in [docs/NUVIO_AUTH.md](NUVIO_AUTH.md). Authenticated session
values are persisted through the encrypted Nuvio session store; Relay does not
store provider credentials in the general settings DataStore.

Nuvio media opens through the public `nuvio://meta` URI for `movie` and `tv`
items. The URI opens the title in Nuvio so Nuvio can choose a stream. It does
not provide Relay with a direct arbitrary-episode playback URI.

### Profile switching and pairing

The active Nuvio profile controls the Nuvio library sync and resume context.
When RelayTube profiles are available, Settings > Providers & Accounts >
Profile pairing maps each Nuvio profile to one RelayTube profile. Until a manual
pairing is selected, Relay uses one unique normalized profile-name match; it
does not guess from stored IDs or list position.

Changing profiles updates the active RelayTube profile ID and the two feed
snapshots together. Delayed provider responses are accepted only when their
profile ID still matches the selected profile and the current refresh
generation, so an old profile cannot repopulate Home after a switch.

## RelayTube / SmartTube

Full feed integration requires a maintained RelayTube companion build. Relay
recognizes these launchable package variants:

- `com.relaytube.stable`
- `com.relaytube.beta`
- `com.relaytube.fdroid`
- `app.smarttube.stable`
- `org.smarttube.stable`
- `org.smarttube.beta`

Relay accepts the companion's package-targeted, permission-protected broadcasts:

| Action | Purpose | Required extras |
| --- | --- | --- |
| `com.relaytube.action.PLAYBACK` | Active video/session update | `video_id`, `title`, playback metadata, optional `profile_id` |
| `com.relaytube.action.SUBSCRIPTIONS` | Profile-scoped subscription feed | `profile_id`, `videos` |
| `com.relaytube.action.CONTINUE_WATCHING` | Profile-scoped resume feed | `profile_id`, `videos` |
| `com.relaytube.action.PROFILES` | Available RelayTube profiles | `profiles`, optional selected `profile_id` |

The manifest uses package-specific `ACCESS_VIDEO_DATA` permissions for the
stable, beta, and F-Droid RelayTube receivers. Broadcast payloads are bounded
to 256 KiB, profile lists and feeds to 24 entries, and fields are validated for
IDs, text lengths, numeric ranges, and safe artwork URLs. Invalid, duplicate,
or partially malformed data is ignored; it cannot erase the last valid
profile-scoped snapshot. An explicit empty JSON array is a valid empty feed.

Relay also probes the maintained RelayTube content provider at
`content://<relaytube-package>.relayprofiles`. The supported calls are:

- `profiles`: return available profiles and the selected `profile_id`;
- `select`: request a profile selection by its exact profile ID;
- `feeds`: return `subscriptions`, `continue_watching`, and the echoed
  `profile_id` for the requested profile.

If the content-provider call is unavailable, Relay falls back to the
package-targeted profile request/selection broadcasts. Feed responses without
an echoed matching profile ID are rejected. Refreshes are retried on the
current generation after profile selection, while the last-known-good cache is
kept if the companion is unavailable.

RelayTube profile IDs are the authority for feed isolation. A profile name is
display-only; do not use it as a cache key or as proof that a delayed response
belongs to the active profile.

The optional `SmartTubeNowPlayingService` is an Android notification-listener
service for the active media session. It is opt-in through Android settings and
reads public session/notification metadata only. Relay never reads private
SmartTube history. Without RelayTube, stock SmartTube is limited to that
public metadata and cannot provide the profile-scoped feeds above.

Relay's Subscriptions settings filter only which Relay cards are shown for a
channel. They never modify the user's YouTube subscriptions.

## TMDB and optional metadata

TMDB is a read-only metadata supplement; it cannot establish that a provider
stream is playable. Title matching is exact-first: normalized exact matches
win, while fuzzy matches require a title at least eight characters long, at
least 0.90 normalized edit similarity (or the same words in another order),
and a 0.08 score margin over the next candidate. Otherwise Relay leaves the
item unmatched and attaches no TMDB metadata.

In Settings > Data Sources, the optional services are:

- TMDB: title matching, artwork, calendars, recommendations, and episode
  metadata;
- OMDb: optional critic-score metadata;
- Fanart.tv: optional higher-resolution artwork and logos;
- TheTVDB: optional TV season and episode metadata when provider data is
  incomplete.

TMDB and OMDb user keys are remotely verified before being saved. Fanart.tv and
TheTVDB keys are validated locally and stored hidden. Keys are not displayed
after saving. A packaged TMDB key may also be supplied to a build through
`tmdb.apiKey`/`RELAY_TMDB_API_KEY`; it is a build input, not a substitute for
user-owned provider credentials.

Nuvio requests use 12-second connect/read timeouts; TMDB uses 8 seconds. Both
retry transient failures and HTTP 408/425/429/500/502/503/504 up to three total
attempts with 300 ms and 600 ms backoff. Non-idempotent Nuvio library writes
and one-time QR exchanges are not retried after a transport failure.

## Stremio

Stremio is handoff-only. Relay creates validated `stremio:///` board, search,
and supported detail links, but does not present a synthetic Stremio catalog or
claim access to Stremio's private Continue Watching data.

## Safety and failure behavior

Provider and Android-framework work runs outside the Compose rendering path
where required. Recoverable failures preserve cached/local content when
possible and appear as bounded local diagnostics under Settings > Device
Settings > Show advanced diagnostics. Credentials, provider tokens, and feed
payloads are not written to those diagnostics.
