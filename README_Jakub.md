# Custom features (this fork)

Several per-account features were added on top of upstream Linphone. Both are configured from **Settings → Accounts → (pick account) → Account settings**.

## 1. Restrict account to specific WiFi network(s)

Field: `Restrict to WiFi networks` (comma/semicolon-separated SSID list, near the bottom of the main account settings screen). Leave empty for no restriction.

- When set, an incoming call for that account is auto-declined (SIP 486 Busy) unless the phone is currently connected to one of the listed WiFi SSIDs.
- Requires the `ACCESS_FINE_LOCATION` runtime permission (requested automatically the first time you fill in this field) **and** device-wide Location Services turned on — this is an Android OS requirement to read the current WiFi SSID, not a Linphone limitation. If Location Services is off, the app can't tell WiFi networks apart and lets calls through rather than blocking everything.
- Implementation: `CoreContext.isCallForbiddenByWifiSsidRestriction()` in `core/CoreContext.kt`, storage in `CorePreferences.kt` (`account_wifi_ssid_allow_list` config key, keyed by account SIP identity).

## 2. In-call shortcut button (e.g. trigger a smart-home action mid-call)

Field: `In-call shortcut button`, same account settings screen. Once set, a call for that account shows an extra action icon (bolt/external-link icon) in the ongoing-call bottom action bar — tap it to fire the configured Android `Intent` (e.g. run a Tasker task, open a door, etc.) without leaving the call.

Two ways to configure it:

- **Picker** (tap the field itself): opens Android's `ACTION_CREATE_SHORTCUT` chooser. Works for apps that register a legacy shortcut-creation activity — **Tasker** is the main example. Pick Tasker, configure/pick a Task, done.
- **Manual entry** (expand `Advanced settings` further down the same screen → `Configure shortcut manually`): needed for apps that only expose **modern dynamic/pinned shortcuts** (`ShortcutManager`), which the picker above *cannot* see — reading another app's dynamic/pinned shortcuts requires the `ACCESS_SHORTCUTS` permission, which is `signature|privileged` and unreachable for a normal app like Linphone. This is a hard Android platform restriction, not something fixable from our side.

  To find the raw Intent for a dynamic/pinned shortcut, run `adb shell dumpsys shortcut` on the device and look for the target app's `ShortcutInfo` entry, e.g. its `intents=[Intent { act=... dat=... cmp=... }]` line. Fill the 3 fields (Action / Data URI / Component) in the manual-entry dialog from that.

  Example — real one in use, a Loxone smart-home door-open command (`com.loxone.kerberos` app), works from any Loxone Miniserver on the same setup:
  ```
  Label:     Door
  Action:    android.intent.action.VIEW
  Data URI:  loxonecmd://ms?mac=EEE000780187&loc=control%2F1a60308f-038d-7b59-ffff960575f4d50a&cmd=jdev/sps/io/1a60308f-038d-7b59-ffff960575f4d50a/pulse
  Component: com.loxone.kerberos/.utility.BackgroundCmdActivity
  ```

- Implementation: storage in `CorePreferences.kt` (`account_shortcut_intent_uri` / `account_shortcut_name` config keys, as a serialized `Intent.toUri(URI_INTENT_SCHEME)` string), settings UI in `ui/main/settings/fragment/AccountSettingsFragment.kt` + `ShortcutManualEntryDialogModel.kt`, call-screen button + launch logic in `ui/call/viewmodel/CurrentCallViewModel.kt` (`runShortcut()`), portrait layout `layout/call_actions_bottom_sheet.xml`, landscape/tablet layout `layout-land/call_actions_bottom_sheet.xml`.

## 3. Intercom mode

Switch: `Intercom mode`, in the account's `Advanced settings` section. Meant for door-station / intercom accounts, together with the in-call shortcut button above.

- Calls (incoming and outgoing) on that account never switch to full-screen mode automatically when video starts, so the bottom action panel stays visible. Tapping the video still toggles full screen manually.
- Every time the active-call screen is shown, the bottom action panel is expanded, so the shortcut button is one tap away right after answering.
- Implementation: storage in `CorePreferences.kt` (`account_intercom_mode` config key, keyed by account SIP identity), full-screen suppression in `CurrentCallViewModel.updateVideoDirection()`, panel expansion in `ActiveCallFragment.expandActionsBottomSheetIfIntercomMode()`.

# CONTRIBUTIONS

In order to submit a patch for inclusion in linphone's source code:

1. First make sure your patch applies to latest git sources before submitting: patches made to old versions can't and won't be merged.
2. Fill out and send us an email with the link of pull-request and the [Contributor Agreement](https://linphone.org/sites/default/files/bc-contributor-agreement_0.pdf) for your patch to be included in the git tree.

The goal of this agreement to grant us peaceful exercise of our rights on the linphone source code, while not losing your rights on your contribution.
