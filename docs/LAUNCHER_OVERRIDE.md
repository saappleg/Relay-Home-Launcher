# Launcher override reliability

Relay Home uses the normal Android Home resolver first. The Shizuku override is an explicitly
requested, user-authorized compatibility path for TV firmware that keeps reclaiming the Home
role. It exposes only the two launcher operations in `IRelayHomeShell`; it is not a general ADB
or shell interface.

## Fallback ladder

When the user chooses **Make Relay Home default with Shizuku**, Relay attempts the following in
order. A step is considered successful only after the Home resolver reports
`com.relayhome.launcher` as the selected package.

1. **Component disable** — disable the detected stock Home component with
   `pm disable-user --user 0 <stock-package>/<stock-activity>`, apply Relay as Home, and verify
   the resolved Home component.
2. **Package-level override** — if the component step fails, restore the component state when
   needed, disable the stock package with `pm disable-user --user 0 <stock-package>`, apply Relay
   as Home, and verify again.
3. **Home-intent priority** — if package disable also fails, restore the stock package when
   needed, apply the Android HOME role and `set-home-activity` for Relay without disabling the
   stock package, and verify the resolver one more time.

If every step fails, Relay attempts to re-enable and restore the stock launcher and reports
whether that recovery itself was verified. A failed operation never reports Relay as the active
strategy. If no stock launcher is available, the ladder starts at Home-intent priority.

The optional **Relay Home auto-start** accessibility service is a separate compatibility aid. It
launches Relay when a known stock TV launcher window becomes active, but Android does not provide
an immediate visibility guarantee for `startActivity`. Its diagnostic result is therefore marked
`unverified`, never as a successful Home override.

## Diagnostics

Settings > Launcher > **Override diagnostics** shows the current resolver-backed strategy, the
reason it is shown, device/API information, and the most recent local event records. Events use
structured fields for:

- operation (`set_relay_home`, `restore_stock_launcher`, or accessibility/Shizuku support work);
- strategy (`component_disable`, `package_level_override`, or `home_intent_priority`);
- phase (`attempt`, `command`, `verification`, `cleanup`, or `service`);
- outcome (`started`, `success`, `failure`, or `unverified`);
- cause, target component, observed Home resolver output, and fixed command name where relevant.

The same JSON-shaped records are written to local Logcat with the tag `RelayLauncherOverride`.
Relay retains a bounded recent history in its existing launcher-override preferences. Nothing in
this flow sends telemetry or uploads device information. For a live trace, use:

```text
adb logcat -s RelayLauncherOverride:I
```

Interpretation:

- **Component disable** or **Package-level override** means that disable step and the final Home
  resolver check both succeeded in the last recorded apply operation.
- **Home-intent priority** means Relay is currently selected by the resolver, but no verified
  disable step is recorded (including the normal Android Home selection path).
- **Not active** means the current resolver does not select Relay, regardless of an earlier
  successful attempt. The `Why` text and the failure event identify the last known cause.
- A command with `success` is not itself proof that Home changed. Look for the later strategy
  `verification` event with `success` and an observed Relay component.
- A cleanup or stock-restore event must also have a `verification` success before recovery is
  considered verified.

OEM command behavior varies. A package manager command can return exit code zero while the
firmware still resolves its privileged launcher, which is why Relay records command results and
resolver verification separately.

## OEM/firmware issue template

Copy this template into an issue. Remove account tokens, provider data, and any other private
information before posting.

```text
### Device
- Relay version/build:
- OEM and model:
- Android TV / Google TV version:
- Android API level:
- Firmware/build number:
- Shizuku version and startup mode (ADB/root):
- Stock launcher package/activity:

### Expected
Relay Home remains the selected Home app after applying the Shizuku override.

### Reproduction
1. Open Relay Settings > Launcher.
2. Authorize Relay in Shizuku.
3. Choose Make/Re-apply Relay Home with Shizuku.
4. Reboot or press Home, as applicable.

### Observed result
- Home resolver after the attempt:
- Strategy shown under Override diagnostics:
- Diagnostics Why text:
- Does the stock launcher reclaim Home after reboot? (yes/no)
- Does Accessibility auto-start change the result? (yes/no/not tested)

### Local evidence
Paste the Override diagnostics event list and, if available, the filtered output from:

adb logcat -d -s RelayLauncherOverride:I

Do not include provider tokens, account credentials, or unrelated personal data.

### Recovery check
- Was stock launcher restore requested? (yes/no)
- Did diagnostics verify stock launcher as Home? (yes/no)
- Any output/error text from the device:
```
