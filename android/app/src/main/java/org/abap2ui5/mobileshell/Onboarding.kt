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
 *
 * Parsing is kept free of Android APIs so it can be unit tested on the JVM
 * (see src/test/.../OnboardingTest.kt); persistence lives in [onboard].
 */
object Onboarding {

    private const val PREFS = "shell_prefs"

    data class Payload(
        val url: String,
        val msHost: String? = null,
        val msAppId: String? = null,
    )

    /** Parses a scanned payload; null when it is not a usable onboarding code. */
    fun parseQrPayload(payload: String): Payload? {
        val trimmed = payload.trim()
        if (trimmed.startsWith("https://") || trimmed.startsWith("http://")) {
            return Payload(trimmed)
        }
        if (trimmed.startsWith("{")) {
            return try {
                val json = JSONObject(trimmed)
                val url = json.optString("url").trim().takeIf { it.isNotBlank() } ?: return null
                Payload(
                    url = url,
                    msHost = json.optString("msHost").trim().takeIf { it.isNotBlank() },
                    msAppId = json.optString("msAppId").trim().takeIf { it.isNotBlank() },
                )
            } catch (e: Exception) {
                null
            }
        }
        return null
    }

    /**
     * Parses and stores a scanned payload.
     * Returns the endpoint URL, or null when the payload is not usable.
     */
    fun onboard(context: Context, payload: String): String? {
        val parsed = parseQrPayload(payload) ?: return null
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            parsed.msHost?.let { putString(MobileServicesPush.KEY_MS_HOST, it) }
            parsed.msAppId?.let { putString(MobileServicesPush.KEY_MS_APP_ID, it) }
        }.apply()
        return parsed.url
    }
}
