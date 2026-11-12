package com.lockweb.app.ui.dashboard

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Text("+", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // App Title Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "LockWeb",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Self-Control & Website Blocker",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Loud Warning Banner when protection is weakened
            if (health.overallLevel == OverallProtectionLevel.DEGRADED || !health.isGuardRunning) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "⚠️ PROTECTION IS WEAKENED",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.labelLarge
                        )
                        if (!health.isGuardRunning) {
                            Text(
                                text = "Anti-Tamper Guard is inactive. Settings can be accessed without password.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Button(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(intent)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Re-Enable Guard")
                            }
                        } else if (health.vpnStatus !is VpnStatus.Active) {
                            Text(
                                text = "DNS VPN engine is not filtering traffic.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Button(onClick = onStartVpn) {
                                Text("Start DNS Filtering")
                            }
                        }
                    }
                }
            }

            // Honest Protection Status Badge
            DashboardStatusBadge(health = health, onCheckDetails = onNavigateToStatus)

            // Blocked Queries Counter Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "BLOCKED DNS QUERIES",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$blockedCount",
                            fontSize = 38.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        OutlinedButton(
                            onClick = { LockWebVpnService.resetCounter() },
                            enabled = blockedCount > 0
                        ) {
                            Text("Reset")
                        }
                    }
                    Text(
                        text = "Anonymous increment-only counter. Zero domain names stored.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Quick VPN Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onStartVpn,
                    modifier = Modifier.weight(1f),
                    enabled = health.vpnStatus !is VpnStatus.Active && health.vpnStatus !is VpnStatus.Starting
                ) {
                    Text("Start Protection")
                }

                OutlinedButton(
                    onClick = onStopVpn,
                    modifier = Modifier.weight(1f),
                    enabled = health.vpnStatus is VpnStatus.Active || health.vpnStatus is VpnStatus.Degraded,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Pause Protection")
                }
            }

            // Active Rules Quick View Card
            Card(
                modifier = Modifier.fillMaxWidth()
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
                            text = "Custom Block Rules (${userRules.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        OutlinedButton(onClick = onNavigateToBlocklist) {
                            Text("View All", fontSize = 12.sp)
                        }
                    }

                    if (userRules.isEmpty()) {
                        Text(
                            text = "No custom rules added yet. Tap '+' to add a website to block.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            userRules.take(3).forEach { rule ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = rule.normalizedHost,
                                        fontFamily = FontFamily.Monospace,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = rule.mode,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }
                    }

                    // Adult Content Status row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (adultEnabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                else MaterialTheme.colorScheme.surfaceContainer
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Adult Content Filter",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (adultEnabled) "Active 🔒" else "Disabled",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (adultEnabled) MaterialTheme.colorScheme.primary else Color.Gray
                        )
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
                            Toast.makeText(context, "Rule added for ${result.entity.normalizedHost}", Toast.LENGTH_SHORT).show()
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
fun DashboardStatusBadge(
    health: ProtectionHealth,
    onCheckDetails: () -> Unit
) {
    val (statusLabel, statusColor, desc) = when (health.overallLevel) {
        OverallProtectionLevel.PROTECTED -> Triple("Protected", Color(0xFF2E7D32), "Active DNS filtering + Anti-Tamper Guard running.")
        OverallProtectionLevel.DEGRADED -> Triple("Degraded", Color(0xFFED6C02), health.summaryText)
        OverallProtectionLevel.STOPPED -> Triple("Stopped", Color.Gray, "Protection is currently idle.")
        OverallProtectionLevel.ERROR -> Triple("Error", Color(0xFFD32F2F), health.summaryText)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = statusColor.copy(alpha = 0.12f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(statusColor)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Status: $statusLabel",
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedButton(onClick = onCheckDetails) {
                Text("Layers", fontSize = 11.sp)
            }
        }
    }
}
