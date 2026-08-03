CLASS zcl_test_mobile_poc DEFINITION PUBLIC FINAL CREATE PUBLIC.

  PUBLIC SECTION.
    INTERFACES z2ui5_if_app.

ENDCLASS.


CLASS zcl_test_mobile_poc IMPLEMENTATION.

  METHOD z2ui5_if_app~main.

    " Phase-0 demo for the native shell bridge (see /PLAN.md).
    "
    " The shell injects window.abap2ui5Native (contract v0, /bridge/README.md).
    " This app feature-detects it and degrades to a MessageToast in a plain
    " browser. The html:script + follow_up_action mechanism used here is the
    " documented interim pattern; Phase 3 replaces it with a proper custom
    " control that fires the scan result back as a regular backend event.

    IF client->check_on_init( ).

      DATA(view) = z2ui5_cl_xml_view=>factory( ).

      view->_generic( name = `script` ns = `html`
        )->_cc_plain_xml(
          |function a2u5BridgeInfo() \{| &&
          |  if (!window.abap2ui5Native) \{ sap.m.MessageToast.show("Not running in the native shell"); return; \}| &&
          |  window.abap2ui5Native.getDeviceInfo().then(function (i) \{| &&
          |    sap.m.MessageToast.show(i.platform + " " + i.osVersion + " / " + i.model);| &&
          |  \});| &&
          |\}| &&
          |function a2u5BridgeToast() \{| &&
          |  if (!window.abap2ui5Native) \{ sap.m.MessageToast.show("Not running in the native shell"); return; \}| &&
          |  window.abap2ui5Native.showToast("Hello from ABAP");| &&
          |\}| &&
          |function a2u5BridgeScan() \{| &&
          |  if (!window.abap2ui5Native) \{ sap.m.MessageToast.show("Not running in the native shell"); return; \}| &&
          |  window.abap2ui5Native.scanBarcode().then(function (value) \{| &&
          |    sap.m.MessageToast.show("Scanned: " + value);| &&
          |  \}).catch(function (e) \{| &&
          |    sap.m.MessageToast.show("Scan failed: " + e.message);| &&
          |  \});| &&
          |\}| ).

      view->shell( )->page( `abap2UI5 - Native Shell PoC`
        )->button( text = `Device Info`   press = client->_event( `INFO` )
        )->button( text = `Native Toast`  press = client->_event( `TOAST` )
        )->button( text = `Scan Barcode`  press = client->_event( `SCAN` ) ).

      client->view_display( view->stringify( ) ).
      RETURN.

    ENDIF.

    CASE client->get( )-event.
      WHEN `INFO`.
        client->follow_up_action( `a2u5BridgeInfo()` ).
      WHEN `TOAST`.
        client->follow_up_action( `a2u5BridgeToast()` ).
      WHEN `SCAN`.
        client->follow_up_action( `a2u5BridgeScan()` ).
    ENDCASE.

  ENDMETHOD.

ENDCLASS.
