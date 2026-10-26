package com.example.romalala

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : ComponentActivity() {
    private val projectionManager by lazy { getSystemService(MediaProjectionManager::class.java) }
    private var awaitingConsent = false
    private var starting = false
    private var stopping = false
    private var introPlayed = false
    private lateinit var menu: RomalalaMenuView
    private val serviceState = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            starting = false; stopping = false; refresh()
        }
    }
    private val captureConsent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        awaitingConsent = false
        val consentData = result.data
        if (result.resultCode != RESULT_OK || consentData == null) toast(R.string.capture_denied)
        else if (!Settings.canDrawOverlays(this)) toast(R.string.overlay_required)
        else {
            val start = Intent(this, RomalalaService::class.java).apply {
                action = RomalalaService.ACTION_START
                putExtra(RomalalaService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(RomalalaService.EXTRA_RESULT_DATA, consentData)
            }
            try {
                starting = true
                ContextCompat.startForegroundService(this, start)
            } catch (_: RuntimeException) {
                starting = false; toast(R.string.start_failed)
            }
        }
        refresh()
    }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) toast(R.string.notifications_off)
        launchCapture()
    }
    private val overlayPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        awaitingConsent = savedInstanceState?.getBoolean("awaitingConsent") ?: false
        introPlayed = savedInstanceState?.getBoolean("introPlayed") ?: false
        menu = RomalalaMenuView(this).apply {
            onPrimary = { primaryAction() }
            onSpotify = { openSpotify() }
        }
        ViewCompat.setOnApplyWindowInsetsListener(menu) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        setContentView(menu)
        refresh()
    }
    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(this, serviceState, IntentFilter(RomalalaService.ACTION_STATE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
    }
    override fun onResume() {
        super.onResume()
        if (RomalalaService.isRunning) starting = false
        if (!RomalalaService.isRunning) stopping = false
        refresh()
        if (!introPlayed) {
            introPlayed = true
            menu.post { if (!isFinishing && lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) menu.conversion.play() }
        }
    }
    override fun onStop() {
        menu.stopAnimation()
        unregisterReceiver(serviceState)
        super.onStop()
    }
    private fun primaryAction() {
        when {
            awaitingConsent || starting || stopping -> return
            RomalalaService.isRunning -> {
                stopping = stopService(Intent(this, RomalalaService::class.java))
                refresh()
            }
            !Settings.canDrawOverlays(this) -> {
                try {
                    overlayPermission.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                } catch (_: android.content.ActivityNotFoundException) { toast(R.string.overlay_settings_missing) }
            }
            else -> {
                awaitingConsent = true
                refresh()
                if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else launchCapture()
            }
        }
    }
    private fun launchCapture() {
        try { captureConsent.launch(projectionManager.createScreenCaptureIntent()) }
        catch (_: RuntimeException) {
            awaitingConsent = false; refresh(); toast(R.string.capture_unavailable)
        }
    }
    private fun openSpotify() {
        try {
            val launch = packageManager.getLaunchIntentForPackage("com.spotify.music")
            if (launch == null) toast(R.string.spotify_unavailable) else startActivity(launch)
        } catch (_: RuntimeException) { toast(R.string.spotify_unavailable) }
    }
    private fun refresh() {
        if (::menu.isInitialized) menu.update(Settings.canDrawOverlays(this), RomalalaService.isRunning, awaitingConsent || starting, stopping)
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("awaitingConsent", awaitingConsent)
        outState.putBoolean("introPlayed", introPlayed)
        super.onSaveInstanceState(outState)
    }
    private fun toast(message: Int) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
