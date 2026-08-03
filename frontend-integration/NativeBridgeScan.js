sap.ui.define(["sap/ui/core/Control", "sap/m/Button"], (Control, Button) => {
  "use strict";
  // Native-shell scan button (Phase 3): renders a regular UI5 button that
  // triggers the shell bridge's scanBarcode() and hands the result to the
  // backend as a normal control event — no hand-written JS in apps.
  //
  // Destination: frontend repo, app/webapp/cc/NativeBridgeScan.js (pattern:
  // CameraPicture.js). Outside the native shell the button hides itself
  // unless showInBrowser=true (then it fires OnError instead of scanning),
  // so apps degrade gracefully per the bridge contract.
  return Control.extend("z2ui5.cc.NativeBridgeScan", {
    metadata: {
      properties: {
        text: { type: "string", defaultValue: "Scan" },
        icon: { type: "string", defaultValue: "sap-icon://bar-code" },
        value: { type: "string" },
        showInBrowser: { type: "boolean", defaultValue: false },
      },
      events: {
        OnScan: {
          allowPreventDefault: true,
          parameters: { value: { type: "string" } },
        },
        OnError: {
          allowPreventDefault: true,
          parameters: { message: { type: "string" } },
        },
      },
    },

    init() {
      this._btn = new Button({ press: () => this._scan() });
      this._btn.setParent(this);
    },

    exit() {
      this._btn.destroy();
    },

    _scan() {
      if (!window.abap2ui5Native) {
        this.fireOnError({ message: "native shell not available" });
        return;
      }
      window.abap2ui5Native.scanBarcode().then(
        (value) => {
          this.setProperty("value", value, true);
          this.fireOnScan({ value });
        },
        (e) => this.fireOnError({ message: e.message })
      );
    },

    renderer(rm, ctrl) {
      if (!window.abap2ui5Native && !ctrl.getShowInBrowser()) return;
      ctrl._btn.setText(ctrl.getText());
      ctrl._btn.setIcon(ctrl.getIcon());
      rm.renderControl(ctrl._btn);
    },
  });
});
