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
 *
 * The lock is a user toggle unless an EMM manages `app_lock_enabled`
 * (Phase 4, regulated scenarios): a managed value wins over the preference
 * and cannot be toggled away in the app.
 */
object AppLock {

    private const val PREFS = "shell_prefs"
    private const val KEY_ENABLED = "app_lock_enabled"
    private const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    fun isEnabled(context: Context): Boolean =
        ManagedConfig.read(context).appLockEnabled ?: isEnabledByUser(context)

    /** False when an EMM manages the setting — the UI must not offer a toggle. */
    fun isUserConfigurable(context: Context): Boolean =
        ManagedConfig.read(context).appLockEnabled == null

    /** True when the lock is administrator-enforced rather than user-chosen. */
    fun isEnforced(context: Context): Boolean =
        ManagedConfig.read(context).appLockEnabled == true

    fun toggle(activity: FragmentActivity) {
        if (!isUserConfigurable(activity)) return
        val enable = !isEnabledByUser(activity)
        if (enable && !canAuthenticate(activity)) return
        activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enable).apply()
    }

    enum class Failure {
        /** The user cancelled or failed the prompt. */
        CANCELLED,

        /** No biometric/device credential is enrolled, and the lock is enforced. */
        NO_AUTHENTICATOR,
    }

    /**
     * Blocks app start until the user authenticates.
     *
     * With no authenticator enrolled a user-chosen lock fails open (not
     * locking someone out of their own shell), while an enforced lock fails
     * closed — an administrator who requires the lock requires a device
     * credential with it.
     */
    fun gate(activity: FragmentActivity, onSuccess: () -> Unit, onFailure: (Failure) -> Unit) {
        if (!canAuthenticate(activity)) {
            if (isEnforced(activity)) onFailure(Failure.NO_AUTHENTICATOR) else onSuccess()
            return
        }
        prompt(activity, activity.getString(R.string.app_lock_title),
            onResult = { ok -> if (ok) onSuccess() else onFailure(Failure.CANCELLED) })
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

    private fun isEnabledByUser(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)

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
