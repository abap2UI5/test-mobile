# Native bridge contract

`native-bridge.js` is the source of truth for the JS shim both shells inject
into the WebView. The shells bundle byte-identical copies:

* `android/app/src/main/assets/native-bridge.js`
* `ios/Resources/native-bridge.js`

Edit here first, then sync the copies.

## Contract v1

```js
window.abap2ui5Native = {
  available: true,
  version: 1,
  platform: "android" | "ios",
  getDeviceInfo(): Promise<{platform, model, osVersion, appVersion}>,
  showToast(text): Promise<void>,
  scanBarcode(): Promise<string>,        // rejects: Error("cancelled") | Error("unsupported")
  getPushToken(): Promise<string>,       // FCM token (Android) / APNs hex (iOS); rejects: Error("unavailable")
  biometricConfirm(reason): Promise<boolean>,  // false = user cancelled/failed; rejects: Error("unavailable")
}
```

v1 adds `getPushToken` and `biometricConfirm` (additive over v0, per the
rules below). Feature-detect individual methods when running against an
older shell: `if (window.abap2ui5Native.getPushToken) ...`.

Rules:

* **Feature-detect, never assume.** In a plain browser `window.abap2ui5Native`
  is undefined; ABAP apps must degrade gracefully.
* **Everything returns a promise**, so ABAP-side JS is platform-independent.
* **Additive changes only** within a major version. Renames/removals bump the
  major version and must keep the old names working for one release.
* Transport (Android `@JavascriptInterface`, iOS `webkit.messageHandlers`) is
  an implementation detail — ABAP apps talk only to `window.abap2ui5Native`.

## Callback mechanism

Async calls register a pending promise under a sequence id; the native side
settles it by evaluating:

```js
window.__a2u5BridgeResolve(id, result /* JS value */, error /* string|null */)
```

`__a2u5BridgeResolve` and `__a2u5Android` are private — never call them from
app code.
