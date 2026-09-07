# Relay release checklist

Relay Home reads releases from `saappleg/Relay-Home-Launcher`. The updater's
Stable channel ignores prereleases; Beta & pre-releases includes them. Publish
only a SemVer-style tag such as `v0.1.0-beta.7`, with one signed production APK
whose package is `com.relayhome.launcher`.

## Published beta.6

[v0.1.0-beta.6](https://github.com/saappleg/Relay-Home-Launcher/releases/tag/v0.1.0-beta.6)
is the current published prerelease. It is not a draft and contains exactly
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

Beta.7 is the next signed prerelease and packages the completed next-phase
hardening work from the current branch:

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

The release workflow publishes this build as `v0.1.0-beta.7` with Android
version code `30`. The final tag, APK digest, signing certificate, and GitHub
release URL are recorded here after the workflow completes and are verified
against the published asset.

## Version and signing rules

Use a version code larger than every published APK. Beta.6 is code 29, so the
next beta must use at least code 30. Never replace the production signing key
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
This fallback is useful for debug and test APKs but is not the published beta.6
build and must never be used as release evidence. Release packaging fails closed
unless `RELAY_VERSION_NAME`, `RELAY_VERSION_CODE`, and the signing values are
explicitly provided. The local release keystore may be configured in ignored
`local.properties` or with `RELAY_SIGNING_*` environment variables.

## Local release validation

Use JDK 17. With a permanent PKCS12 keystore configured, run:

```bash
export RELAY_VERSION_NAME=0.1.0-beta.7
export RELAY_VERSION_CODE=30
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
  -f version_name=0.1.0-beta.7 \
  -f version_code=30 \
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
gh release view v0.1.0-beta.7 --repo saappleg/Relay-Home-Launcher \
  --json tagName,isDraft,isPrerelease,targetCommitish,assets
```

Confirm the release is not a draft, has the expected prerelease flag, contains
exactly `relay-home-0.1.0-beta.7.apk`, and that its tag points to the tested
revision. Install the published asset on a clean TV, then update from the
signed beta baseline and verify that app data remains intact.

## Updater and release asset requirements

The in-app updater follows a bounded HTTPS redirect chain and accepts only the
expected GitHub API/release-asset hosts. It requires one unambiguous APK with a
valid size, then verifies package identity, non-debuggable status, signing
certificate continuity, tag-matching semantic version, and an increasing
Android version code before showing Android's installer.

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
