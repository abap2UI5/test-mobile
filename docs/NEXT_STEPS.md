# Next steps — detailed work plan

Status assessment as of 2026-08-20. Complements [PLAN.md](../PLAN.md): the
plan describes the phases, this file lists **what is actually still open**,
in execution order, with dependencies and acceptance criteria.

## Where the PoC stands

* Phases 0–4 are implemented in PoC scope (see status table in PLAN.md).
* No open GitHub issues or pull requests.
* CI builds both shells and tests the bridge contract. The iOS build is
  green **for the first time** — before task A1 it never reached the
  compiler at all.
* Everything achievable without an SAP/Google/Apple account has been done —
  see *Recently completed*. What is left needs real devices, external
  accounts, or a maintainer decision.
* The Phase-3 artifacts (`NativeBridgeScan`, view-builder snippet) are still
  staged in [`frontend-integration/`](../frontend-integration/) and have
  **not** been moved into the framework repos yet.
* One open code TODO: `android/.../MobileServicesPush.kt` — the push
  registration call needs the Phase-1 Mobile Services session (returns 401
  unauthenticated until then).

### Recently completed

| Task | Outcome |
|------|---------|
| A1 | `build_ios` green. Two causes, not one: XcodeGen writes Xcode project format 77, which the Xcode 15.4 of the `macos-14` image cannot read (→ `macos-15`), and once the compiler was reachable, `BarcodeScanner.swift` failed on the main-actor isolation of `DataScannerViewController`. |
| E1 (partly) | TLS policy enforced on both platforms: Android `network_security_config` (no cleartext, system trust anchors, user CAs in debug builds only) with a commented pinning template, iOS `NSAllowsArbitraryLoads: false`. An `http://` endpoint is refused with an explanation instead of a blank WebView. The **pinning decision itself is still open** (E1 below). |
| E3 | `app_lock_enabled` manageable by EMM on both platforms; the user toggle disappears and an enforced lock fails closed where no credential is enrolled. |
| E4 | The Android shell logs the WebView provider/version at start and warns below a documented floor (`WebViewVersion.MINIMUM_MAJOR`). |
| E5 | `screenshot_protection` manageable by EMM: Android `FLAG_SECURE`, iOS content cover while not in the foreground (documented limitation — iOS cannot block screenshots). |
| A2/A3 (prep) | [`TESTING.md`](TESTING.md) — device runbook for bridge smoke test, session expiry, QR onboarding, managed config (TestDPC / `simctl` recipes, no EMM needed), TLS policy, WebView floor. Executing it still needs hardware. |
| Test layer | Bridge shim contract tests (both transports, out-of-order and duplicate settling, teardown mid-call, double injection) on Node with the shells mocked, plus JVM unit tests for QR payload and version parsing. Both gated in CI. |

The remaining work falls into five blocks. Block A needs no external
accounts and can start immediately; B–E have external prerequisites or a
decision gate.

---

## Block A — immediate, no external accounts (unblocks everything else)

### A1. Fix the red iOS CI build — **done**

The red build had two independent causes, the second only visible once the
first was out of the way:

1. Unpinned `brew install xcodegen` writes Xcode project format 77, which
   the Xcode 15.4 of the `macos-14` image cannot open
   (*"in a future Xcode project file format (77)"*). Fixed by moving to
   `macos-15`; the workflow now echoes the `xcodebuild`/`xcodegen` versions
   so the same drift is diagnosable from the log alone if the runner image
   ever falls behind XcodeGen again.
2. With the project readable, `xcodebuild` reached the compiler for the
   first time and rejected `BarcodeScanner.swift`:
   `DataScannerViewController` is `@MainActor`, so reading
   `isSupported`/`isAvailable` from the nonisolated enum is an error. Fixed
   by isolating the enum to the main actor — every caller is a UI path
   already delivered on the main thread.

Only ever fixing the first would have replaced a red build with a red
build; that the second existed at all is what the "never compiled outside
CI" note in PLAN.md was warning about.

### A2. First real build + on-device smoke test (Phase 0 exit criterion)

CI now proves that both shells compile. Nobody has yet run either against a
real backend — follow [`TESTING.md`](TESTING.md), which lists the checks
per platform.

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

Both were built account-free but never exercised end to end. The steps are
now written down — sections 3 and 4 of [`TESTING.md`](TESTING.md), including
how to feed managed config without an EMM (TestDPC on Android, `simctl
defaults` on iOS) — so this task is running them, not designing them.

The QR payload shapes are covered by JVM unit tests, the managed-config
precedence and the enforced app lock are not: those need a device.

**Acceptance:** the runbook executed on both platforms, with corrections
filed where behavior deviates from it.

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

E3–E5 are implemented (see *Recently completed*); what remains are the
decisions and the real-device/rollout work. E1–E2 before any pilot with
real data.

* E1. **Certificate pinning decision.** The groundwork is in place — TLS is
  enforced on both platforms and a `domain-config` template with pin-set
  placeholders sits in `network_security_config.xml`; iOS additionally needs
  a WKWebView challenge handler if the answer is yes. What is missing is the
  decision itself, which is a landscape question, not a coding one: pinning
  breaks TLS-inspecting proxies and turns certificate rotation into an app
  release. Decide, then either fill in the pins (with a backup pin and a
  meetable expiration) or record why not.
* E2. **CSP verification**: confirm the natively-injected shim keeps working
  under hardened UI5 CSP settings on both platforms — needs a real backend
  ([`TESTING.md`](TESTING.md) §7).
* E4a. Pin the WebView floor in the EMM's compliance rules to the same
  number the shell warns at, and re-check `WebViewVersion.MINIMUM_MAJOR`
  against the UI5 version the backend actually serves.
* E6. Observability: enable MS client log upload + usage analytics (needs B).
* E7. Distribution execution per `docs/DISTRIBUTION.md`: EMM rollout
  (managed Play private track / ABM custom app); public-store option later
  needs a demo endpoint + hosted privacy policy.

**Acceptance:** security checklist in `docs/DISTRIBUTION.md` fully checked
or consciously waived per item.

---

## Suggested sequence

```
A1 ✔ ──► A2 ──► A3 ──► D0 (decision) ──► D1–D3 ──► D4 (as needed)
                │
                └──► B1 ──► B2 ──► B3 ──► B4/B5 ──► C1–C4 ──► E6
E1, E2, E4a, E7 in parallel once A2 provides real devices
```

Rule of thumb: what could be done in this repo without accounts or hardware
is done; A2/A3 and E1/E2 now need devices, B and C accounts and credentials,
D a maintainer decision, E7 the first real rollout.
