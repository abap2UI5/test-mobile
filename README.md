# test-mobile

PoC: running abap2UI5 apps on iOS and Android in a **generic native shell**
built for the SAP mobile stack (SAP Mobile Services + SAP BTP SDK), with the
UI rendered by the regular abap2UI5 frontend in a WebView and native device
features exposed through a small JS bridge.

**Start here: [PLAN.md](PLAN.md)** — goals, architecture, phases, risks.

| Directory | Content |
|-----------|---------|
| [`bridge/`](bridge/) | Bridge contract v1 + shared JS shim (source of truth) |
| [`android/`](android/) | Android shell — WebView, ZXing scan, QR onboarding, app lock, FCM push, managed config |
| [`ios/`](ios/) | iOS shell — WKWebView, VisionKit scan, app lock, APNs push, managed config (XcodeGen) |
| [`abap/`](abap/) | Sample app exercising the bridge + Mobile Services push client |
| [`frontend-integration/`](frontend-integration/) | `NativeBridgeScan` custom control, staged for the frontend repo |
| [`docs/`](docs/) | Distribution & hardening guide |

Status: **Phases 0–4 implemented in PoC scope** (see the status table in
PLAN.md). What still needs external accounts: the BTP SDK onboarding flow
(SAP repositories), and push activation (Firebase project, APNs key, Mobile
Services credentials). Online-only by design: abap2UI5 is a server-roundtrip
architecture, offline sync is explicitly out of scope.
