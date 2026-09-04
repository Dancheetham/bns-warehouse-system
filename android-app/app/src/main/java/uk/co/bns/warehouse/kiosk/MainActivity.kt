package uk.co.bns.warehouse.kiosk

import android.Manifest
import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Locale

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

private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
private const val STATUS_BAR_UPDATE_INTERVAL_MS = 30_000L

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var statusTime: TextView
    private lateinit var statusWifi: TextView
    private lateinit var statusBattery: TextView

    private val statusBarHandler = Handler(Looper.getMainLooper())
    private val statusBarUpdater = object : Runnable {
        override fun run() {
            updateClock()
            updateWifiSignal()
            statusBarHandler.postDelayed(this, STATUS_BAR_UPDATE_INTERVAL_MS)
        }
    }

    // Battery level changes are a sticky broadcast, not something worth
    // polling on the same timer as the clock/Wi-Fi - registering for the
    // broadcast means it updates the moment the level actually changes.
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level >= 0 && scale > 0) {
                statusBattery.text = "${(level * 100 / scale)}%"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        hideSystemBars()

        statusTime = findViewById(R.id.statusTime)
        statusWifi = findViewById(R.id.statusWifi)
        statusBattery = findViewById(R.id.statusBattery)

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

        requestLocationPermissionForWifiSignal()
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        startKioskPinning()

        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        statusBarUpdater.run()
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(batteryReceiver)
        } catch (e: IllegalArgumentException) {
            // Wasn't registered (e.g. onPause without a matching onResume
            // having run yet) - harmless, nothing to clean up.
        }
        statusBarHandler.removeCallbacks(statusBarUpdater)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun updateClock() {
        val format = SimpleDateFormat("HH:mm", Locale.getDefault())
        statusTime.text = format.format(System.currentTimeMillis())
    }

    /**
     * Wi-Fi RSSI needs location permission granted on API 27+ (Android 8.1) -
     * without it, the OS returns a placeholder value rather than the real
     * signal strength, since Wi-Fi scan results can otherwise be used to
     * infer location. Requested once at startup - a single one-time prompt is
     * reasonable for a dedicated kiosk device provisioned once, not something
     * that needs to happen on every launch.
     */
    private fun requestLocationPermissionForWifiSignal() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun updateWifiSignal() {
        try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            if (!wifiManager.isWifiEnabled) {
                statusWifi.text = "Wi-Fi off"
                return
            }
            @Suppress("DEPRECATION")
            val connectionInfo = wifiManager.connectionInfo
            val rssi = connectionInfo?.rssi
            if (rssi == null || rssi == Int.MIN_VALUE) {
                statusWifi.text = "Wi-Fi --"
                return
            }
            @Suppress("DEPRECATION")
            val level = WifiManager.calculateSignalLevel(rssi, 5) // 0..4
            val bar = "▂▄▆█".let { chars ->
                (0 until 4).joinToString("") { i -> if (i < level) chars[i].toString() else "·" }
            }
            statusWifi.text = "Wi-Fi $bar"
        } catch (e: Exception) {
            // Missing permission, Wi-Fi service unavailable, etc. - shows a
            // clear placeholder rather than crashing the whole app over a
            // status readout that isn't essential to actually using it.
            statusWifi.text = "Wi-Fi --"
        }
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
