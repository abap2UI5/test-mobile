# Next steps — detailed work plan

Status assessment as of 2026-08-20. Complements [PLAN.md](../PLAN.md): the
plan describes the phases, this file lists **what is actually still open**,
in execution order, with dependencies and acceptance criteria.

## Where the PoC stands

* Phases 0–4 are implemented in PoC scope (see status table in PLAN.md).
* No open GitHub issues or pull requests.
* CI: `build_android` is **green** on `main`; `build_ios` is **red** on
  `main` (see task A1 — tooling mismatch, not a code bug).
* The Phase-3 artifacts (`NativeBridgeScan`, view-builder snippet) are still
  staged in [`frontend-integration/`](../frontend-integration/) and have
  **not** been moved into the framework repos yet.
* One open code TODO: `android/.../MobileServicesPush.kt` — the push
  registration call needs the Phase-1 Mobile Services session (returns 401
  unauthenticated until then).

The remaining work falls into five blocks. Block A needs no external
accounts and can start immediately; B–E have external prerequisites or a
decision gate.

---

## Block A — immediate, no external accounts (unblocks everything else)

### A1. Fix the red iOS CI build

`build_ios` fails on `main`: unpinned `brew install xcodegen` now installs
XcodeGen 2.45.4, which emits Xcode project format 77; the `macos-14` runner
has Xcode 15.4, which cannot read it:

> The project 'Abap2UI5Shell' cannot be opened because it is in a future
> Xcode project file format (77).

Fix options (either works, first is smaller):

1. Keep `macos-14` and force a compatible format — pin XcodeGen to a 2.4x
   version that emits format ≤ 60, or set the project format explicitly in
   `ios/project.yml` (XcodeGen `options.xcodeVersion` /
   `objectVersion`-compatible setting).
2. Move the workflow to `macos-15` and select an Xcode 16+ toolchain.

Also worth doing in the same pass: pin the XcodeGen version in the workflow
regardless of the option chosen, so the build cannot drift again.

**Acceptance:** `build_ios` green on `main`.

### A2. First real build + on-device smoke test (Phase 0 exit criterion)

PLAN.md notes the projects were authored without local toolchains and have
never been compiled outside CI. CI proves the Android build; nobody has yet
run the shells against a real backend.

* Run the Android shell on a device/emulator against a real abap2UI5
  endpoint; execute `zcl_test_mobile_poc`: device info, toast, ZXing scan.
* Same on iOS (simulator for info/toast; **scan needs a real device** —
  VisionKit DataScanner does not run in the simulator).
* Verify the reload-after-background/session-expiry behavior against a real
  session draft timeout (PLAN.md risk 3).
* File whatever version alignment the first build needs (AGP/Kotlin/Xcode)
  as fixes, not workarounds.

**Acceptance:** barcode scanned from an abap2UI5 app inside the Android
shell against a real ABAP backend (the declared PoC exit criterion), and
the same flow demonstrated on an iOS device.

### A3. QR onboarding + managed-config paths verified

Both were built account-free but never exercised end to end:

* QR onboarding with plain-URL payload and with JSON payload
  (`{"url", "msHost", "msAppId"}`).
* Managed config: push `endpoint_url` via a test EMM (or `adb` restrictions
  / Apple Configurator) and confirm the precedence chain
  *managed config > stored preference > manual/QR*.

**Acceptance:** documented walkthrough (screenshots or step list) added to
`docs/`, corrections filed where behavior deviates.

---

## Block B — SAP account required: finish Phase 1 (BTP SDK onboarding)

Prerequisite: SAP BTP account with Mobile Services; SDK artifacts come via
SAP's repositories / SDK assistant. This block carries **risk #1** (auth
handover) and gates push activation (C) — start it before C.

### B1. Mobile Services application + destination

Create the *Mobile Application* in the MS cockpit with a destination to the
ABAP system (`/sap/bc/z2ui5...` or the cloud HTTP service). Record the
config (host, app id) in the repo docs so QR payloads can be generated.

### B2. SDK onboarding flows replacing the plain URL entry

* Android: `com.sap.cloud.android:onboarding` + foundation — QR onboarding,
  OAuth against MS, passcode/biometric integration (replaces/absorbs the
  hand-rolled `Onboarding.kt` + `AppLock.kt` paths where the SDK covers them).
* iOS: SAPFoundation `OnboardingFlow` equivalent.

### B3. Session handover SDK → WebView (the critical spike)

Evaluate the SDKs' authenticated-WebView helpers **first**; only if they
don't fit, hand-roll: Android `CookieManager` + `shouldInterceptRequest`
(attach `Authorization`), iOS `WKWebsiteDataStore` cookie injection or
`WKURLSchemeHandler`.

**Acceptance:** SPA JSON roundtrips are authenticated straight after
onboarding, with no interactive login inside the WebView.

### B4. Token refresh + timeout

Expired token / MS session → re-run flow silently where possible, then
reload the SPA. Combine with the existing background-expiry reload.

### B5. Close the code TODO in `MobileServicesPush.kt`

Attach the MS session to the push runtime registration call (the SDK push
helper covers exactly this). Unblocks C.

---

## Block C — Firebase/Apple credentials required: activate Phase 2 (push)

Code is shipped; this block is configuration + end-to-end verification.
Depends on B (registration call needs the authenticated session).

* C1. Firebase project → `google-services.json` into `android/app/`
  (FCM activates automatically), server key into MS push settings.
* C2. APNs key/cert in MS, `aps-environment` entitlement + signed build
  (needs an Apple Developer team; extend `ios/project.yml` signing).
* C3. MS push API service key for the backend; configure
  `zcl_test_mobile_push`.
* C4. End-to-end test: ABAP → MS → device on both platforms, including
  deep link on notification tap (data key `url`) into the correct app.

**Acceptance:** a push sent from ABAP opens the target abap2UI5 app on both
platforms via deep link.

---

## Block D — decision gate, then framework integration (Phase 3 graduation)

### D0. Decision: adopt the shell approach

The staged artifacts graduate only "once the shell approach is accepted"
(frontend-integration/README.md). Input for the decision: results of A2/A3
(does the PoC hold up on real devices?) and ideally B3 (auth story).
Owner/forum: abap2UI5 maintainers.

After a positive decision:

* D1. PR to **abap2UI5/frontend**: `NativeBridgeScan.js` →
  `app/webapp/cc/NativeBridgeScan.js` (pattern `CameraPicture.js`).
* D2. PR to **abap2UI5/abap2UI5**: `native_bridge_scan` method in
  `z2ui5_cl_xml_view_cc` (snippet is ready in `frontend-integration/`).
* D3. Sample app in the samples repo + docs page (extend the mobile
  documentation beyond `mobile_start.html` with the Stage-2 shell).
* D4. Backlog, additive contract v1+ extensions only as concrete apps need
  them: NFC, share sheet, push-token control, biometric-confirm-before-save
  control. Additions only via PR review (PLAN.md risk 5).

**Acceptance for D1/D2:** a view built with
`native_bridge_scan( ... )` receives the scanned value as a regular
`client->_event` with arguments — no `html:script` workaround — and renders
nothing (or a hidden control) in a plain browser.

---

## Block E — production hardening & distribution (Phase 4 leftovers)

Real-device / rollout tasks, mostly independent; E1–E2 before any pilot
with real data.

* E1. **Certificate pinning decision** and, if pinned, implementation:
  Android `network_security_config`, iOS `URLSession`/WKWebView challenge
  handler — or a documented decision against it (proxy/TLS-inspection
  landscapes).
* E2. **CSP verification**: confirm the natively-injected shim keeps working
  under hardened UI5 CSP settings on both platforms.
* E3. App lock enforceable via managed config: wire `app_lock_enabled` into
  the Android restrictions schema and the iOS managed-config dictionary.
* E4. Define the minimum Android System WebView version for EMM rollouts
  (risk 2) in `docs/DISTRIBUTION.md`.
* E5. Screenshot/recents protection (`FLAG_SECURE` / iOS blur) — decide per
  sensitivity of the target apps.
* E6. Observability: enable MS client log upload + usage analytics (needs B).
* E7. Distribution execution per `docs/DISTRIBUTION.md`: EMM rollout
  (managed Play private track / ABM custom app); public-store option later
  needs a demo endpoint + hosted privacy policy.

**Acceptance:** security checklist in `docs/DISTRIBUTION.md` fully checked
or consciously waived per item.

---

## Suggested sequence

```
A1 ──► A2 ──► A3 ──► D0 (decision) ──► D1–D3 ──► D4 (as needed)
               │
               └──► B1 ──► B2 ──► B3 ──► B4/B5 ──► C1–C4 ──► E6
E1–E5, E7 in parallel once A2 provides real devices
```

Rule of thumb: everything in A is a normal PR against this repo today;
B and C are blocked on accounts/credentials, not on code; D needs a
maintainer decision; E belongs to the first real rollout.
