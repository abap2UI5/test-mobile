# Device verification runbook

CI proves that both shells build and that the bridge shim keeps its
contract. Everything below needs real hardware or an emulator and is what
turns the PoC from "compiles" into "works" — the open A2/A3 tasks in
[NEXT_STEPS.md](NEXT_STEPS.md).

None of it needs an SAP, Google or Apple account. What it does need is a
reachable abap2UI5 endpoint and `abap/zcl_test_mobile_poc.clas.abap`
installed on that system.

## 1. Bridge smoke test (Phase 0 exit criterion)

Start the shell, enter the endpoint, run `zcl_test_mobile_poc` and press
each button:

| Check | Android | iOS |
|-------|---------|-----|
| `getDeviceInfo` | manufacturer/model/OS/app version appear | model/OS/app version appear |
| `showToast` | toast at the bottom | overlay label fades in and out |
| `scanBarcode` | ZXing scanner opens, value returns | VisionKit scanner opens, value returns — **physical device only**, DataScanner does not run in the simulator |
| `scanBarcode`, cancelled | promise rejects with `cancelled`, app stays usable | same |
| `biometricConfirm` | system prompt, `true` on success, `false` on cancel | same |
| `getPushToken` | rejects with `unavailable` without `google-services.json` | rejects with `unavailable` on the simulator |

Also confirm the negative case: open the same abap2UI5 app in a desktop
browser. `window.abap2ui5Native` must be undefined and the app must degrade
instead of erroring.

**Phase 0 is done when a barcode scanned in the Android shell arrives in the
ABAP app against a real backend.**

## 2. Session handling (PLAN.md risk 3)

`BACKGROUND_RELOAD_MS` / `backgroundReloadSeconds` is 30 minutes in both
shells. Lower it temporarily (e.g. 30 seconds) to test in one sitting:

1. Open an app, background the shell longer than the interval, return.
2. The SPA must reload rather than resume a dead draft session.
3. Repeat after the server session really expired — the reload must run the
   backend's auth redirect and land on a usable app, not on a login page
   inside a broken frame.

Restore the constant afterwards.

## 3. QR onboarding

Generate a code from either payload shape (any QR generator, e.g.
`qrencode -o onboard.png '<payload>'`):

```
https://host/sap/bc/z2ui5?sap-client=100
```

```json
{"url":"https://host/sap/bc/z2ui5?sap-client=100",
 "msHost":"example.hana.ondemand.com","msAppId":"com.example.app"}
```

Scan it via the *Scan QR* menu entry. The endpoint must be stored (survives
a restart), and with the JSON payload the Mobile Services coordinates must
land in the preferences for the later push registration.

Negative cases — the shell must reject these with the "not a valid
onboarding payload" toast and keep running: a Wi-Fi QR code, a truncated
JSON object, JSON without `url`. (These shapes are also covered by the JVM
unit tests in `android/app/src/test/`.)

## 4. Managed configuration (no EMM needed)

**Android** — with [TestDPC](https://play.google.com/store/apps/details?id=com.afwsamples.testdpc):

1. Install TestDPC and set it up as device owner (fresh device/emulator) or
   in a work profile.
2. TestDPC → *Manage app restrictions* → pick `org.abap2ui5.mobileshell`.
3. Set `endpoint_url`, `app_lock_enabled`, `screenshot_protection`.
4. Restart the shell and verify:
   * managed `endpoint_url` → the *Set endpoint* and *Scan QR* menu entries
     are gone and the pushed URL is loaded;
   * managed `app_lock_enabled=true` → the app-lock toggle is gone, the lock
     prompt appears at start, and on a device **without** an enrolled
     credential the shell refuses to start with the administrator message;
   * managed `screenshot_protection=true` → screenshots fail and the recents
     thumbnail is blank.
5. Clear the restrictions again: every setting must return to user control —
   an unset key is not the same as a managed `false`.

**iOS** — the shell reads the standard managed-app dictionary, so a
simulator can be fed the same input MDM would push:

```sh
xcrun simctl spawn booted defaults write org.abap2ui5.Abap2UI5Shell \
  com.apple.configuration.managed -dict \
  endpoint_url -string "https://host/sap/bc/z2ui5?sap-client=100" \
  app_lock_enabled -bool YES \
  screenshot_protection -bool YES
```

Relaunch the app and check the same three effects. `screenshot_protection`
on iOS only covers the content when the app leaves the foreground (check the
app switcher) — it cannot block a screenshot; see
[DISTRIBUTION.md](DISTRIBUTION.md).

Remove it again with:

```sh
xcrun simctl spawn booted defaults delete org.abap2ui5.Abap2UI5Shell \
  com.apple.configuration.managed
```

## 5. TLS policy

* An `http://` endpoint must be refused with the cleartext message instead
  of a blank WebView (Android `network_security_config`, iOS ATS).
* A debug build must still work behind an inspecting proxy with a
  user-installed CA (Android `debug-overrides`); a release build must not.

## 6. WebView floor (PLAN.md risk 2)

`adb logcat -s WebViewVersion` prints provider and version at every start.
On a device whose WebView is below `WebViewVersion.MINIMUM_MAJOR` the
warning toast must appear — an old System WebView renders the UI5 SPA
half-broken rather than failing loudly, which is exactly the support case
this check exists to short-circuit.

## 7. CSP verification (still open)

Both shells inject the shim through native evaluate APIs
(`evaluateJavascript` / `WKUserScript`), which are not subject to the page's
CSP. Confirm this against a backend with the hardened UI5 CSP settings
switched on, on both platforms, before relying on it in production. This is
the one Phase-4 security item that cannot be settled without a real system.
