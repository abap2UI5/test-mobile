# Distribution & hardening (Phase 4)

How the shell reaches devices and what to lock down before production.

## Managed configuration

Precedence in both shells: **managed configuration > stored preference >
manual entry/QR**. The key names are identical on both platforms:

| Key | Type | Effect |
|-----|------|--------|
| `endpoint_url` | string | The abap2UI5 URL. Set means no user ever types one — the in-app endpoint/QR entry disappears. |
| `app_lock_enabled` | bool | Forces (or forbids) the biometric/device-credential lock at app start. Set means the user cannot toggle it. |
| `screenshot_protection` | bool | Blocks screenshots and the recents/app-switcher preview. |

A key that is **not** set stays under user control — that is the difference
between "administrator set false" and "not managed", and both shells treat
it that way.

* **Android (managed Google Play / Intune / Workspace ONE):** the EMM reads
  the schema from `res/xml/app_restrictions.xml` and offers the keys in its
  app-config UI; values arrive via `RestrictionsManager`.
* **iOS (MDM managed app configuration):** push a config dictionary; the
  shell reads it from `com.apple.configuration.managed`.
* **Unmanaged devices:** QR onboarding — print/display a QR with either the
  plain URL or the JSON payload (`{"url": ..., "msHost": ..., "msAppId": ...}`,
  see `android/.../Onboarding.kt`).

With `app_lock_enabled` forced on, a device **without** an enrolled
credential is refused rather than let through: an administrator who requires
the lock requires a device credential with it. Without the managed key the
user-chosen lock keeps failing open, so nobody locks themselves out of a
shell they configured themselves.

Note on `screenshot_protection`: Android enforces it (`FLAG_SECURE` — the
screenshot is blocked outright). iOS has no such API; the shell covers its
content whenever the app leaves the foreground, which keeps business data
out of the app-switcher snapshot but cannot stop a deliberate screenshot or
screen recording. Where that matters, the control has to come from MDM
restrictions on the device, not from the app.

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

- [x] **TLS only**, no cleartext exceptions: Android
      `res/xml/network_security_config.xml` (`cleartextTrafficPermitted="false"`,
      system trust anchors only, user CAs in debug builds so proxy inspection
      still works while developing), iOS `NSAllowsArbitraryLoads: false` in
      `project.yml`. An `http://` endpoint is refused with an explanation
      rather than a blank WebView.
- [ ] **Certificate pinning** decision: a template `domain-config` sits ready
      in `network_security_config.xml`; on iOS it needs a WKWebView challenge
      handler. Pinning breaks TLS-inspecting proxies and turns certificate
      rotation into an app release — decide deliberately, and if you pin,
      ship a backup pin and an expiration you can meet.
- [ ] **CSP:** the shells inject the shim via the native evaluate APIs, which
      are not subject to the page's CSP — verify against the hardened UI5 CSP
      settings on both platforms before relying on it. (Real-device task.)
- [x] **App lock** enforceable via managed config (`app_lock_enabled`) for
      regulated scenarios — user toggle disappears, devices without an
      enrolled credential are refused.
- [x] **WebView floor:** the Android shell logs the WebView provider and
      version at start and warns the user below
      `WebViewVersion.MINIMUM_MAJOR` (100 — align it with the browser-support
      statement of the UI5 version your backend bootstraps, abap2UI5 ships
      OpenUI5 1.142 at the time of writing). Pin the same floor in the EMM's
      compliance rules (PLAN.md risk 2).
- [x] **Screenshot/recents protection** available via `screenshot_protection`
      (Android FLAG_SECURE, iOS foreground-only content cover — see the
      caveat above).
- [ ] **No secrets in the shell:** the shell holds no client secrets; auth
      stays in the WebView session or (Phase 1+) in the BTP SDK secure store.
      Re-verify once the BTP SDK onboarding lands.

## Observability

Mobile Services offers client log upload and usage analytics once the SDK
is onboarded (Phase 1+). Until then: Android `Log` + Play console vitals,
iOS `os_log` + Xcode Organizer.
