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

## Beta.7 release notes

Beta.7 is the current published signed prerelease and packages the next-phase
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

The `feature/focus-info-preview` branch is ahead of the beta.7 tag. Its
post-beta.7 follow-ups include clearer debug-versus-production updater guidance,
an additional Home route-restoration focus acknowledgement, Settings tile
accessibility semantics, and bounded Continue Watching adaptation. These branch
changes are not part of the published beta.7 APK; validate and release them
under a new version after the normal release gate passes.

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

Beta.8 is published only after the full JVM, lint, APK, emulator, release
workflow, and non-destructive physical-TV update checks pass. Record the final
tag target, asset SHA-256, package/version, and signing certificate below after
publication.

## Version and signing rules

Use a version code larger than every published APK. Beta.7 is code 30, so the
next beta must use at least code 31. Never replace the production signing key
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

Non-release local builds use the development identity `0.1.0-beta.5` / code 28.
This fallback is useful for debug and test APKs but is not the published beta.7
build and must never be used as release evidence. Release packaging fails closed
unless `RELAY_VERSION_NAME`, `RELAY_VERSION_CODE`, and the signing values are
explicitly provided. The local release keystore may be configured in ignored
`local.properties` or with `RELAY_SIGNING_*` environment variables.

## Local release validation

Use JDK 17. With a permanent PKCS12 keystore configured, run:

```bash
export RELAY_VERSION_NAME=0.1.0-beta.8
export RELAY_VERSION_CODE=31
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
  --ref feature/focus-info-preview \
  -f channel=beta \
  -f version_name=0.1.0-beta.8 \
  -f version_code=31 \
  -f release_notes='Short, factual release notes.'
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
gh release view v0.1.0-beta.8 --repo saappleg/Relay-Home-Launcher \
  --json tagName,isDraft,isPrerelease,targetCommitish,assets
```

Confirm the release is not a draft, has the expected prerelease flag, contains
exactly `relay-home-0.1.0-beta.8.apk`, and that its tag points to the tested
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

The baseline-profile module is wired but generation is explicit and is not a
release-workflow step. It currently covers startup and a short Home D-pad
traversal. Collect it only on an emulator with no physical ADB target attached:

```bash
./gradlew :baselineprofile:generateRelayBaselineProfile
```

There is not yet a standalone Macrobenchmark before/after report. Review any
generated profile output and measure cold start before treating it as a release
performance claim.
