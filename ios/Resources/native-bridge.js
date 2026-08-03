/*
 * abap2UI5 native bridge shim — contract v1.
 *
 * Source of truth for the JS side of the shell bridge. Both shells inject
 * this file into the WebView after page load:
 *   - Android: copied to android/app/src/main/assets/native-bridge.js,
 *     injected via evaluateJavascript in onPageFinished
 *   - iOS: bundled as ios/Resources/native-bridge.js, injected as WKUserScript
 *
 * Keep the three copies identical — edit here first, then sync.
 *
 * abap2UI5 apps feature-detect with `if (window.abap2ui5Native) ...` and must
 * degrade gracefully in a plain browser, where this object does not exist.
 */
(function () {
  "use strict";
  if (window.abap2ui5Native) return;

  var pending = new Map();
  var seq = 0;

  // Called by the native side to settle a pending promise.
  // `result` is a JS value (the native side builds the literal), `error` a
  // message string or null/undefined.
  window.__a2u5BridgeResolve = function (id, result, error) {
    var p = pending.get(id);
    if (!p) return;
    pending.delete(id);
    if (error) p.reject(new Error(error));
    else p.resolve(result);
  };

  function call(invoke) {
    var id = String(++seq);
    return new Promise(function (resolve, reject) {
      pending.set(id, { resolve: resolve, reject: reject });
      try {
        invoke(id);
      } catch (e) {
        pending.delete(id);
        reject(e);
      }
    });
  }

  var android = window.__a2u5Android;
  var ios =
    window.webkit &&
    window.webkit.messageHandlers &&
    window.webkit.messageHandlers.a2u5Bridge;
  if (!android && !ios) return; // plain browser — no bridge

  function iosCall(method, extra) {
    return call(function (id) {
      var msg = { id: id, method: method };
      if (extra) for (var k in extra) msg[k] = extra[k];
      ios.postMessage(msg);
    });
  }

  window.abap2ui5Native = {
    available: true,
    version: 1,
    platform: android ? "android" : "ios",

    // Promise<{platform, model, osVersion, appVersion}>
    getDeviceInfo: function () {
      if (android) return Promise.resolve(JSON.parse(android.platformInfo()));
      return iosCall("getDeviceInfo");
    },

    // Promise<void>
    showToast: function (text) {
      if (android) {
        android.showToast(String(text));
        return Promise.resolve();
      }
      return iosCall("showToast", { text: String(text) });
    },

    // Promise<string> — resolves with the scanned barcode value,
    // rejects with Error("cancelled") or Error("unsupported").
    scanBarcode: function () {
      if (android)
        return call(function (id) {
          android.scanBarcode(id);
        });
      return iosCall("scanBarcode");
    },

    // Promise<string> — the device push token (FCM registration token on
    // Android, APNs token hex on iOS). Rejects with Error("unavailable")
    // when push is not configured on the device/shell.
    getPushToken: function () {
      if (android)
        return call(function (id) {
          android.getPushToken(id);
        });
      return iosCall("getPushToken");
    },

    // Promise<boolean> — true when the user passed the biometric/device
    // credential check, false when they cancelled or failed it.
    // Rejects with Error("unavailable") when no authenticator is enrolled.
    biometricConfirm: function (reason) {
      if (android)
        return call(function (id) {
          android.biometricConfirm(id, String(reason));
        });
      return iosCall("biometricConfirm", { reason: String(reason) });
    },
  };
})();
