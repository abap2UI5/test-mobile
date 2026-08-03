# test-mobile

PoC: running abap2UI5 apps on iOS and Android in a **generic native shell**
built for the SAP mobile stack (SAP Mobile Services + SAP BTP SDK), with the
UI rendered by the regular abap2UI5 frontend in a WebView and native device
features exposed through a small JS bridge.

**Start here: [PLAN.md](PLAN.md)** — goals, architecture, phases, risks.

| Directory | Content |
|-----------|---------|
| [`bridge/`](bridge/) | Bridge contract v0 + shared JS shim (source of truth) |
| [`android/`](android/) | Android shell (Kotlin, WebView, ZXing barcode scan) |
| [`ios/`](ios/) | iOS shell (SwiftUI, WKWebView; project via XcodeGen) |
| [`abap/`](abap/) | Sample abap2UI5 app exercising the bridge |

Status: **Phase 0** — plain shells without SAP BTP SDK dependency (that is
Phase 1, requires SAP repositories). Online-only by design: abap2UI5 is a
server-roundtrip architecture, offline sync is explicitly out of scope.
