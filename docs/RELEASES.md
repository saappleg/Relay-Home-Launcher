# Relay release checklist

Relay Home reads releases from `saappleg/Relay-Home-Launcher`. The updater's
Stable channel ignores prereleases; Beta & pre-releases includes them. Publish
only a SemVer-style tag such as `v0.1.0-beta.7`, with one signed production APK
whose package is `com.relayhome.launcher`.

## Published beta.6 (previous signed prerelease)

[v0.1.0-beta.6](https://github.com/saappleg/Relay-Home-Launcher/releases/tag/v0.1.0-beta.6)
is a previous published prerelease. It is not a draft and contains exactly
one APK asset:

- file: `relay-home-0.1.0-beta.6.apk`;
- Android version code/name: `29` / `0.1.0-beta.6`;
- SHA-256: `60ef291b71700af45ef2051307d08a7d383542a7568137f5998f6e82e0d5f49b`;
- release tag target: `240bf8b6762c9c3b0b48d2cdfb258060ac48d26e`; and
- production signing certificate SHA-256: `4da8c2767f2e47a8a95c74bda80e9349c4e5b1b0e8fdb2b52d3bd0775d68bc21`.

Beta.6 contains the root navigation-generation hardening and Home focus graph
cleanup. It rejects stale focus/scroll restoration after destination changes,
removes orphaned profile and invisible favorite-app targets, keeps recycled-card
requesters tied to mounted content, and covers Details-to-Home, nested Settings
Back, hero/rail handoffs, and rapid D-pad traversal.

## Beta.7 release notes (historical)

Beta.7 was a published signed prerelease and packages the next-phase
hardening work that was complete at its tag:

- root navigation transitions use generation-aware focus restoration, preventing
  stale Details/Home callbacks and redundant scroll resets during rapid D-pad
  navigation;
- Home focus cleanup removes orphaned profile/favorite bridges and stale
  recycled-card requesters, with coverage for mounted Home/Hero/rail targets;
- Home, Apps, and Settings custom controls expose contextual accessibility
  semantics, including hero actions, media-card state, icon-only actions, and
  switch state, with Compose accessibility-tree coverage;
- a baseline-profile module and explicit emulator-only profile collection task
  are wired into release packaging; the measured cold-start improvement is
  documented separately and is not presented as a workflow benchmark result;
- the Android TV long-press Home/Back interception spike found no reliable
  global interception path, so Relay does not add a recent-app overlay or
  accessibility privilege. Use the remote's dedicated Recent/Overview control
  or the device's multitasking gesture instead.

Published beta.7 verification:

- release: [v0.1.0-beta.7](https://github.com/saappleg/Relay-Home-Launcher/releases/tag/v0.1.0-beta.7);
- tag target: `68c19639ffc68ea2a360f1e1ebcfba8e3928e399`;
- asset: `relay-home-0.1.0-beta.7.apk`;
- asset SHA-256: `116bf035affa2870d66786dccb24ce5fc51a78f93f0e7261e1163b8c95eafbcb`;
- package/version: `com.relayhome.launcher`, `0.1.0-beta.7`, versionCode `30`;
- production signing certificate SHA-256:
  `4da8c2767f2e47a8a95c74bda80e9349c4e5b1b0e8fdb2b52d3bd0775d68bc21`.

The GitHub release is non-draft and marked as a prerelease. The APK was
downloaded from that release and independently checked before TV installation.

Beta.8 and beta.9 added later follow-ups. Beta.10's signed APK uses version
code 33 and has SHA-256
`c887c334b93756422010aa3514e8d749cafc9b18f59479984c67cda00bde3094`; its tag
points to `11be5a7bccd2dc913d437e933c19e3053c69b5d9`.

## Published beta.12 verification

[v0.1.0-beta.12](https://github.com/saappleg/Relay-Home-Launcher/releases/tag/v0.1.0-beta.12)
is the latest published prerelease. Its signed APK is `relay-home-0.1.0-beta.12.apk`:

- workflow: [GitHub Actions run 36258512877](https://github.com/saappleg/Relay-Home-Launcher/actions/runs/36258512877);
- tag target: `3badc8856fd25059dc3c86b9617030bd273e7f58`;
- package/version: `com.relayhome.launcher`, `0.1.0-beta.12`, versionCode `35`;
- asset SHA-256: `708f40c3265a299af596949dc4ac650250ee9f4b5f71e39314a920b2b87b9ec0`;
- production signing certificate SHA-256:
  `4da8c2767f2e47a8a95c74bda80e9349c4e5b1b0e8fdb2b52d3bd0775d68bc21`.

The release is non-draft and marked as a prerelease. The isolated Android TV
emulator suite passed before the workflow verified the signed APK and published
it. The downloaded APK's SHA-256 matches the GitHub asset digest.

Beta.12 reduces Home rendering work, restores the exact Home card and scroll
position after App Peek, and keeps artwork changes settled while navigating.
RelayTube feed reads retain last-known-good data through placeholder responses
and retry delayed provider updates. Profile changes use RelayTube's
package-verified provider call on a serialized background worker, with the
signature-protected broadcast as a fallback.

## Published beta.11 verification (historical)

[v0.1.0-beta.11](https://github.com/saappleg/Relay-Home-Launcher/releases/tag/v0.1.0-beta.11)
was the latest published prerelease before beta.12. Its signed APK is `relay-home-0.1.0-beta.11.apk`:

- workflow: [GitHub Actions run 35789140861](https://github.com/saappleg/Relay-Home-Launcher/actions/runs/35789140861);
- tag target: `ac61b1251fa9e950883d531c9c6b0b6b87a834a1`;
- package/version: `com.relayhome.launcher`, `0.1.0-beta.11`, versionCode `34`;
- asset SHA-256: `6b836420b23afa566f397f6bf8fb79781df4065c3f1914d8c7a1b39819c9468a`;
- production signing certificate SHA-256:
  `4da8c2767f2e47a8a95c74bda80e9349c4e5b1b0e8fdb2b52d3bd0775d68bc21`.

The release is non-draft and marked as a prerelease. The isolated Android TV
emulator suite passed all 109 tests before the workflow verified the signed
APK and published it. The downloaded APK's SHA-256 matches the GitHub asset
digest.

Beta.11 brings the newer Home/App Peek implementation together with beta.10's
Nuvio session, RelayTube naming, TMDB, and Shizuku fixes. It also includes
acknowledged Home focus restoration after App Peek, settled-card artwork
updates, account-scoped Nuvio/RelayTube mappings, profile-specific search
history, and preserved profile selection when a corrupt session token is
cleared.

## Beta.8 release notes

Beta.8 packages the post-beta.7 follow-up fixes and audit results:

- Home route restoration now acknowledges focus after visibility-based provider
  handoffs, preventing a stale temporary focus suppression state;
- the Home focus graph remains limited to mounted, visible interactive targets,
  with orphan-focus and D-pad regression coverage;
- Settings custom tiles expose user-facing accessibility labels and decorative
  symbols stay out of the merged accessibility tree;
- Continue Watching adaptation is bounded before Home composition performs the
  configured limiting and mapping work;
- updater diagnostics clearly explain that a debug package cannot in-place
  update from a signed production GitHub APK;
- release documentation records the signed production workflow and the tested
  Android TV long-press interception limitation.

Beta.8 is published only after the full JVM, lint, APK, emulator, and release
workflow checks pass. Its final tag target, asset SHA-256, package/version, and
signing certificate are recorded below; the physical-TV update verification is
completed as a separate post-publication gate.

Published beta.8 verification:

- release: [v0.1.0-beta.8](https://github.com/saappleg/Relay-Home-Launcher/releases/tag/v0.1.0-beta.8);
- workflow: [GitHub Actions run 34184737399](https://github.com/saappleg/Relay-Home-Launcher/actions/runs/34184737399);
- tag target: `1ca2eb44f2b9693e9299e31942eeb03820e19107`;
- asset: `relay-home-0.1.0-beta.8.apk`;
- asset SHA-256: `c1247a9a15001b5ba8666f21261d65938d7e35e15cb66deb7ef448e030b1774c`;
- package/version: `com.relayhome.launcher`, `0.1.0-beta.8`, versionCode `31`;
- production signing certificate SHA-256:
  `4da8c2767f2e47a8a95c74bda80e9349c4e5b1b0e8fdb2b52d3bd0775d68bc21`.

The GitHub release is non-draft and marked as a prerelease. The workflow's
isolated Android TV emulator suite passed before signing and publication.

## Version and signing rules

Use a version code larger than every published APK. Beta.12 is code 35, so the
next beta must use at least code 36. Never replace the production signing key
after the first signed beta; Android updates require certificate continuity.

The publish workflow requires these repository secrets:

- `SIGNING_KEY`: Base64-encoded permanent PKCS12 keystore;
- `KEY_STORE_PASSWORD`;
- `ALIAS`;
- `KEY_PASSWORD`; and
- `TMDB_API_KEY`.

The checked-in signing example uses the corresponding five
`relay.signing.*` values: store file, store type, store password, key alias,
and key password. A JKS keystore is not accepted by the publish workflow.

Non-release local builds use the development identity `0.1.0-beta.13` / code 36.
This fallback is useful for debug and test APKs but is not the published beta.12
build and must never be used as release evidence. Release packaging fails closed
unless `RELAY_VERSION_NAME`, `RELAY_VERSION_CODE`, and the signing values are
explicitly provided. The local release keystore may be configured in ignored
`local.properties` or with `RELAY_SIGNING_*` environment variables.

## Local release validation

Use JDK 17. With a permanent PKCS12 keystore configured, run:

```bash
export RELAY_VERSION_NAME=0.1.0-beta.13
export RELAY_VERSION_CODE=36
./gradlew :app:verifyRelayReleaseVersion :app:lintRelease \
  :app:testReleaseUnitTest :app:assembleRelease
```

The output APK is `app/build/outputs/apk/release/app-release.apk`. Inspect its
package, version, and signer before distributing it. Do not commit the
keystore, passwords, API keys, `local.properties`, or the APK.

## GitHub publication

Publishing is performed by the manually dispatched **Publish Relay Home
release** workflow in `.github/workflows/publish-release.yml`. Its inputs are
`channel` (`alpha`, `beta`, or `stable`), `version_name`, `version_code`, and
`release_notes`. For an authorized GitHub CLI session, an equivalent dispatch
is:

```bash
gh workflow run publish-release.yml \
  --repo saappleg/Relay-Home-Launcher \
  --ref main \
  -f channel=beta \
  -f version_name=0.1.0-beta.13 \
  -f version_code=36 \
  -f release_notes='Describe the changes included in the next release.'
```

The workflow checks out exactly the requested revision, validates the channel
and SemVer input, downloads and inspects every previously published APK over
HTTPS, and refuses a non-increasing version code. It then runs
`connectedCheck` on an isolated API 34 Android TV x86 `tv_1080p` emulator with
animations disabled, builds the signed release, checks package/version,
verifies the APK certificate against the configured PKCS12 key, and publishes
the prerelease or stable release with the correct flag. The runner removes the
keystore after the job.

The Gradle build refuses connected tests while a physical ADB target is
attached. This is intentional: Android's connected-test lifecycle owns package
cleanup and can alter launcher state or remove a Relay package on a real TV.
The release workflow uses its isolated emulator and is the supported release
instrumentation path; a physical TV is for non-destructive post-release smoke
testing only.

After the workflow completes, verify the release rather than relying on the
workflow's display alone:

```bash
gh run list --repo saappleg/Relay-Home-Launcher --workflow publish-release.yml --limit 5
gh release view v0.1.0-beta.13 --repo saappleg/Relay-Home-Launcher \
  --json tagName,isDraft,isPrerelease,targetCommitish,assets
```

Confirm the release is not a draft, has the expected prerelease flag, contains
exactly `relay-home-0.1.0-beta.13.apk`, and that its tag points to the tested
revision. Install the published asset on a clean TV, then update from the
signed beta baseline and verify that app data remains intact.

## Updater and release asset requirements

The in-app updater follows a bounded HTTPS redirect chain and accepts only the
expected GitHub API/release-asset hosts. It requires one unambiguous APK with a
valid size, then verifies package identity, non-debuggable status, signing
certificate continuity, tag-matching semantic version, and an increasing
Android version code before showing Android's installer.

Debug builds are intentionally installed under `com.relayhome.launcher.debug`
and cannot consume GitHub production APKs, which use `com.relayhome.launcher`.
When a debug build checks for updates, Relay reports that the signed production
build must be installed separately; it never treats the package suffix as an
in-place update and never weakens signer, release-tag, or version checks.

The original `v0.1.0-beta.1` asset was debug-signed. Testers must uninstall
that build before installing the first permanently signed beta. Future signed
updates preserve app data normally.

## Release history

- `v0.1.0-alpha.1` (code 7) introduced the icon-first TV navigation, persisted
  appearance modes, compact rails, and hardened provider handoffs.
- `v0.1.0-alpha.2` (code 8) fixed narrow-TV Search, settings/provider focus
  restoration, app artwork treatment, and update discovery/install checks.
- `v0.1.0-alpha.3`–`alpha.5` (codes 9–11) fixed stale App Peek focus, the GitHub
  update endpoint, and RelayTube flavor/profile-provider discovery.
- `v0.1.0-alpha.6`–`alpha.12` (codes 12–18) hardened Nuvio re-auth, QR login,
  search/details focus, launcher override verification, and firmware fallbacks.
- `v0.1.0-beta.1` (code 24) brought together Home rails, provider feeds, the
  paged All Apps grid, direct handoff, and off-main-thread discovery.
- `v0.1.0-beta.6` (code 29) is the previous signed prerelease described above.
- `v0.1.0-beta.7` (code 30) contains the next-phase navigation/focus,
  accessibility, baseline-profile, and Android TV interception-limit work
  described above.
- `v0.1.0-beta.8` (code 31) contains the post-beta.7 focus-restoration,
  accessibility, bounded Continue Watching, updater-diagnostics, and release
  documentation follow-ups described above.
- `v0.1.0-beta.10` (code 33) contains configurable Home rows, ordered favorites,
  profile-safe Nuvio syncing and automatic refresh, TMDB caching, RelayTube-aware
  handoff, and safer Shizuku launcher switching.
- `v0.1.0-beta.11` (code 34) restores the latest Home/App Peek experience,
  confirms focus on a visible Home target after peeks, coalesces focused-art
  updates, scopes Nuvio/RelayTube profile mappings and search history, and
  preserves Nuvio profile selection when a corrupt session is cleared.
- `v0.1.0-beta.12` (code 35) reduces Home/App Peek rendering work, restores the
  exact Home card and scroll position, preserves settled artwork during D-pad
  movement, retries RelayTube feed sync safely, and switches profiles through
  the package-verified provider API across differently signed companion builds.

The baseline-profile module is wired but generation is explicit and is not a
release-workflow step. It currently covers startup and a short Home D-pad
traversal. Collect it only on an emulator with no physical ADB target attached:

```bash
./gradlew :baselineprofile:generateRelayBaselineProfile
```

There is not yet a standalone Macrobenchmark before/after report. Review any
generated profile output and measure cold start before treating it as a release
performance claim.
