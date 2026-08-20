/*
 * Contract tests for the shim (bridge/native-bridge.js).
 *
 * The shim is the one piece of the bridge that ships identically to both
 * shells and runs inside customer apps, so its promise plumbing is checked
 * here rather than on a device: the shells are mocked, only the shim is real.
 *
 * Run: node --test bridge/test/native-bridge.test.js
 */

const test = require("node:test");
const assert = require("node:assert");
const fs = require("node:fs");
const path = require("node:path");

const SHIM = fs.readFileSync(
  path.join(__dirname, "..", "native-bridge.js"),
  "utf8");

/**
 * Installs the shim over a fake window.
 * `transport` is "android", "ios" or "browser" (no native side at all).
 */
function install(transport) {
  const calls = [];
  const window = {};

  if (transport === "android") {
    window.__a2u5Android = {
      platformInfo: () => {
        calls.push({ method: "platformInfo" });
        return JSON.stringify({
          platform: "android",
          model: "Google Pixel 8",
          osVersion: "14",
          appVersion: "0.1.0",
        });
      },
      showToast: (text) => calls.push({ method: "showToast", text }),
      scanBarcode: (id) => calls.push({ method: "scanBarcode", id }),
      getPushToken: (id) => calls.push({ method: "getPushToken", id }),
      biometricConfirm: (id, reason) =>
        calls.push({ method: "biometricConfirm", id, reason }),
    };
  } else if (transport === "ios") {
    window.webkit = {
      messageHandlers: {
        a2u5Bridge: { postMessage: (msg) => calls.push(msg) },
      },
    };
  }

  const previousWindow = global.window;
  global.window = window;
  try {
    // eslint-disable-next-line no-eval
    (0, eval)(SHIM);
  } finally {
    global.window = previousWindow;
  }
  return { window, bridge: window.abap2ui5Native, calls };
}

test("plain browser gets no bridge object at all", () => {
  const { bridge } = install("browser");
  assert.strictEqual(bridge, undefined,
    "apps feature-detect on window.abap2ui5Native — it must stay undefined");
});

test("a second injection does not replace the live bridge", () => {
  const { window } = install("android");
  const first = window.abap2ui5Native;
  const previousWindow = global.window;
  global.window = window;
  try {
    (0, eval)(SHIM); // Android injects on every onPageFinished
  } finally {
    global.window = previousWindow;
  }
  assert.strictEqual(window.abap2ui5Native, first,
    "re-injection would orphan promises pending on the previous instance");
});

for (const transport of ["android", "ios"]) {
  test(`${transport}: advertises contract v1`, () => {
    const { bridge } = install(transport);
    assert.strictEqual(bridge.available, true);
    assert.strictEqual(bridge.version, 1);
    assert.strictEqual(bridge.platform, transport);
    for (const method of ["getDeviceInfo", "showToast", "scanBarcode",
      "getPushToken", "biometricConfirm"]) {
      assert.strictEqual(typeof bridge[method], "function", `${method} missing`);
    }
  });

  test(`${transport}: a resolved call settles with the native value`, async () => {
    const { window, bridge, calls } = install(transport);
    const promise = bridge.scanBarcode();
    window.__a2u5BridgeResolve(calls[0].id, "4006381333931", null);
    assert.strictEqual(await promise, "4006381333931");
  });

  test(`${transport}: an error settles the call as a rejection`, async () => {
    const { window, bridge, calls } = install(transport);
    const promise = bridge.scanBarcode();
    window.__a2u5BridgeResolve(calls[0].id, null, "cancelled");
    await assert.rejects(promise, /cancelled/);
  });

  test(`${transport}: concurrent calls settle independently`, async () => {
    const { window, bridge, calls } = install(transport);
    const scan = bridge.scanBarcode();
    const token = bridge.getPushToken();
    assert.notStrictEqual(calls[0].id, calls[1].id, "ids must be unique");
    // Settle out of order — the shim must not rely on call sequence.
    window.__a2u5BridgeResolve(calls[1].id, "fcm-token", null);
    window.__a2u5BridgeResolve(calls[0].id, "scanned", null);
    assert.strictEqual(await token, "fcm-token");
    assert.strictEqual(await scan, "scanned");
  });

  test(`${transport}: settling an unknown id is ignored`, () => {
    const { window } = install(transport);
    assert.doesNotThrow(() => window.__a2u5BridgeResolve("999", "x", null));
  });

  test(`${transport}: a call settles only once`, async () => {
    const { window, bridge, calls } = install(transport);
    const promise = bridge.scanBarcode();
    window.__a2u5BridgeResolve(calls[0].id, "first", null);
    window.__a2u5BridgeResolve(calls[0].id, null, "late error");
    assert.strictEqual(await promise, "first");
  });

  test(`${transport}: biometricConfirm passes the reason through`, async () => {
    const { window, bridge, calls } = install(transport);
    const promise = bridge.biometricConfirm("Approve the posting");
    assert.strictEqual(calls[0].reason, "Approve the posting");
    window.__a2u5BridgeResolve(calls[0].id, true, null);
    assert.strictEqual(await promise, true);
  });
}

test("android: getDeviceInfo resolves from the synchronous interface", async () => {
  const { bridge } = install("android");
  assert.deepStrictEqual(await bridge.getDeviceInfo(), {
    platform: "android",
    model: "Google Pixel 8",
    osVersion: "14",
    appVersion: "0.1.0",
  });
});

test("android: showToast resolves without a native callback", async () => {
  const { bridge, calls } = install("android");
  await bridge.showToast("saved");
  assert.deepStrictEqual(calls[0], { method: "showToast", text: "saved" });
});

test("ios: getDeviceInfo and showToast go through the message handler", async () => {
  const { window, bridge, calls } = install("ios");
  const info = bridge.getDeviceInfo();
  assert.strictEqual(calls[0].method, "getDeviceInfo");
  window.__a2u5BridgeResolve(calls[0].id, { platform: "ios" }, null);
  assert.deepStrictEqual(await info, { platform: "ios" });

  const toast = bridge.showToast("saved");
  assert.strictEqual(calls[1].method, "showToast");
  assert.strictEqual(calls[1].text, "saved");
  window.__a2u5BridgeResolve(calls[1].id, null, null);
  await toast;
});

test("a throwing transport rejects instead of hanging the promise", async () => {
  // The WebView can be torn down while a call is in flight; the shim must
  // not leave the app waiting on a promise nothing will ever settle.
  const { window, bridge } = install("android");
  window.__a2u5Android.scanBarcode = () => {
    throw new Error("interface gone");
  };
  await assert.rejects(bridge.scanBarcode(), /interface gone/);
});
