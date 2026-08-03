import LocalAuthentication
import SwiftUI
import UIKit
import WebKit

/// WKWebView wrapper with the native side of bridge contract v1.
///
/// The shared shim (Resources/native-bridge.js, copy of /bridge/native-bridge.js)
/// is injected as a WKUserScript; calls arrive via the "a2u5Bridge" message
/// handler and are settled through window.__a2u5BridgeResolve(id, result, error).
struct ShellWebView: UIViewRepresentable {
    let url: URL
    /// Increment to force a reload (session-expiry handling, see RootView).
    var reloadToken: Int = 0

    func makeCoordinator() -> Coordinator { Coordinator() }

    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        let controller = WKUserContentController()

        if let shimURL = Bundle.main.url(forResource: "native-bridge", withExtension: "js"),
           let shim = try? String(contentsOf: shimURL) {
            controller.addUserScript(WKUserScript(
                source: shim,
                injectionTime: .atDocumentEnd,
                forMainFrameOnly: true))
        }
        controller.add(context.coordinator, name: "a2u5Bridge")
        config.userContentController = controller

        let webView = WKWebView(frame: .zero, configuration: config)
        webView.allowsBackForwardNavigationGestures = true
        context.coordinator.webView = webView
        webView.load(URLRequest(url: url))
        return webView
    }

    func updateUIView(_ webView: WKWebView, context: Context) {
        if context.coordinator.lastReloadToken != reloadToken {
            context.coordinator.lastReloadToken = reloadToken
            webView.load(URLRequest(url: url))
            return
        }
        if context.coordinator.lastRequestedURL != url.absoluteString {
            context.coordinator.lastRequestedURL = url.absoluteString
            webView.load(URLRequest(url: url))
        }
    }

    final class Coordinator: NSObject, WKScriptMessageHandler {
        weak var webView: WKWebView?
        var lastReloadToken = 0
        var lastRequestedURL: String?

        func userContentController(_ userContentController: WKUserContentController,
                                   didReceive message: WKScriptMessage) {
            guard message.name == "a2u5Bridge",
                  let body = message.body as? [String: Any],
                  let id = body["id"] as? String,
                  let method = body["method"] as? String else { return }

            switch method {
            case "getDeviceInfo":
                let device = UIDevice.current
                let info: [String: String] = [
                    "platform": "ios",
                    "model": device.model,
                    "osVersion": device.systemVersion,
                    "appVersion": Bundle.main
                        .object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "0",
                ]
                resolve(id: id, resultJSON: jsonString(info), error: nil)

            case "showToast":
                let text = body["text"] as? String ?? ""
                showToast(text)
                resolve(id: id, resultJSON: "null", error: nil)

            case "scanBarcode":
                BarcodeScanner.present { [weak self] value, error in
                    if let value {
                        self?.resolve(id: id, resultJSON: self?.jsonString(value) ?? "null", error: nil)
                    } else {
                        self?.resolve(id: id, resultJSON: "null", error: error ?? "cancelled")
                    }
                }

            case "getPushToken":
                if let token = UserDefaults.standard.string(forKey: "push_token") {
                    resolve(id: id, resultJSON: jsonString(token), error: nil)
                } else {
                    resolve(id: id, resultJSON: "null", error: "unavailable")
                }

            case "biometricConfirm":
                let reason = (body["reason"] as? String).flatMap { $0.isEmpty ? nil : $0 }
                    ?? "Confirm action"
                let context = LAContext()
                var laError: NSError?
                guard context.canEvaluatePolicy(.deviceOwnerAuthentication, error: &laError) else {
                    resolve(id: id, resultJSON: "null", error: "unavailable")
                    return
                }
                context.evaluatePolicy(.deviceOwnerAuthentication,
                                       localizedReason: reason) { [weak self] success, _ in
                    self?.resolve(id: id, resultJSON: success ? "true" : "false", error: nil)
                }

            default:
                resolve(id: id, resultJSON: "null", error: "unknown method \(method)")
            }
        }

        private func resolve(id: String, resultJSON: String, error: String?) {
            let errorJSON = error.map { jsonString($0) } ?? "null"
            let js = "window.__a2u5BridgeResolve(\(jsonString(id)), \(resultJSON), \(errorJSON))"
            DispatchQueue.main.async { [weak self] in
                self?.webView?.evaluateJavaScript(js)
            }
        }

        private func jsonString(_ value: Any) -> String {
            guard JSONSerialization.isValidJSONObject([value]),
                  let data = try? JSONSerialization.data(withJSONObject: [value]),
                  let wrapped = String(data: data, encoding: .utf8) else { return "null" }
            // Strip the wrapping array brackets to get a bare JSON literal.
            return String(wrapped.dropFirst().dropLast())
        }

        /// Minimal toast: transient label overlay on the key window.
        private func showToast(_ text: String) {
            DispatchQueue.main.async {
                guard let window = UIApplication.shared.connectedScenes
                    .compactMap({ ($0 as? UIWindowScene)?.keyWindow }).first else { return }
                let label = UILabel()
                label.text = text
                label.textAlignment = .center
                label.textColor = .white
                label.backgroundColor = UIColor.black.withAlphaComponent(0.75)
                label.font = .preferredFont(forTextStyle: .callout)
                label.numberOfLines = 0
                label.layer.cornerRadius = 12
                label.clipsToBounds = true
                label.alpha = 0
                let maxWidth = window.bounds.width - 48
                let size = label.sizeThatFits(CGSize(width: maxWidth - 32, height: .greatestFiniteMagnitude))
                label.frame = CGRect(
                    x: (window.bounds.width - size.width - 32) / 2,
                    y: window.bounds.height - size.height - 120,
                    width: size.width + 32,
                    height: size.height + 20)
                window.addSubview(label)
                UIView.animate(withDuration: 0.2) { label.alpha = 1 }
                DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
                    UIView.animate(withDuration: 0.3, animations: { label.alpha = 0 }) { _ in
                        label.removeFromSuperview()
                    }
                }
            }
        }
    }
}
