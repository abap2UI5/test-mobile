# abap2UI5 Mobile Shell — PoC Plan

Bring abap2UI5 apps to iOS and Android as a **native shell app** built on the
SAP mobile stack (SAP Mobile Services + SAP BTP SDK for iOS/Android), with the
UI rendered by the existing abap2UI5 frontend inside a WebView.

This is "Stage 2" of the abap2UI5 mobile strategy:

| Stage | Approach | Status |
|-------|----------|--------|
| 1 | SAP Mobile Start (Work Zone tiles on the phone) | available today, [documented](https://abap2ui5.github.io/docs/configuration/mobile_start.html) |
| **2** | **Generic native shell + WebView + native bridge** | **this PoC** |
| 3 | MDK / native rendering | not pursued (metadata mismatch, offline-first design) |

## Scope decision: online-only

abap2UI5 is a server-roundtrip architecture — every event loads the session
draft, runs `main`, and returns view/model JSON. The offline features of the
SAP mobile stack (Offline OData, sync) are structurally incompatible with
this. The shell is **online-only by design**; this is a documented product
decision, not a gap to close later.

## Architecture

```
┌─────────────────────────── device ───────────────────────────┐
│  Native shell app (iOS: Swift/WKWebView, Android: Kotlin)    │
│                                                              │
│  ┌ onboarding ─────────────┐   ┌ WebView ──────────────────┐ │
│  │ SAP BTP SDK flows:      │   │ abap2UI5 SPA (UI5 shell)  │ │
│  │ QR onboard, OAuth/SSO,  │──▶│ loaded from ABAP backend  │ │
│  │ passcode, biometrics    │   │                           │ │
│  └─────────────────────────┘   │  window.abap2ui5Native ◀──┼─┼── JS bridge
│  ┌ native services ────────┐   └────────────┬──────────────┘ │
│  │ barcode scan, toast,    │◀───────────────┘                │
│  │ device info, push token │      (promise-based calls)      │
│  └─────────────────────────┘                                 │
└───────────────────────────────┬──────────────────────────────┘
                                │ HTTPS (JSON roundtrips)
                 ┌──────────────▼──────────────┐
                 │ SAP Mobile Services (BTP)   │  auth, push, analytics,
                 │  └─ destination ────────┐   │  app lifecycle
                 └───────────────────────── │ ─┘
                                ┌──────────▼─────────┐
                                │ ABAP backend       │
                                │ abap2UI5 framework │
                                │ + your apps        │
                                └────────────────────┘
```

Key properties:

* **One generic client for all abap2UI5 apps.** The shell knows one URL; all
  UI logic stays in ABAP. App updates never require an app-store release.
* **Trivial embedding.** abap2UI5 is a single-URL SPA with all state held
  server-side in the session draft — there is no client state to persist,
  restore, or migrate in the shell.
* **Native bridge as progressive enhancement.** ABAP apps feature-detect
  `window.abap2ui5Native` and fall back to browser APIs (or hide the button)
  when running in a plain browser.

## Repository layout

```
PLAN.md                 this plan
bridge/                 bridge contract (source of truth for the JS shim)
  native-bridge.js      shared shim injected by both shells
  README.md             contract documentation
android/                Android shell (Kotlin, Gradle) — full PoC path
ios/                    iOS shell (SwiftUI + WKWebView, XcodeGen)
abap/                   sample abap2UI5 app exercising the bridge
```

## Bridge contract (v0)

Both shells inject `bridge/native-bridge.js` after page load. It exposes:

```js
window.abap2ui5Native = {
  available: true,
  platform: "android" | "ios",
  getDeviceInfo(): Promise<{platform, model, osVersion, appVersion}>,
  showToast(text): Promise<void>,
  scanBarcode(): Promise<string>,   // resolves with the scanned value
}
```

All methods are promise-based so ABAP-side JS is identical on both platforms.
The transport differs per platform (Android `@JavascriptInterface` object,
iOS `webkit.messageHandlers`) and is fully hidden by the shim. See
[bridge/README.md](bridge/README.md) for details and versioning rules.

## Phases

### Phase 0 — plain shells + bridge (this PoC)

No SDK dependency yet, so everything builds with stock tooling:

* Android shell: WebView, endpoint configurable in-app, bridge with
  `getDeviceInfo`, `showToast`, `scanBarcode` (ZXing).
* iOS shell: WKWebView, endpoint configurable, bridge with `getDeviceInfo`
  and `showToast`; `scanBarcode` rejects with `unsupported` (VisionKit
  DataScanner lands in Phase 3).
* Sample ABAP app (`abap/zcl_test_mobile_poc.clas.abap`) that feature-detects
  the bridge and calls it via `follow_up_action`.

**Exit criterion:** scan a barcode from an abap2UI5 app running inside the
Android shell against a real ABAP backend.

### Phase 1 — SAP Mobile Services onboarding (the actual "Mobile SDK" step)

Replace the plain URL entry with the BTP SDK onboarding flow:

* Create a *Mobile Application* in the Mobile Services cockpit with a
  destination pointing at the ABAP system (`/sap/bc/...` or cloud HTTP
  service of abap2UI5).
* Android: `com.sap.cloud.android:onboarding` / foundation flows — QR
  onboarding, OAuth against Mobile Services, passcode/biometric app lock.
* iOS: SAPFoundation `OnboardingFlow` equivalent.
* **Session handover to the WebView is the critical piece:** after
  onboarding, copy the OAuth session into the WebView so the SPA's JSON
  roundtrips are authenticated — Android via `CookieManager` +
  intercepting `WebViewClient.shouldInterceptRequest` (attach
  `Authorization` header), iOS via `WKWebsiteDataStore` cookie injection or
  `WKURLSchemeHandler`. Both SDKs ship helpers for authenticated WebViews;
  evaluate those first before hand-rolling.
* Handle token refresh + session timeout (re-run flow, then reload SPA).

Note: the BTP SDK artifacts are pulled via SAP's repositories/SDK assistant
(SAP account required) — that is why Phase 0 deliberately builds without them.

### Phase 2 — push notifications

The feature that justifies the whole effort:

* Shell registers with FCM/APNs, hands the push token to Mobile Services.
* ABAP backend calls the Mobile Services push REST API
  (`/mobileservices/push/...`) — thin ABAP client class, reusable for all
  abap2UI5 apps.
* Tap on notification → deep link → shell opens the abap2UI5 URL with a
  start parameter (`?app=...`), which abap2UI5 already supports for direct
  app starts.

### Phase 3 — first-class bridge integration in abap2UI5

* New custom control `NativeBridge` in the [frontend repo](https://github.com/abap2UI5/frontend)
  (pattern: `CameraPicture.js`) with proper UI5 events, so the scan result
  arrives in ABAP as a normal `client->_event` with arguments — no
  hand-written JS in apps anymore:

  ```abap
  view->_z2ui5( )->native_bridge(
      scan_result = client->_bind_edit( mv_scanned )
      onscan      = client->_event( `SCANNED` ) ).
  ```
* iOS barcode scanning via VisionKit `DataScannerViewController`.
* Extend the contract as needed (biometric confirm, NFC, share sheet) —
  contract versioned in `bridge/README.md`.

### Phase 4 — hardening & distribution

* MDM/app-config (managed configuration for the endpoint URL — no manual
  entry), Intune/MobileIron rollout, app-store metadata.
* Client log upload + usage analytics via Mobile Services.
* CSP review: the SPA must allow the injected shim (shells inject via the
  native evaluate APIs, which bypass page CSP — verify on both platforms).
* Certificate pinning decision.

## Risks / open questions

| # | Risk | Mitigation |
|---|------|-----------|
| 1 | Auth handover SDK session → WebView (cookies vs. header injection) | Phase 1 spike; both SDKs document authenticated-WebView patterns |
| 2 | WebView feature drift (old Android System WebView on managed devices) | set `minSdk` 26+, document minimum WebView version |
| 3 | Session draft timeout while app is backgrounded | reload SPA on resume after timeout; abap2UI5 handles expired drafts server-side |
| 4 | Users expect offline | explicit scope statement (see above), communicate early |
| 5 | Bridge API sprawl | contract versioned in one file, additions only via PR review |

## Running the PoC

* **Android:** open `android/` in Android Studio (or `./gradlew assembleDebug`
  with Android SDK 35 installed), run, enter your abap2UI5 URL
  (e.g. `https://<host>/sap/bc/z2ui5?sap-client=100`).
* **iOS:** `brew install xcodegen && cd ios && xcodegen generate`, open the
  generated `Abap2UI5Shell.xcodeproj`, run on a device/simulator, enter the URL.
* **ABAP:** install `abap/zcl_test_mobile_poc.clas.abap` (requires abap2UI5),
  start it inside the shell, press the bridge buttons.

Note: this PoC was authored in a container without Android/iOS toolchains —
the projects follow standard templates but have not been compiled here yet.
First local build may need minor version alignment (AGP/Kotlin/Xcode).
