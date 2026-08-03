package org.abap2ui5.mobileshell

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.Menu
import android.view.MenuItem
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import org.json.JSONObject

/**
 * Generic abap2UI5 shell: a full-screen WebView pointed at one abap2UI5
 * endpoint, plus the native bridge (see /bridge/README.md, contract v1).
 *
 * Onboarding order for the endpoint URL:
 *   1. managed configuration (MDM app config, see ManagedConfig)
 *   2. stored preference (manual entry or QR onboarding)
 *   3. first-run dialog
 *
 * Authentication happens inside the WebView: the first request runs the
 * regular OAuth/SAML redirect dance of the backend (or of the Mobile
 * Services destination in front of it) and the session cookies persist in
 * the WebView's CookieManager. After BACKGROUND_RELOAD_MS in background the
 * SPA is reloaded so an expired server session re-authenticates cleanly.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var pendingScanId: String? = null
    private var pausedAt: Long = 0

    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        val id = pendingScanId ?: return@registerForActivityResult
        pendingScanId = null
        if (result.contents != null) {
            resolveBridge(id, JSONObject.quote(result.contents), null)
        } else {
            resolveBridge(id, "null", "cancelled")
        }
    }

    private val qrOnboardingLauncher = registerForActivityResult(ScanContract()) { result ->
        val contents = result.contents ?: return@registerForActivityResult
        val url = Onboarding.parseQrPayload(this, contents)
        if (url != null) {
            saveEndpoint(url)
            webView.loadUrl(url)
        } else {
            Toast.makeText(this, getString(R.string.toast_qr_invalid), Toast.LENGTH_LONG).show()
        }
    }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = object : WebViewClient() {
            // Keep navigation inside the shell; auth redirects (IdP login
            // pages) must also run in here so the cookies land in this
            // WebView's cookie store.
            override fun onPageFinished(view: WebView, url: String) {
                view.evaluateJavascript(loadAsset("native-bridge.js"), null)
            }
        }
        webView.addJavascriptInterface(NativeBridge(), "__a2u5Android")

        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        ManagedConfig.endpointOverride(this)?.let { saveEndpoint(it) }

        if (AppLock.isEnabled(this)) {
            AppLock.gate(this,
                onSuccess = { start() },
                onFailure = { finish() })
        } else {
            start()
        }
    }

    private fun start() {
        // A notification tap hands the target URL over as an extra
        // (see PushService); it wins over the configured endpoint once.
        val deepLink = intent.getStringExtra(PushService.EXTRA_DEEPLINK_URL)
        val url = deepLink ?: endpointUrl()
        if (url.isNullOrBlank()) askForEndpoint() else webView.loadUrl(url)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(PushService.EXTRA_DEEPLINK_URL)?.let { webView.loadUrl(it) }
    }

    override fun onPause() {
        super.onPause()
        pausedAt = SystemClock.elapsedRealtime()
    }

    override fun onResume() {
        super.onResume()
        // After a long background stay the abap2UI5 draft session and the
        // auth session are likely gone — reload instead of resuming a dead
        // SPA (risk 3 in PLAN.md).
        if (pausedAt > 0 && SystemClock.elapsedRealtime() - pausedAt > BACKGROUND_RELOAD_MS) {
            endpointUrl()?.let { webView.loadUrl(it) }
        }
        pausedAt = 0
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, MENU_ENDPOINT, 0, getString(R.string.menu_endpoint))
        menu.add(0, MENU_QR_ONBOARDING, 1, getString(R.string.menu_qr_onboarding))
        menu.add(0, MENU_APP_LOCK, 2, getString(
            if (AppLock.isEnabled(this)) R.string.menu_app_lock_disable
            else R.string.menu_app_lock_enable))
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(MENU_APP_LOCK)?.title = getString(
            if (AppLock.isEnabled(this)) R.string.menu_app_lock_disable
            else R.string.menu_app_lock_enable)
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        MENU_ENDPOINT -> { askForEndpoint(); true }
        MENU_QR_ONBOARDING -> { launchQrOnboarding(); true }
        MENU_APP_LOCK -> { AppLock.toggle(this); true }
        else -> super.onOptionsItemSelected(item)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    private fun endpointUrl(): String? =
        getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_URL, null)

    private fun saveEndpoint(url: String) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_URL, url).apply()
    }

    private fun askForEndpoint() {
        val input = EditText(this).apply {
            hint = getString(R.string.dialog_endpoint_hint)
            setText(endpointUrl() ?: "")
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_endpoint_title))
            .setView(input)
            .setCancelable(endpointUrl() != null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val url = input.text.toString().trim()
                if (url.isNotEmpty()) {
                    saveEndpoint(url)
                    webView.loadUrl(url)
                }
            }
            .setNeutralButton(R.string.menu_qr_onboarding) { _, _ -> launchQrOnboarding() }
            .show()
    }

    private fun launchQrOnboarding() {
        qrOnboardingLauncher.launch(ScanOptions().apply {
            setPrompt(getString(R.string.qr_onboarding_prompt))
            setBeepEnabled(false)
            setOrientationLocked(false)
        })
    }

    private fun loadAsset(name: String): String =
        assets.open(name).bufferedReader().use { it.readText() }

    /** Settle a pending bridge promise; `resultLiteral` must be a JS literal. */
    private fun resolveBridge(id: String, resultLiteral: String, error: String?) {
        val errorLiteral = if (error == null) "null" else JSONObject.quote(error)
        val js = "window.__a2u5BridgeResolve(${JSONObject.quote(id)}, $resultLiteral, $errorLiteral)"
        runOnUiThread { webView.evaluateJavascript(js, null) }
    }

    /** Native side of bridge contract v1 — runs on a WebView worker thread. */
    inner class NativeBridge {

        @JavascriptInterface
        fun platformInfo(): String = JSONObject()
            .put("platform", "android")
            .put("model", "${Build.MANUFACTURER} ${Build.MODEL}")
            .put("osVersion", Build.VERSION.RELEASE)
            .put("appVersion", BuildConfig.VERSION_NAME)
            .toString()

        @JavascriptInterface
        fun showToast(text: String) {
            runOnUiThread {
                Toast.makeText(this@MainActivity, text, Toast.LENGTH_SHORT).show()
            }
        }

        @JavascriptInterface
        fun scanBarcode(id: String) {
            runOnUiThread {
                if (pendingScanId != null) {
                    resolveBridge(id, "null", "busy")
                    return@runOnUiThread
                }
                pendingScanId = id
                scanLauncher.launch(ScanOptions().apply {
                    setBeepEnabled(false)
                    setOrientationLocked(false)
                })
            }
        }

        @JavascriptInterface
        fun getPushToken(id: String) {
            val token = PushService.storedToken(this@MainActivity)
            if (token != null) {
                resolveBridge(id, JSONObject.quote(token), null)
            } else {
                resolveBridge(id, "null", "unavailable")
            }
        }

        @JavascriptInterface
        fun biometricConfirm(id: String, reason: String) {
            runOnUiThread {
                AppLock.confirm(this@MainActivity, reason,
                    onResult = { ok -> resolveBridge(id, if (ok) "true" else "false", null) },
                    onUnavailable = { resolveBridge(id, "null", "unavailable") })
            }
        }
    }

    companion object {
        private const val PREFS = "shell_prefs"
        private const val KEY_URL = "endpoint_url"
        private const val MENU_ENDPOINT = 1
        private const val MENU_QR_ONBOARDING = 2
        private const val MENU_APP_LOCK = 3
        private const val BACKGROUND_RELOAD_MS = 30L * 60 * 1000
    }
}
