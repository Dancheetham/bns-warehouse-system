package uk.co.bns.warehouse.kiosk

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

/**
 * A thin full-screen wrapper around the handheld web app - not a rewrite of
 * anything, just a launcher-capable shell around it so a dedicated warehouse
 * scanner can be locked to this one screen.
 *
 * *** EDIT THIS before building *** - point it at wherever your warehouse
 * system is actually reachable on the LAN. If your server's IP changes
 * (common with DHCP), either update this and rebuild, or set a static/
 * reserved IP for the server on your router so this never needs touching again.
 */
private const val WAREHOUSE_URL = "http://192.168.1.245:8081/handheld"

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        hideSystemBars()

        webView = findViewById(R.id.webView)
        webView.settings.javaScriptEnabled = true
        // The web app uses localStorage for the "remembered accounts" list on
        // the login screen, and the session itself relies on cookies - both
        // need this on, and CookieManager needs an explicit accept below too.
        webView.settings.domStorageEnabled = true
        webView.settings.setSupportZoom(false)

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)

        // Without this, WebView hands any navigation off to whatever browser
        // is installed, which is exactly the "just a browser" behaviour this
        // app exists to avoid - keep every navigation inside the WebView.
        webView.webViewClient = WebViewClient()

        if (savedInstanceState == null) {
            webView.loadUrl(WAREHOUSE_URL)
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        startKioskPinning()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    /**
     * Screen pinning (Android's built-in "app pinning") - works on any
     * device with zero setup, unlike full Device Owner kiosk mode which
     * needs one-time adb provisioning on a factory-reset device. This is
     * what actually stops a Home swipe/button from leaving the app: once
     * pinned, Home and Recents are disabled until explicitly unpinned.
     *
     * The very first time, Android shows a brief system dialog explaining
     * pinning - that's a one-off, not something this code can skip (only
     * Device Owner mode can avoid it entirely). Every time after, this
     * silently re-pins on resume.
     */
    private fun startKioskPinning() {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val alreadyPinned = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            activityManager.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
        } else {
            @Suppress("DEPRECATION")
            activityManager.isInLockTaskMode
        }
        if (!alreadyPinned) {
            try {
                startLockTask()
            } catch (e: Exception) {
                // Some devices/OEM Android builds restrict this - fails safe
                // by just running as a normal (non-pinned) full-screen app
                // rather than crashing.
            }
        }
    }

    private fun hideSystemBars() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
            )
    }

    // Back button navigates the WebView's own history (e.g. handheld ->
    // picking -> back to handheld) instead of the default Android back
    // behaviour, which would otherwise back out of the app entirely.
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
