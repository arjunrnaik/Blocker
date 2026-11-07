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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import com.lockweb.app.guard.GuardSessionManager
import com.lockweb.app.guard.GuardWatchdog
import com.lockweb.app.health.HealthMonitor
import com.lockweb.app.health.OverallProtectionLevel
import com.lockweb.app.health.ProtectionHealth
import com.lockweb.app.vpn.LockWebVpnService
import com.lockweb.app.vpn.VpnStatus
import com.lockweb.corefilter.BlockRule
import com.lockweb.corefilter.RuleMode
import com.lockweb.corefilter.RuleSet

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
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    VpnDebugScreen(
                        onStartVpn = { handleStartVpn() },
                        onStopVpn = { handleStopVpn() },
                        onResetCounter = { LockWebVpnService.resetCounter() }
                    )
                }
            }
        }
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

@Composable
fun VpnDebugScreen(
    onStartVpn: () -> Unit,
    onStopVpn: () -> Unit,
    onResetCounter: () -> Unit
) {
    val context = LocalContext.current
    val status by LockWebVpnService.status.collectAsState()
    val blockedCount by LockWebVpnService.blockedCount.collectAsState()
    val guardRunning by GuardSessionManager.instance.isGuardRunning.collectAsState()
    val health by HealthMonitor.observeProtectionHealth().collectAsState(
        initial = ProtectionHealth(vpnStatus = status, isGuardRunning = guardRunning)
    )

    val testRules = remember {
        mutableStateListOf("reddit.com")
    }
    var newDomainInput by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Column {
                Text(
                    text = "LockWeb",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Phase 5: Anti-Tamper Guard & Self-Protection Engine",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Overall Protection Health Badge
            OverallProtectionBadge(health = health)

            // Layer Breakdown Card
            ProtectionLayersCard(
                vpnStatus = status,
                isGuardRunning = guardRunning,
                onOpenAccessibilitySettings = {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                }
            )

            // Block Counter Card
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
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        OutlinedButton(
                            onClick = onResetCounter,
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

            // Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onStartVpn,
                    modifier = Modifier.weight(1f),
                    enabled = status != VpnStatus.Active && status != VpnStatus.Starting
                ) {
                    Text("Start Protection")
                }

                OutlinedButton(
                    onClick = onStopVpn,
                    modifier = Modifier.weight(1f),
                    enabled = status == VpnStatus.Active || status is VpnStatus.Degraded || status == VpnStatus.Starting,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Stop Protection")
                }
            }

            // Rule hot-swap test editor
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Active Block Rules (In-Memory Hot-Swap)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        testRules.forEach { domain ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = domain,
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "SUBDOMAINS",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newDomainInput,
                            onValueChange = { newDomainInput = it },
                            placeholder = { Text("e.g. twitter.com") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                val trimmed = newDomainInput.trim().lowercase()
                                if (trimmed.isNotEmpty() && !testRules.contains(trimmed)) {
                                    testRules.add(trimmed)
                                    val compiled = RuleSet.compile(
                                        userRules = testRules.map { BlockRule(it, RuleMode.SUBDOMAINS) },
                                        blocklistDomains = emptyList()
                                    ).ruleSet
                                    LockWebVpnService.updateRuleSet(compiled)
                                    newDomainInput = ""
                                }
                            },
                            enabled = newDomainInput.isNotBlank()
                        ) {
                            Text("Add")
                        }
                    }
                }
            }

            // Phase 5 Anti-Tamper & Verification Checklist
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Phase 5 Anti-Tamper Verification Checklist:",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "1. Enable 'LockWeb Anti-Tamper Guard' in Android Accessibility settings.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "2. Open Android Settings -> Apps -> LockWeb -> verify Password Gate pops up.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "3. Tap Back/Cancel on Password Gate -> verify you are sent back to Home screen.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "4. Open Settings -> VPN or Private DNS -> verify Password Gate appears.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "5. Enter password in Gate -> verify 60s temporary administrative access is granted.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
fun OverallProtectionBadge(health: ProtectionHealth) {
    val (label, color, desc) = when (health.overallLevel) {
        OverallProtectionLevel.PROTECTED -> Triple("Protected", Color(0xFF2E7D32), "Active DNS filtering + Anti-Tamper Guard running.")
        OverallProtectionLevel.DEGRADED -> Triple("Degraded (Weakened)", Color(0xFFED6C02), health.summaryText)
        OverallProtectionLevel.STOPPED -> Triple("Stopped", Color.Gray, "Protection is currently idle.")
        OverallProtectionLevel.ERROR -> Triple("Error", Color(0xFFD32F2F), health.summaryText)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.12f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(color)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Overall Status: $label",
                    fontWeight = FontWeight.Bold,
                    color = color,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ProtectionLayersCard(
    vpnStatus: VpnStatus,
    isGuardRunning: Boolean,
    onOpenAccessibilitySettings: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "PROTECTION LAYERS",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )

            // Layer 1: DNS VPN
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "DNS Filtering Engine (VPN)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (vpnStatus is VpnStatus.Active) "Active (NXDOMAIN blocking)" else "Inactive",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (vpnStatus is VpnStatus.Active) Color(0xFF2E7D32) else Color.Gray
                    )
                }
                Text(
                    text = if (vpnStatus is VpnStatus.Active) "✅" else "⚪",
                    fontSize = 18.sp
                )
            }

            // Layer 2: Anti-Tamper Guard
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Anti-Tamper Guard",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (isGuardRunning) "Running (Settings Intercept Active)" else "Disabled (Tap to Enable)",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isGuardRunning) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                    )
                }
                if (!isGuardRunning) {
                    Button(
                        onClick = onOpenAccessibilitySettings,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Enable", fontSize = 12.sp)
                    }
                } else {
                    Text(text = "✅", fontSize = 18.sp)
                }
            }
        }
    }
}
