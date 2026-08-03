package org.abap2ui5.mobileshell

import android.content.Context
import org.json.JSONObject

/**
 * QR onboarding (Phase 1): instead of typing the endpoint, the user scans a
 * QR code — same UX idea as the BTP SDK's QR onboarding, without the SDK.
 *
 * Accepted payloads:
 *   - a plain URL:  https://host/sap/bc/z2ui5?sap-client=100
 *   - a JSON object: {"url": "https://...", "msHost": "x.hana.ondemand.com",
 *                     "msAppId": "com.example.app"}
 *     msHost/msAppId are optional and enable Mobile Services push
 *     registration (see MobileServicesPush).
 */
object Onboarding {

    private const val PREFS = "shell_prefs"

    /** Returns the endpoint URL, or null when the payload is not usable. */
    fun parseQrPayload(context: Context, payload: String): String? {
        val trimmed = payload.trim()
        if (trimmed.startsWith("https://") || trimmed.startsWith("http://")) {
            return trimmed
        }
        if (trimmed.startsWith("{")) {
            return try {
                val json = JSONObject(trimmed)
                val url = json.optString("url").takeIf { it.isNotBlank() } ?: return null
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
                    json.optString("msHost").takeIf { it.isNotBlank() }
                        ?.let { putString(MobileServicesPush.KEY_MS_HOST, it) }
                    json.optString("msAppId").takeIf { it.isNotBlank() }
                        ?.let { putString(MobileServicesPush.KEY_MS_APP_ID, it) }
                }.apply()
                url
            } catch (e: Exception) {
                null
            }
        }
        return null
    }
}
