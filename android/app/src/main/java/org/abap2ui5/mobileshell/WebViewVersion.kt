package org.abap2ui5.mobileshell

import android.content.Context
import android.util.Log
import android.webkit.WebView

/**
 * WebView version floor (Phase 4, PLAN.md risk 2).
 *
 * On locked-down managed devices the Android System WebView is often frozen
 * at whatever shipped with the image, while abap2UI5 serves a current
 * OpenUI5 (1.142 at the time of writing). A too-old WebView does not fail
 * loudly — the SPA renders half-broken — so the shell checks the version at
 * startup and says so instead of leaving users to guess.
 *
 * [MINIMUM_MAJOR] is a deliberately conservative floor. Align it with the
 * browser-support statement of the UI5 version your backend bootstraps and
 * pin the same number in the EMM's device compliance rules
 * (see docs/DISTRIBUTION.md).
 */
object WebViewVersion {

    const val MINIMUM_MAJOR = 100

    private const val TAG = "WebViewVersion"

    /** Major version of the installed WebView, or null when undeterminable. */
    fun current(context: Context): Int? =
        majorVersion(WebView.getCurrentWebViewPackage()?.versionName)

    /**
     * Parses "120.0.6099.43" to 120.
     * Returns null for null, empty or non-numeric version names — an unknown
     * version must never be reported as "too old".
     */
    fun majorVersion(versionName: String?): Int? =
        versionName?.trim()?.substringBefore('.')?.toIntOrNull()?.takeIf { it > 0 }

    /** True when the installed WebView is known to be below the floor. */
    fun isBelowMinimum(context: Context): Boolean {
        val major = current(context) ?: return false
        return major < MINIMUM_MAJOR
    }

    fun logCurrent(context: Context) {
        val pkg = WebView.getCurrentWebViewPackage()
        Log.i(TAG, "WebView provider=${pkg?.packageName} version=${pkg?.versionName} " +
            "(floor=$MINIMUM_MAJOR)")
    }
}
