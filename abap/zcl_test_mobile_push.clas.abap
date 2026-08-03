CLASS zcl_test_mobile_push DEFINITION PUBLIC FINAL CREATE PUBLIC.

  PUBLIC SECTION.

    "! Thin client for the SAP Mobile Services backend push API (Phase 2).
    "!
    "! Sends a notification to all devices a user registered through the
    "! shell (see android/.../MobileServicesPush.kt):
    "!
    "!   POST https://{host}/mobileservices/push/v1/backend
    "!        /applications/{app_id}/notifications/users/{user}
    "!
    "! Authentication: OAuth2 client credentials from the Mobile Services
    "! service key (role push, url = token endpoint). For simplicity the PoC
    "! takes a ready bearer token — productively, wrap token retrieval and
    "! caching here or use an HTTP destination of type OAuth2.
    "!
    "! Written for ABAP Cloud (if_web_http_client); for Standard ABAP
    "! replace with cl_http_client=>create_by_url accordingly.
    METHODS constructor
      IMPORTING
        iv_host   TYPE string
        iv_app_id TYPE string.

    "! @parameter iv_deep_link | full abap2UI5 URL incl. &app= — delivered as
    "!                           data key "url", the shells deep-link on tap
    METHODS push_to_user
      IMPORTING
        iv_user      TYPE string
        iv_token     TYPE string
        iv_alert     TYPE string
        iv_deep_link TYPE string OPTIONAL
      RAISING
        cx_web_http_client_error
        cx_http_dest_provider_error.

  PRIVATE SECTION.
    DATA mv_host   TYPE string.
    DATA mv_app_id TYPE string.

ENDCLASS.


CLASS zcl_test_mobile_push IMPLEMENTATION.

  METHOD constructor.
    mv_host   = iv_host.
    mv_app_id = iv_app_id.
  ENDMETHOD.

  METHOD push_to_user.

    DATA(lv_url) = |https://{ mv_host }/mobileservices/push/v1/backend| &&
                   |/applications/{ mv_app_id }/notifications/users/{ iv_user }|.

    DATA(lv_body) = |\{"alert":"{ iv_alert }"| &&
        COND #( WHEN iv_deep_link IS NOT INITIAL
                THEN |,"data":\{"url":"{ iv_deep_link }"\}| ) && |\}|.

    DATA(lo_dest) = cl_http_destination_provider=>create_by_url( lv_url ).
    DATA(lo_client) = cl_web_http_client_manager=>create_by_http_destination( lo_dest ).

    DATA(lo_request) = lo_client->get_http_request( ).
    lo_request->set_header_field( i_name  = `Authorization`
                                  i_value = |Bearer { iv_token }| ).
    lo_request->set_content_type( `application/json` ).
    lo_request->set_text( lv_body ).

    DATA(lo_response) = lo_client->execute( if_web_http_client=>post ).
    DATA(lv_status) = lo_response->get_status( )-code.
    IF lv_status < 200 OR lv_status >= 300.
      RAISE EXCEPTION NEW cx_web_http_client_error( ).
    ENDIF.

    lo_client->close( ).

  ENDMETHOD.

ENDCLASS.
