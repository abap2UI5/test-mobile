package org.abap2ui5.mobileshell

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Biometric/device-credential app lock (Phase 1). The BTP SDK ships its own
 * passcode/biometric flows — this is the SDK-free equivalent for the PoC and
 * doubles as backing for the bridge's biometricConfirm().
 */
object AppLock {

    private const val PREFS = "shell_prefs"
    private const val KEY_ENABLED = "app_lock_enabled"
    private const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)

    fun toggle(activity: FragmentActivity) {
        val enable = !isEnabled(activity)
        if (enable && !canAuthenticate(activity)) return
        activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enable).apply()
    }

    /** Blocks app start until the user authenticates. */
    fun gate(activity: FragmentActivity, onSuccess: () -> Unit, onFailure: () -> Unit) {
        if (!canAuthenticate(activity)) {
            // Enrollment removed since the lock was enabled — fail open
            // rather than locking the user out of a PoC shell.
            onSuccess()
            return
        }
        prompt(activity, activity.getString(R.string.app_lock_title),
            onResult = { ok -> if (ok) onSuccess() else onFailure() })
    }

    /** Bridge biometricConfirm(): one-off confirmation with a caller reason. */
    fun confirm(
        activity: FragmentActivity,
        reason: String,
        onResult: (Boolean) -> Unit,
        onUnavailable: () -> Unit,
    ) {
        if (!canAuthenticate(activity)) {
            onUnavailable()
            return
        }
        prompt(activity, reason.ifBlank { activity.getString(R.string.app_lock_title) }, onResult)
    }

    private fun canAuthenticate(context: Context): Boolean =
        BiometricManager.from(context)
            .canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    private fun prompt(activity: FragmentActivity, title: String, onResult: (Boolean) -> Unit) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) =
                    onResult(true)

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) =
                    onResult(false)
            })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setAllowedAuthenticators(AUTHENTICATORS)
                .build())
    }
}
