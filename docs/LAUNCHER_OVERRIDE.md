# Launcher override reliability

Relay Home uses Android's normal Home resolver first. The Shizuku override is
an explicitly requested, user-authorized compatibility path for firmware that
keeps reclaiming the Home role. It exposes only the two launcher operations in
`IRelayHomeShell`; it is not a general ADB or shell interface.

The controls live in Settings > Device Settings. The separate Compatibility
Mode uses the optional **Relay Home auto-start** Accessibility service.

## Setup modes

### Advanced Mode

Advanced Mode uses Shizuku and does not enable the Accessibility service. After
the user authorizes Shizuku, Relay attempts this verified fallback ladder:

1. **Component disable** — disable the detected stock Home component for user
   0, select Relay as Home, and verify the resolved Home component.
2. **Package-level override** — if component disable fails, restore component
   state as needed, disable the stock launcher package for user 0, select Relay,
   and verify again.
3. **Home-intent priority** — if package disable also fails, restore the stock
   package as needed, apply Android's Home role and `set-home-activity` for
   Relay, and verify the resolver again.

Each strategy is active only when the Home resolver observes
`com.relayhome.launcher`. If all strategies fail, Relay attempts to re-enable
and restore the stock launcher and reports whether that recovery was verified.
If no stock launcher is detected, the ladder starts at Home-intent priority.

OEM behavior varies. A package-manager command can return success while the
firmware still resolves its privileged launcher, which is why command success
and resolver verification are recorded separately.

### Compatibility Mode

Compatibility Mode asks the user to enable **Relay Home auto-start** in Android
Accessibility settings. It launches Relay when a known stock TV launcher
window becomes active. Android does not provide an immediate visibility or Home
resolver guarantee for `startActivity`, so this mode is reported as observed or
`unverified`, never as a verified Home override. Accessibility services can
also add a small system performance cost.

### Long-press Home/Back and recent apps

The current Compatibility Mode service is intentionally a window-state
auto-start service. Its declaration does not request key filtering, and Relay
does not claim a global `Home` or `Back` key hook. This preserves normal system
navigation and avoids asking for the broad accessibility capability that can
inspect/filter typed input.

The repeated hardware spike on 2026-09-07 used the authorized ADB target
`192.168.1.103:35543` (`onn 4K Pro Streaming Device`, Android 14/API 34,
build `URO3.260203.035.A1.15640252`). The installed app was the production
`com.relayhome.launcher`, version `0.1.0-beta.7`, version code `30`, with
Relay PID `27649`. The device state was observed without uninstalling Relay,
clearing data, changing the launcher role, or enabling an accessibility
service:

- From Relay Home, five long Home holds, five ordinary Home presses, five
  long Back holds, and five ordinary Back presses all left
  `com.relayhome.launcher/.MainActivity` resumed. No recent-app overlay
  appeared and the process PID stayed `27649`.
- From the root `com.android.tv.settings/.MainSettings` task, five long Home
  holds left Settings resumed every time. As the matched control, five
  ordinary Home presses resolved Relay every time. This distinguishes the
  normal Home resolver path from a long-press interception path.
- From that same Settings root, five long Back holds and five ordinary Back
  presses left Settings resumed every time. The root Settings task consumed
  Back; no Relay-owned overlay or global interception was observed.
- After the matrix, one ordinary Home press restored Relay Home. The Relay
  PID was still `27649`, and a 2,200-line recent Logcat scan contained no
  `FATAL EXCEPTION`, `AndroidRuntime`, or Relay crash marker.
- `enabled_accessibility_services` was `null`; `Bound services` and `Enabled
  services` were empty. Source/configuration inspection confirms that
  `RelayAutoStartService` listens only for `TYPE_WINDOW_STATE_CHANGED`, its
  XML declares only `typeWindowStateChanged`, `canRetrieveWindowContent=false`,
  and it has no key-filter or `onKeyEvent` path.

These ADB holds are repeatable input-injection controls, not a substitute for
every OEM remote model. They nevertheless match the source/configuration
boundary: Home is system-owned, Back is delivered to the focused app/task, and
Relay's current accessibility service is not a key-filter service. On this
production onn firmware, the evidence does not establish a reliable,
app-local way to turn either long press into a recent-app overlay. This issue
therefore remains a spike result only; any overlay would require a separate
follow-up investigation using an OEM-supported Recent/Overview integration.

Recommended fallback: keep Relay as the selected Home app and use the TV
remote's dedicated Recent/Overview control, if present, or the OEM's own
multitasking gesture. Do not enable key filtering or add a broad accessibility
privilege solely to synthesize a recent-app overlay; that would be a new
security/performance surface without a reliable Home/Back guarantee.

## Diagnostics

Open Settings > Device Settings > **Show advanced diagnostics**. The screen
shows the current resolver-backed strategy, why it is shown, device/API
information, recent local events, recoverable operation errors, and the last
process crash when one was recorded.

Override events use structured fields for:

- operation (`set_relay_home`, `restore_stock_launcher`, or Accessibility/
  Shizuku support work);
- strategy (`component_disable`, `package_level_override`,
  `home_intent_priority`, `accessibility_auto_start`, or `none`);
- phase (`attempt`, `command`, `verification`, `cleanup`, or `service`);
- outcome (`started`, `success`, `failure`, or `unverified`); and
- cause, target component, observed Home resolver output, and fixed command
  name when relevant.

Relay retains at most 48 recent override events in local preferences and emits
the same JSON-shaped records to Logcat with the `RelayLauncherOverride` tag:

```bash
adb -s <serial> logcat -s RelayLauncherOverride:I
```

Interpretation:

- **Component disable** or **Package-level override** means the disable step
  and final Home resolver check both succeeded for the recorded operation.
- **Home-intent priority** means Relay is currently selected by the resolver,
  but no verified disable step is recorded. This includes normal Android Home
  selection.
- **Not active** means the current resolver does not select Relay, regardless
  of an earlier successful attempt.
- A command with `success` is not proof that Home changed. Look for the later
  `verification` event with `success` and an observed Relay component.
- Stock-launcher recovery is verified only after its own verification event
  reports success.

The last process crash is a separate, synchronous, bounded record. It stores
exception, thread, stack trace, device, and OS/build context locally so an
intermittent crash can be copied from Settings after restart. It does not
store provider tokens, URLs, or user media and is not uploaded.

## Reversible fallback

Use the in-app **Reversible ADB fallback** only when the TV ignores the
verified Shizuku path. Relay displays the exact remembered stock package
command when available:

```bash
adb shell pm disable-user --user 0 <stock-launcher-package>
adb shell pm enable --user 0 <stock-launcher-package>
```

The first command disables the stock package; the second restores it. Confirm
the stock package before running either command, and keep the restore command
available. Relay does not execute arbitrary ADB commands.

## OEM/firmware issue template

Copy this template into an issue. Remove account tokens, provider data, and any
other private information before posting.

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
1. Open Relay Settings > Device Settings.
2. Authorize Relay in Shizuku.
3. Choose Advanced Mode and apply the Relay Home override.
4. Reboot or press Home, as applicable.

### Observed result
- Home resolver after the attempt:
- Active mode shown in Device Settings:
- Diagnostics Why text:
- Does the stock launcher reclaim Home after reboot? (yes/no)
- Does Accessibility auto-start change the result? (yes/no/not tested)

### Local evidence
Paste the Override diagnostics event list and, if available, filtered output from:

adb logcat -d -s RelayLauncherOverride:I

Do not include provider tokens, account credentials, or unrelated personal data.

### Recovery check
- Was stock launcher restore requested? (yes/no)
- Did diagnostics verify stock launcher as Home? (yes/no)
- Any output/error text from the device:
```
