# Distribution & hardening (Phase 4)

How the shell reaches devices and what to lock down before production.

## Endpoint provisioning

Precedence in both shells: **managed configuration > stored preference >
manual entry/QR**. For managed fleets no user ever types a URL:

* **Android (managed Google Play / Intune / Workspace ONE):** the EMM reads
  the schema from `res/xml/app_restrictions.xml` and offers `endpoint_url`
  in its app-config UI.
* **iOS (MDM managed app configuration):** push a config dictionary; the
  shell reads `com.apple.configuration.managed` → `endpoint_url`.
* **Unmanaged devices:** QR onboarding — print/display a QR with either the
  plain URL or the JSON payload (`{"url": ..., "msHost": ..., "msAppId": ...}`,
  see `android/.../Onboarding.kt`).

## Store vs. enterprise distribution

The shell is a generic client (one URL, no customer code), so both models work:

* **Enterprise-only (recommended start):** distribute via MDM (managed Play
  private track / Apple Business Manager custom app). No public store review,
  endpoint provisioned by the EMM.
* **Public store:** possible later as an official "abap2UI5 client" — needs
  a demo endpoint for review and a hosted privacy policy.

## Push prerequisites

* Android: Firebase project + `google-services.json` in `android/app/`
  (build activates FCM automatically), server key registered in the Mobile
  Services push settings.
* iOS: APNs key/certificate in Mobile Services, `aps-environment`
  entitlement + signed build.
* Backend: service key of the Mobile Services push API; see
  `abap/zcl_test_mobile_push.clas.abap`.

## Security checklist before production

- [ ] **TLS only**, no cleartext exceptions (neither manifest nor ATS).
- [ ] **Certificate pinning** decision: pin the ABAP/Mobile Services host in
      both shells (Android `network_security_config`, iOS `URLSession`/WKWebView
      challenge handler) — or document why not (proxy/inspection setups).
- [ ] **CSP:** the shells inject the shim via the native evaluate APIs, which
      are not subject to the page's CSP — verify against the hardened UI5 CSP
      settings on both platforms before relying on it.
- [ ] **App lock** enforced via managed config for regulated scenarios
      (currently a user toggle; wire `app_lock_enabled` into the restrictions
      schema when required).
- [ ] **WebView floor:** define the minimum Android System WebView version in
      the EMM (feature drift on locked-down devices, PLAN.md risk 2).
- [ ] **Screenshot/recents protection** (FLAG_SECURE / iOS blur) if the apps
      show sensitive data.
- [ ] **No secrets in the shell:** the shell holds no client secrets; auth
      stays in the WebView session or (Phase 1+) in the BTP SDK secure store.

## Observability

Mobile Services offers client log upload and usage analytics once the SDK
is onboarded (Phase 1+). Until then: Android `Log` + Play console vitals,
iOS `os_log` + Xcode Organizer.
