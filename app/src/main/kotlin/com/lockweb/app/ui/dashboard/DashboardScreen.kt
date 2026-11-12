package com.lockweb.app.ui.dashboard

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lockweb.app.LockWebApplication
import com.lockweb.app.data.AddRuleResult
import com.lockweb.app.health.OverallProtectionLevel
import com.lockweb.app.health.ProtectionHealth
import com.lockweb.app.ui.components.AddRuleDialog
import com.lockweb.app.ui.theme.StatusError
import com.lockweb.app.ui.theme.StatusProtected
import com.lockweb.app.ui.theme.StatusWarning
import com.lockweb.app.vpn.LockWebVpnService
import com.lockweb.app.vpn.VpnStatus
import kotlinx.coroutines.launch

@Composable
fun DashboardScreen(
    health: ProtectionHealth,
    blockedCount: Long,
    onStartVpn: () -> Unit,
    onStopVpn: () -> Unit,
    onNavigateToBlocklist: () -> Unit,
    onNavigateToStatus: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as LockWebApplication
    val scope = rememberCoroutineScope()

    val userRules by app.container.ruleRepository.userRules.collectAsState(initial = emptyList())
    val adultEnabled by app.container.settingsRepository.isAdultBlocklistEnabled.collectAsState(initial = false)

    var showAddRuleDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddRuleDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Add Rule")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // App Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "LockWeb",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        text = "Self-Control & Privacy Shield",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Status indicator pill
                Surface(
                    color = when (health.overallLevel) {
                        OverallProtectionLevel.PROTECTED -> StatusProtected.copy(alpha = 0.15f)
                        OverallProtectionLevel.DEGRADED -> StatusWarning.copy(alpha = 0.15f)
                        OverallProtectionLevel.STOPPED -> MaterialTheme.colorScheme.surfaceVariant
                        OverallProtectionLevel.ERROR -> StatusError.copy(alpha = 0.15f)
                    },
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.clickable { onNavigateToStatus() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    when (health.overallLevel) {
                                        OverallProtectionLevel.PROTECTED -> StatusProtected
                                        OverallProtectionLevel.DEGRADED -> StatusWarning
                                        OverallProtectionLevel.STOPPED -> Color.Gray
                                        OverallProtectionLevel.ERROR -> StatusError
                                    }
                                )
                        )
                        Text(
                            text = when (health.overallLevel) {
                                OverallProtectionLevel.PROTECTED -> "Active"
                                OverallProtectionLevel.DEGRADED -> "Degraded"
                                OverallProtectionLevel.STOPPED -> "Paused"
                                OverallProtectionLevel.ERROR -> "Error"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = when (health.overallLevel) {
                                OverallProtectionLevel.PROTECTED -> StatusProtected
                                OverallProtectionLevel.DEGRADED -> StatusWarning
                                OverallProtectionLevel.STOPPED -> MaterialTheme.colorScheme.onSurfaceVariant
                                OverallProtectionLevel.ERROR -> StatusError
                            }
                        )
                    }
                }
            }

            // Primary Protection Hero Module
            ProtectionHeroCard(
                health = health,
                adultEnabled = adultEnabled,
                onStartVpn = onStartVpn,
                onStopVpn = onStopVpn,
                onNavigateToStatus = onNavigateToStatus,
                onFixGuard = {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                }
            )

            // Blocked Queries Counter Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "BLOCKED REQUESTS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (blockedCount > 0) {
                            IconButton(
                                onClick = { LockWebVpnService.resetCounter() },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Refresh,
                                    contentDescription = "Reset Counter",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "$blockedCount",
                        fontSize = 42.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = (-1).sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Real-time anonymous filtering • Zero history stored",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Custom Rules Quick Glance
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Active Rules",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onNavigateToBlocklist() }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "View all (${userRules.size})",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Icon(
                                Icons.Rounded.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (userRules.isEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "No custom rules added yet. Tap '+' to block a domain.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            userRules.take(3).forEach { rule ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = rule.normalizedHost,
                                            fontFamily = FontFamily.Monospace,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Surface(
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = rule.mode,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Adult Filter status row
                    Surface(
                        color = if (adultEnabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToBlocklist() }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Lock,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (adultEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Adult Content Blocker",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Text(
                                text = if (adultEnabled) "Permanent Lock" else "Off",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (adultEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddRuleDialog) {
        AddRuleDialog(
            onAddRule = { domain, mode, note ->
                scope.launch {
                    val result = app.container.ruleRepository.addRule(domain, mode, note)
                    when (result) {
                        is AddRuleResult.Success -> {
                            showAddRuleDialog = false
                            Toast.makeText(context, "Blocked ${result.entity.normalizedHost}", Toast.LENGTH_SHORT).show()
                        }
                        is AddRuleResult.Duplicate -> {
                            Toast.makeText(context, "Rule already exists for this domain", Toast.LENGTH_LONG).show()
                        }
                        is AddRuleResult.InvalidDomain -> {
                            Toast.makeText(context, "Invalid domain: ${result.reason}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            },
            onDismiss = { showAddRuleDialog = false }
        )
    }
}

@Composable
private fun ProtectionHeroCard(
    health: ProtectionHealth,
    adultEnabled: Boolean,
    onStartVpn: () -> Unit,
    onStopVpn: () -> Unit,
    onNavigateToStatus: () -> Unit,
    onFixGuard: () -> Unit
) {
    val isRunning = health.vpnStatus is VpnStatus.Active
    val hasAlert = !health.isGuardRunning || health.overallLevel == OverallProtectionLevel.DEGRADED

    val cardColor = when {
        hasAlert -> StatusWarning.copy(alpha = 0.12f)
        isRunning -> StatusProtected.copy(alpha = 0.12f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = when {
                            hasAlert -> StatusWarning.copy(alpha = 0.2f)
                            isRunning -> StatusProtected.copy(alpha = 0.2f)
                            else -> MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = when {
                                    hasAlert -> Icons.Rounded.Warning
                                    isRunning -> Icons.Rounded.Shield
                                    else -> Icons.Rounded.Pause
                                },
                                contentDescription = null,
                                tint = when {
                                    hasAlert -> StatusWarning
                                    isRunning -> StatusProtected
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    Column {
                        Text(
                            text = when {
                                hasAlert && !health.isGuardRunning -> "Guard Disconnected"
                                hasAlert -> "Protection Degraded"
                                isRunning -> "Protection Active"
                                else -> "Protection Paused"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = when {
                                !health.isGuardRunning -> "Anti-tamper protection requires attention"
                                isRunning -> "DNS inspection active • Zero battery impact"
                                else -> "Filter is currently paused"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Quick Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!health.isGuardRunning) {
                    Button(
                        onClick = onFixGuard,
                        colors = ButtonDefaults.buttonColors(containerColor = StatusWarning),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Enable Guard", fontWeight = FontWeight.Bold)
                    }
                } else if (!isRunning) {
                    Button(
                        onClick = onStartVpn,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Resume")
                    }
                } else {
                    OutlinedButton(
                        onClick = onStopVpn,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Rounded.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Pause Protection")
                    }
                }

                OutlinedButton(
                    onClick = onNavigateToStatus,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Audit Layers")
                }
            }

            // Mini Status Pills Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusPill("VPN", health.vpnStatus is VpnStatus.Active, Modifier.weight(1f))
                StatusPill("Guard", health.isGuardRunning, Modifier.weight(1f))
                StatusPill("Admin", health.isDeviceAdminActive, Modifier.weight(1f))
                StatusPill("Adult Lock", adultEnabled, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatusPill(label: String, active: Boolean, modifier: Modifier = Modifier) {
    Surface(
        color = if (active) StatusProtected.copy(alpha = 0.12f)
        else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (active) StatusProtected else Color.Gray)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (active) StatusProtected else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
