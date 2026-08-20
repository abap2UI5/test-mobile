package org.abap2ui5.mobileshell

import android.content.Context
import android.content.RestrictionsManager
import android.os.Bundle

/**
 * MDM managed configuration (Phase 4): an EMM (Intune, Workspace ONE, ...)
 * can push the shell's settings so users neither type a URL nor can weaken
 * the security posture. Keys are declared in res/xml/app_restrictions.xml.
 *
 * Every value is nullable on purpose: `null` means "not managed", which is
 * what lets the user keep control of a setting on unmanaged devices. A
 * managed `false` is an explicit administrator decision and must not be
 * confused with an absent key (Bundle.getBoolean would return false for
 * both — hence the containsKey checks).
 */
object ManagedConfig {

    const val KEY_ENDPOINT_URL = "endpoint_url"
    const val KEY_APP_LOCK_ENABLED = "app_lock_enabled"
    const val KEY_SCREENSHOT_PROTECTION = "screenshot_protection"

    data class Values(
        val endpointUrl: String? = null,
        val appLockEnabled: Boolean? = null,
        val screenshotProtection: Boolean? = null,
    )

    fun read(context: Context): Values {
        val rm = context.getSystemService(Context.RESTRICTIONS_SERVICE)
            as? RestrictionsManager ?: return Values()
        val restrictions: Bundle = rm.applicationRestrictions ?: return Values()
        return Values(
            endpointUrl = restrictions.getString(KEY_ENDPOINT_URL)?.takeIf { it.isNotBlank() },
            appLockEnabled = restrictions.booleanOrNull(KEY_APP_LOCK_ENABLED),
            screenshotProtection = restrictions.booleanOrNull(KEY_SCREENSHOT_PROTECTION),
        )
    }

    private fun Bundle.booleanOrNull(key: String): Boolean? =
        if (containsKey(key)) getBoolean(key) else null
}
