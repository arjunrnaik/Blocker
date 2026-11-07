package com.lockweb.app.ui.status

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lockweb.app.admin.LockWebDeviceAdminReceiver
import com.lockweb.app.health.ProtectionHealth
import com.lockweb.app.vpn.LockWebVpnService
import com.lockweb.app.vpn.VpnStatus

@Composable
fun ProtectionStatusScreen(
    health: ProtectionHealth,
    onStartVpn: () -> Unit,
    onStopVpn: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val powerManager = remember { context.getSystemService(Context.POWER_SERVICE) as? PowerManager }

    fun checkPrivateDns(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNetwork = cm?.activeNetwork
        val lp: LinkProperties? = cm?.getLinkProperties(activeNetwork)
        val privateDnsServer = lp?.privateDnsServerName
        return !privateDnsServer.isNullOrBlank()
    }

    var isBatteryExempt by remember {
        mutableStateOf(powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false)
    }

    var isPrivateDnsActive by remember {
        mutableStateOf(checkPrivateDns())
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isBatteryExempt = powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
                isPrivateDnsActive = checkPrivateDns()
                LockWebDeviceAdminReceiver.isActive(context)
                LockWebVpnService.refreshAlwaysOnState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Column {
                Text(
                    text = "Protection Health",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Live diagnostic status of all security layers",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Layer 1: DNS-Only VPN Filter
            StatusLayerCard(
                title = "1. DNS-Only VPN Engine",
                subtitle = "Intercepts UDP port 53 DNS queries locally. Routes zero general traffic.",
                isActive = health.vpnStatus is VpnStatus.Active,
                statusText = when (health.vpnStatus) {
                    is VpnStatus.Active -> "Active ✅"
                    is VpnStatus.Starting -> "Starting..."
                    is VpnStatus.Idle -> "Stopped"
                    is VpnStatus.Degraded -> "Degraded: ${health.vpnStatus.reason}"
                    is VpnStatus.Error -> "Error: ${health.vpnStatus.reason}"
                    else -> "Inactive"
                },
                actionButton = {
                    if (health.vpnStatus !is VpnStatus.Active) {
                        Button(onClick = onStartVpn) {
                            Text("Start")
                        }
                    } else {
                        OutlinedButton(
                            onClick = onStopVpn,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Pause")
                        }
                    }
                }
            )

            // Layer 2: Anti-Tamper Guard
            StatusLayerCard(
                title = "2. Anti-Tamper Guard",
                subtitle = "Intercepts access to Android Settings, Force Stop, Clear Data, and VPN toggles.",
                isActive = health.isGuardRunning,
                statusText = if (health.isGuardRunning) "Running ✅" else "Disabled ⚠️",
                actionButton = {
                    if (!health.isGuardRunning) {
                        Button(
                            onClick = {
                                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Fix (Enable)")
                        }
                    }
                }
            )

            // Layer 3: Device Administrator (Anti-Uninstall)
            StatusLayerCard(
                title = "3. Device Administrator",
                subtitle = "Blocks direct launcher uninstallation while active. Deactivation requires master password.",
                isActive = health.isDeviceAdminActive,
                statusText = if (health.isDeviceAdminActive) "Active ✅" else "Inactive ⚠️",
                actionButton = {
                    if (!health.isDeviceAdminActive) {
                        Button(
                            onClick = {
                                context.startActivity(LockWebDeviceAdminReceiver.createActivationIntent(context))
                            }
                        ) {
                            Text("Activate")
                        }
                    }
                }
            )

            // Layer 4: Always-On VPN & Lockdown Reporting
            StatusLayerCard(
                title = "4. Always-On VPN & Lockdown",
                subtitle = "Android system-level guarantee that blocks connections if the VPN is down.",
                isActive = health.isAlwaysOnVpn && health.isLockdownEnabled,
                statusText = if (health.isAlwaysOnVpn && health.isLockdownEnabled) "Active & Locked ✅"
                else if (health.isAlwaysOnVpn) "Always-On (Turn on Lockdown) ⚠️"
                else "Not Enabled ⚠️",
                actionButton = {
                    OutlinedButton(
                        onClick = {
                            val intent = Intent("android.net.vpn.SETTINGS").apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            try {
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
                            }
                        }
                    ) {
                        Text("Configure")
                    }
                }
            )

            // Layer 5: Private DNS Check
            StatusLayerCard(
                title = "5. Android Private DNS",
                subtitle = "Private DNS (DoT) can bypass local VPN DNS filtering if enabled in Android Settings.",
                isActive = !isPrivateDnsActive,
                statusText = if (isPrivateDnsActive) "Enabled (Warning) ⚠️" else "Off (Safe) ✅",
                actionButton = {
                    if (isPrivateDnsActive) {
                        Button(
                            onClick = {
                                val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            }
                        ) {
                            Text("Open Settings")
                        }
                    }
                }
            )

            // Layer 6: Battery Optimization
            StatusLayerCard(
                title = "6. Background Battery Exemption",
                subtitle = "Ensures Android does not put the DNS VPN service to sleep in the background.",
                isActive = isBatteryExempt,
                statusText = if (isBatteryExempt) "Unrestricted ✅" else "Optimized (May sleep) ⚠️",
                actionButton = {
                    if (!isBatteryExempt) {
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                                }
                            }
                        ) {
                            Text("Exempt")
                        }
                    }
                }
            )

            // Browser Guidance Note
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "ℹ️ Browser DNS-over-HTTPS Note",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "Browsers like Chrome have an internal 'Use Secure DNS' toggle. When enabled, Chrome bypasses system DNS. The Anti-Tamper Guard blocks opening Chrome Privacy Settings to keep this disabled.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusLayerCard(
    title: String,
    subtitle: String,
    isActive: Boolean,
    statusText: String,
    actionButton: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = statusText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = if (isActive) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                )
            }

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                actionButton()
            }
        }
    }
}
