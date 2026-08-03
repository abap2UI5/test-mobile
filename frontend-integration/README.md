# Frontend integration (Phase 3)

Ready-to-move artifacts for turning the native bridge into a first-class
abap2UI5 feature. Once the shell approach is accepted, these graduate into
the framework repos — they live here only while the PoC is self-contained.

## 1. Custom control → frontend repo

Copy [`NativeBridgeScan.js`](NativeBridgeScan.js) to
`app/webapp/cc/NativeBridgeScan.js` in
[abap2UI5/frontend](https://github.com/abap2UI5/frontend) (pattern:
`CameraPicture.js`).

The control renders a normal UI5 button, calls the shell bridge's
`scanBarcode()` and fires the result as a regular control event — apps get
the value as an ordinary backend event with arguments, no `html:script`
workaround anymore. Outside the native shell it hides itself
(`showInBrowser=false`) so views work unchanged in the browser.

## 2. View builder method → abap2UI5 repo

Add to `z2ui5_cl_xml_view_cc` (see
[`z2ui5_cl_xml_view_cc.native_bridge_scan.snippet.abap`](z2ui5_cl_xml_view_cc.native_bridge_scan.snippet.abap)):

```abap
DATA(view) = z2ui5_cl_xml_view=>factory( ).
view->shell( )->page( `Scan Demo`
  )->_z2ui5( )->native_bridge_scan(
      text    = `Scan Material`
      value   = client->_bind_edit( mv_scanned )
      onscan  = client->_event( t_arg = VALUE #( ( `${$parameters>/value}` ) ) val = `SCANNED` )
      onerror = client->_event( t_arg = VALUE #( ( `${$parameters>/message}` ) ) val = `SCAN_ERROR` ) ).
```

## 3. Bridge contract

The control depends only on `window.abap2ui5Native` (contract v1,
[/bridge/README.md](../bridge/README.md)) — never on shell internals.
Further controls (push-token display, biometric confirm before save) follow
the same pattern.
