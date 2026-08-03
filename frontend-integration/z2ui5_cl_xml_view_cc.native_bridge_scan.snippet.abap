" Phase-3 addition to z2ui5_cl_xml_view_cc in the abap2UI5 repo
" (src/99/z2ui5_cl_xml_view_cc.clas.abap) — pairs with the frontend custom
" control app/webapp/cc/NativeBridgeScan.js (see README.md in this folder).

" --- definition part -------------------------------------------------------
    METHODS native_bridge_scan
      IMPORTING
        id            TYPE clike OPTIONAL
        text          TYPE clike OPTIONAL
        icon          TYPE clike OPTIONAL
        value         TYPE clike OPTIONAL
        onscan        TYPE clike OPTIONAL
        onerror       TYPE clike OPTIONAL
        showinbrowser TYPE clike OPTIONAL
      RETURNING
        VALUE(result) TYPE REF TO z2ui5_cl_xml_view.

" --- implementation part ----------------------------------------------------
  METHOD native_bridge_scan.

    result = mo_view.
    mo_view->_generic( name   = `NativeBridgeScan`
                       ns     = `z2ui5`
                       t_prop = VALUE #( ( n = `id`            v = id )
                                         ( n = `text`          v = text )
                                         ( n = `icon`          v = icon )
                                         ( n = `value`         v = value )
                                         ( n = `OnScan`        v = onscan )
                                         ( n = `OnError`       v = onerror )
                                         ( n = `showInBrowser` v = showinbrowser )
         ) ).

  ENDMETHOD.
