package com.lockweb.app.ui.status

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.lockweb.app.admin.LockWebDeviceAdminReceiver
import com.lockweb.app.health.ProtectionHealth
import com.lockweb.app.ui.theme.StatusError
import com.lockweb.app.ui.theme.StatusProtected
import com.lockweb.app.ui.theme.StatusWarning
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
    val cm = remember { context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager }

    var isBatteryExempt by remember {
        mutableStateOf(powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false)
    }

    var isPrivateDnsActive by remember {
        val activeNetwork = cm?.activeNetwork
        val lp: LinkProperties? = cm?.getLinkProperties(activeNetwork)
        mutableStateOf(!lp?.privateDnsServerName.isNullOrBlank())
    }

    // Automatically re-sync system permissions and status whenever LockWeb resumes from Settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isBatteryExempt = powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
                val activeNetwork = cm?.activeNetwork
                val lp: LinkProperties? = cm?.getLinkProperties(activeNetwork)
                isPrivateDnsActive = !lp?.privateDnsServerName.isNullOrBlank()
                LockWebDeviceAdminReceiver.isActive(context)
                LockWebVpnService.refreshAlwaysOnState(context)
                com.lockweb.app.guard.GuardSessionManager.instance.syncGuardStatus(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var showAccessibilityHelpDialog by remember { mutableStateOf(false) }

    if (showAccessibilityHelpDialog) {
        AlertDialog(
            onDismissRequest = { showAccessibilityHelpDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = "Enable Anti-Tamper Guard",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Anti-Tamper Guard requires Accessibility permission to detect and guard system settings against uninstallation or bypassing.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "If Android says 'Restricted setting':",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "1. First tap 'Open Accessibility' below, find LockWeb, and tap it (this logs the block in Android).\n2. If it says 'Restricted', tap 'Open App Info' below.\n3. Tap the 3 dots (⋮) in the top-right corner.\n4. Select 'Allow restricted settings' and confirm your PIN.\n5. Return to Accessibility and turn LockWeb ON.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Open Accessibility")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:${context.packageName}")
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Open App Info")
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
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
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header
            Column {
                Text(
                    text = "Protection Health",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = "Live diagnostic status of all security layers",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Section 1: Active Filter Engine
            SecuritySectionCard(title = "Filtering Engine") {
                SecurityItemRow(
                    icon = Icons.Rounded.Shield,
                    title = "DNS Filtering Loopback",
                    subtitle = "Intercepts UDP port 53 DNS queries locally. Routes zero general traffic.",
                    status = when (health.vpnStatus) {
                        is VpnStatus.Active -> ItemStatus.Active("Running")
                        is VpnStatus.Starting -> ItemStatus.Warning("Starting...")
                        is VpnStatus.Degraded -> ItemStatus.Warning("Degraded")
                        is VpnStatus.Error -> ItemStatus.Error("Error")
                        else -> ItemStatus.Warning("Stopped")
                    },
                    action = {
                        if (health.vpnStatus !is VpnStatus.Active) {
                            Button(onClick = onStartVpn) { Text("Start") }
                        } else {
                            OutlinedButton(
                                onClick = onStopVpn,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) { Text("Pause") }
                        }
                    }
                )
            }

            // Section 2: Tamper Protection Suite
            SecuritySectionCard(title = "Tamper Prevention") {
                SecurityItemRow(
                    icon = Icons.Rounded.Security,
                    title = "Anti-Tamper Guard",
                    subtitle = "Blocks unauthorized access to system settings, Force Stop, and app info.",
                    status = if (health.isGuardRunning) ItemStatus.Active("Running") else ItemStatus.Error("Disabled"),
                    action = {
                        if (!health.isGuardRunning) {
                            Button(
                                onClick = {
                                    showAccessibilityHelpDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Enable")
                            }
                        }
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                SecurityItemRow(
                    icon = Icons.Rounded.Lock,
                    title = "Device Administrator",
                    subtitle = "Blocks direct launcher uninstallation while active. Deactivation requires master key.",
                    status = if (health.isDeviceAdminActive) ItemStatus.Active("Active") else ItemStatus.Warning("Inactive"),
                    action = {
                        if (!health.isDeviceAdminActive) {
                            FilledTonalButton(
                                onClick = {
                                    context.startActivity(LockWebDeviceAdminReceiver.createActivationIntent(context))
                                }
                            ) {
                                Text("Activate")
                            }
                        }
                    }
                )
            }

            // Section 3: Android System Guarantees
            SecuritySectionCard(title = "System Guarantees") {
                SecurityItemRow(
                    icon = Icons.Rounded.VpnKey,
                    title = "Always-On VPN & Lockdown",
                    subtitle = if (health.isAlwaysOnVpn && !health.isLockdownEnabled) {
                        "Always-On is active. Enable 'Block connections without VPN' in system settings to prevent leaks if VPN drops."
                    } else {
                        "System guarantee that cuts network access if the VPN service is ever closed."
                    },
                    status = when {
                        health.isAlwaysOnVpn && health.isLockdownEnabled -> ItemStatus.Active("Active & Locked")
                        health.isAlwaysOnVpn -> ItemStatus.Active("Always-On Active")
                        else -> ItemStatus.Warning("Not Configured")
                    },
                    action = {
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
                            Text(if (health.isAlwaysOnVpn) "Settings" else "Configure")
                        }
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                SecurityItemRow(
                    icon = Icons.Rounded.Dns,
                    title = "Private DNS Leak Shield",
                    subtitle = "Private DNS (DoT) can bypass local filters if turned on in system settings.",
                    status = if (isPrivateDnsActive) ItemStatus.Warning("Bypass Risk") else ItemStatus.Active("Safe (Off)"),
                    action = {
                        if (isPrivateDnsActive) {
                            Button(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(intent)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = StatusWarning)
                            ) {
                                Text("Fix in Settings")
                            }
                        }
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                SecurityItemRow(
                    icon = Icons.Rounded.BatteryFull,
                    title = "Background Battery Exemption",
                    subtitle = "Ensures Android does not put the local DNS filtering process to sleep.",
                    status = if (isBatteryExempt) ItemStatus.Active("Unrestricted") else ItemStatus.Warning("Optimized"),
                    action = {
                        if (!isBatteryExempt) {
                            FilledTonalButton(
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
            }

            // Chrome DoH note
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Rounded.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Browser Secure DNS Note",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Browsers like Chrome can bypass system DNS if 'Use Secure DNS' is enabled. The Anti-Tamper Guard automatically intercepts attempts to open Chrome Privacy Settings to protect this.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private sealed interface ItemStatus {
    val text: String
    data class Active(override val text: String) : ItemStatus
    data class Warning(override val text: String) : ItemStatus
    data class Error(override val text: String) : ItemStatus
}

@Composable
private fun SecuritySectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
private fun SecurityItemRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    status: ItemStatus,
    action: @Composable (() -> Unit)? = null
) {
    val statusColor = when (status) {
        is ItemStatus.Active -> StatusProtected
        is ItemStatus.Warning -> StatusWarning
        is ItemStatus.Error -> StatusError
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = CircleShape,
                    color = statusColor.copy(alpha = 0.15f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                color = statusColor.copy(alpha = 0.12f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = when (status) {
                            is ItemStatus.Active -> Icons.Rounded.CheckCircle
                            else -> Icons.Rounded.Warning
                        },
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = status.text,
                        color = statusColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (action != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                action()
            }
        }
    }
}
