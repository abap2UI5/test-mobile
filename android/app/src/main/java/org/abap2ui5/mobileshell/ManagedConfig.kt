package org.abap2ui5.mobileshell

import android.content.Context
import android.content.RestrictionsManager

/**
 * MDM managed configuration (Phase 4): an EMM (Intune, Workspace ONE, ...)
 * can push the endpoint so users never type a URL. Keys are declared in
 * res/xml/app_restrictions.xml.
 */
object ManagedConfig {

    fun endpointOverride(context: Context): String? {
        val rm = context.getSystemService(Context.RESTRICTIONS_SERVICE)
            as? RestrictionsManager ?: return null
        val restrictions = rm.applicationRestrictions ?: return null
        return restrictions.getString("endpoint_url")?.takeIf { it.isNotBlank() }
    }
}
