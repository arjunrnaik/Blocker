package com.lockweb.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.lockweb.app.admin.LockWebDeviceAdminReceiver
import com.lockweb.app.ui.LockWebApp
import com.lockweb.app.ui.theme.LockWebTheme
import com.lockweb.app.vpn.LockWebVpnService
import com.lockweb.app.vpn.VpnStatus

class MainActivity : ComponentActivity() {

    private val vpnConsentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            startVpnServiceInternal()
        } else {
            LockWebVpnService.setStatus(VpnStatus.Error("VPN consent dialog was cancelled by user."))
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        // Notification permission callback
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestNotificationPermissionIfNeeded()

        setContent {
            val app = application as LockWebApplication
            val appTheme by app.container.settingsRepository.appTheme.collectAsState(initial = "SYSTEM")

            LockWebTheme(appTheme = appTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LockWebApp(
                        onStartVpn = { handleStartVpn() },
                        onStopVpn = { handleStopVpn() },
                        onRequestVpnConsent = { handleStartVpn() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        LockWebDeviceAdminReceiver.isActive(this)
        LockWebVpnService.refreshAlwaysOnState(this)
        com.lockweb.app.guard.GuardSessionManager.instance.syncGuardStatus(this)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun handleStartVpn() {
        val prepareIntent = VpnService.prepare(this)
        if (prepareIntent != null) {
            vpnConsentLauncher.launch(prepareIntent)
        } else {
            startVpnServiceInternal()
        }
    }

    private fun startVpnServiceInternal() {
        val startIntent = Intent(this, LockWebVpnService::class.java).apply {
            action = LockWebVpnService.ACTION_START
        }
        ContextCompat.startForegroundService(this, startIntent)
    }

    private fun handleStopVpn() {
        val stopIntent = Intent(this, LockWebVpnService::class.java).apply {
            action = LockWebVpnService.ACTION_STOP
        }
        startService(stopIntent)
    }
}
