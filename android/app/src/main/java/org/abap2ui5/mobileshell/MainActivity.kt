package org.abap2ui5.mobileshell

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import org.json.JSONObject

/**
 * Generic abap2UI5 shell: a full-screen WebView pointed at one abap2UI5
 * endpoint, plus the native bridge (see /bridge/README.md, contract v0).
 *
 * Phase 0: the endpoint is entered manually and stored in SharedPreferences.
 * Phase 1 replaces this with the SAP BTP SDK onboarding flow and hands the
 * authenticated session over to the WebView (see PLAN.md).
 */
class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var pendingScanId: String? = null

    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        val id = pendingScanId ?: return@registerForActivityResult
        pendingScanId = null
        if (result.contents != null) {
            resolveBridge(id, JSONObject.quote(result.contents), null)
        } else {
            resolveBridge(id, "null", "cancelled")
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = object : WebViewClient() {
            // Keep navigation inside the shell; the SPA is a single URL anyway.
            override fun onPageFinished(view: WebView, url: String) {
                view.evaluateJavascript(loadAsset("native-bridge.js"), null)
            }
        }
        webView.addJavascriptInterface(NativeBridge(), "__a2u5Android")

        val url = endpointUrl()
        if (url.isNullOrBlank()) askForEndpoint() else webView.loadUrl(url)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, MENU_ENDPOINT, 0, getString(R.string.menu_endpoint))
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == MENU_ENDPOINT) {
            askForEndpoint()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    private fun endpointUrl(): String? =
        getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_URL, null)

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
                    getSharedPreferences(PREFS, MODE_PRIVATE)
                        .edit().putString(KEY_URL, url).apply()
                    webView.loadUrl(url)
                }
            }
            .show()
    }

    private fun loadAsset(name: String): String =
        assets.open(name).bufferedReader().use { it.readText() }

    /** Settle a pending bridge promise; `resultLiteral` must be a JS literal. */
    private fun resolveBridge(id: String, resultLiteral: String, error: String?) {
        val errorLiteral = if (error == null) "null" else JSONObject.quote(error)
        val js = "window.__a2u5BridgeResolve(${JSONObject.quote(id)}, $resultLiteral, $errorLiteral)"
        runOnUiThread { webView.evaluateJavascript(js, null) }
    }

    /** Native side of bridge contract v0 — runs on a WebView worker thread. */
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
    }

    companion object {
        private const val PREFS = "shell_prefs"
        private const val KEY_URL = "endpoint_url"
        private const val MENU_ENDPOINT = 1
    }
}
