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
    val status by LockWebVpnService.status.collectAsState()
    val blockedCount by LockWebVpnService.blockedCount.collectAsState()

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
                    text = "Phase 3: DNS-Only VPN Engine & Live Verification",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Health Status Card
            StatusCard(status = status)

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

            // Real-device verification instructions
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
                        text = "Device Verification Checklist:",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "1. Tap 'Start Protection' and accept Android's VPN connection request.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "2. Check status indicator turns green (Active).",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "3. Open Chrome and visit 'reddit.com' -> should fail with DNS error.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "4. Switch back to LockWeb -> verify Blocked Queries counter incremented.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "5. Visit 'wikipedia.org' in Chrome -> should load fast with normal internet.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
fun StatusCard(status: VpnStatus) {
    val (statusLabel, statusColor, description) = when (status) {
        is VpnStatus.Idle -> Triple("Idle", Color.Gray, "Protection is currently stopped.")
        is VpnStatus.Starting -> Triple("Starting...", Color(0xFF2196F3), "Configuring TUN and running self-test...")
        is VpnStatus.Active -> Triple("Active", Color(0xFF2E7D32), "DNS filtering is actively protecting device.")
        is VpnStatus.Paused -> Triple("Paused", Color(0xFFED6C02), "Protection is temporarily paused.")
        is VpnStatus.PermissionRequired -> Triple("Permission Required", Color(0xFFED6C02), "VPN consent dialog pending.")
        is VpnStatus.Degraded -> Triple("Degraded", Color(0xFFED6C02), status.reason)
        is VpnStatus.Error -> Triple("Error", Color(0xFFD32F2F), status.reason)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = statusColor.copy(alpha = 0.12f)
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
                    .width(12.dp)
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
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
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
