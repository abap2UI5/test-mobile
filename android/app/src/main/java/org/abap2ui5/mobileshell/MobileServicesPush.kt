package org.abap2ui5.mobileshell

import android.content.Context
import android.provider.Settings
import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * Device registration against the SAP Mobile Services push runtime API
 * (Phase 2):
 *
 *   POST https://{msHost}/mobileservices/push/v1/runtime/applications/{msAppId}
 *        /os/android/devices/{deviceId}
 *   body: {"pushToken": "<fcm token>"}
 *
 * msHost/msAppId come from the QR onboarding payload (see Onboarding) or
 * managed configuration. The call must run inside an authenticated Mobile
 * Services session; in the Phase-0/1 shell the WebView owns those cookies,
 * so this class logs and skips when no session strategy is configured yet.
 * Phase 1 (BTP SDK onboarding) replaces this with the SDK's own
 * push-registration helper which reuses the SDK session automatically.
 */
object MobileServicesPush {

    private const val TAG = "MobileServicesPush"
    private const val PREFS = "shell_prefs"
    const val KEY_MS_HOST = "ms_host"
    const val KEY_MS_APP_ID = "ms_app_id"

    fun registerAsync(context: Context, token: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val host = prefs.getString(KEY_MS_HOST, null)
        val appId = prefs.getString(KEY_MS_APP_ID, null)
        if (host.isNullOrBlank() || appId.isNullOrBlank()) {
            Log.i(TAG, "Mobile Services not configured — token stored locally only")
            return
        }
        val deviceId = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ANDROID_ID)

        thread(name = "ms-push-register") {
            try {
                val url = URL(
                    "https://$host/mobileservices/push/v1/runtime/applications/$appId" +
                        "/os/android/devices/$deviceId")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                // TODO Phase 1: attach the Mobile Services session (SDK
                // onboarding) — an unauthenticated call returns 401 here.
                conn.outputStream.use {
                    it.write("""{"pushToken":"$token"}""".toByteArray())
                }
                Log.i(TAG, "MS push registration: HTTP ${conn.responseCode}")
                conn.disconnect()
            } catch (e: Exception) {
                Log.w(TAG, "MS push registration failed", e)
            }
        }
    }
}
