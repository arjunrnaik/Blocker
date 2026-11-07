package com.lockweb.app.ui.settings

import android.widget.Toast
import com.lockweb.app.admin.LockWebDeviceAdminReceiver
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lockweb.app.LockWebApplication
import com.lockweb.app.blocklist.ImportResult
import com.lockweb.app.blocklist.RuleImportExport
import com.lockweb.app.ui.components.AuthGateDialog
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onStopProtection: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as LockWebApplication
    val scope = rememberCoroutineScope()

    val currentTheme by app.container.settingsRepository.appTheme.collectAsState(initial = "SYSTEM")
    val primaryDns by app.container.settingsRepository.primaryDns.collectAsState(initial = "1.1.1.1")
    val fallbackDns by app.container.settingsRepository.fallbackDns.collectAsState(initial = "1.0.0.1")

    var showAuthGateFor by remember { mutableStateOf<String?>(null) }
    var showDnsDialog by remember { mutableStateOf(false) }
    var showPasswordChangeDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showDisableAllDialog by remember { mutableStateOf(false) }

    var exportedJson by remember { mutableStateOf("") }
    var importJsonInput by remember { mutableStateOf("") }

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
                    text = "Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Preferences, security, and administrative actions",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Theme Setting
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Appearance Theme",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("SYSTEM" to "System", "LIGHT" to "Light", "DARK" to "Dark").forEach { (themeKey, label) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                RadioButton(
                                    selected = currentTheme == themeKey,
                                    onClick = {
                                        scope.launch {
                                            app.container.settingsRepository.setAppTheme(themeKey)
                                        }
                                    }
                                )
                                Text(text = label, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            // Upstream DNS Resolver (Authenticated)
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Upstream DNS Resolver",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Current: $primaryDns / $fallbackDns",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(onClick = { showAuthGateFor = "dns" }) {
                        Text("Change")
                    }
                }
            }

            // Security: Change Password (Authenticated)
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Change Master Password",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Update the master password used to authenticate changes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(onClick = { showAuthGateFor = "change_password" }) {
                        Text("Update")
                    }
                }
            }

            // Rule Import / Export (Authenticated)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Backup & Restore Custom Rules",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Export user-added rules to JSON or import rules additively. The adult blocklist is never exported.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showAuthGateFor = "export" },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Export Rules")
                        }
                        OutlinedButton(
                            onClick = { showAuthGateFor = "import" },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Import Rules")
                        }
                    }
                }
            }

            // Danger Zone: Disable Protection & Allow Uninstall (Authenticated)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "⚠️ Danger Zone: Disable & Uninstall",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "Requires master password. Stops the VPN filter and permits opening system settings to deactivate or uninstall.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(
                        onClick = { showAuthGateFor = "disable_all" },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Disable All Protection")
                    }
                }
            }

            // App Version Info
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "LockWeb v1.0.0",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Zero Telemetry • DNS-Only VPN Loopback",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }

    // Auth Gate Dialog when required
    if (showAuthGateFor != null) {
        AuthGateDialog(
            title = "Master Password Required",
            subtitle = "Authenticate to access administrative settings.",
            authManager = app.container.authManager,
            onAuthenticated = {
                val action = showAuthGateFor
                showAuthGateFor = null
                when (action) {
                    "dns" -> showDnsDialog = true
                    "change_password" -> showPasswordChangeDialog = true
                    "export" -> {
                        scope.launch {
                            val rules = app.container.database.blockRuleDao().getAllRulesList()
                            exportedJson = RuleImportExport.exportToJson(rules)
                            showExportDialog = true
                        }
                    }
                    "import" -> showImportDialog = true
                    "disable_all" -> showDisableAllDialog = true
                }
            },
            onDismiss = { showAuthGateFor = null }
        )
    }

    // DNS Change Dialog
    if (showDnsDialog) {
        var newPrimary by remember { mutableStateOf(primaryDns) }
        var newFallback by remember { mutableStateOf(fallbackDns) }

        AlertDialog(
            onDismissRequest = { showDnsDialog = false },
            title = { Text("Upstream DNS Resolver") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Select or enter custom upstream DNS resolver IPv4 addresses:", style = MaterialTheme.typography.bodySmall)

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(onClick = { newPrimary = "1.1.1.1"; newFallback = "1.0.0.1" }, modifier = Modifier.weight(1f)) {
                            Text("Cloudflare", fontSize = 11.sp)
                        }
                        Button(onClick = { newPrimary = "8.8.8.8"; newFallback = "8.8.4.4" }, modifier = Modifier.weight(1f)) {
                            Text("Google", fontSize = 11.sp)
                        }
                        Button(onClick = { newPrimary = "9.9.9.9"; newFallback = "149.112.112.112" }, modifier = Modifier.weight(1f)) {
                            Text("Quad9", fontSize = 11.sp)
                        }
                    }

                    OutlinedTextField(
                        value = newPrimary,
                        onValueChange = { newPrimary = it },
                        label = { Text("Primary DNS") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = newFallback,
                        onValueChange = { newFallback = it },
                        label = { Text("Fallback DNS") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        app.container.settingsRepository.setUpstreamDns(newPrimary, newFallback)
                        showDnsDialog = false
                    }
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDnsDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Export Dialog
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Exported Block Rules JSON") },
            text = {
                Column {
                    Text("Copy your user-defined block rules:", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = exportedJson,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showExportDialog = false }) { Text("Done") }
            }
        )
    }

    // Import Dialog
    if (showImportDialog) {
        var importError by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("Import Block Rules JSON") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Paste JSON containing rules to add (additive only):", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = importJsonInput,
                        onValueChange = { importJsonInput = it; importError = null },
                        placeholder = { Text("{\"version\":1,\"rules\":[...]}", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (importError != null) {
                        Text(importError ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    when (val res = RuleImportExport.importFromJson(importJsonInput)) {
                        is ImportResult.Success -> {
                            scope.launch {
                                app.container.ruleRepository.importRules(res.rulesToAdd)
                                Toast.makeText(context, "Imported ${res.rulesToAdd.size} rules successfully (${res.skippedCount} skipped)", Toast.LENGTH_SHORT).show()
                                showImportDialog = false
                            }
                        }
                        is ImportResult.Error -> {
                            importError = res.reason
                        }
                    }
                }) {
                    Text("Import")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showImportDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Disable All Protection Dialog
    if (showDisableAllDialog) {
        AlertDialog(
            onDismissRequest = { showDisableAllDialog = false },
            title = { Text("Disable Protection?") },
            text = {
                Text(
                    "This will stop the VPN filter and allow 60 seconds of unintercepted access to system settings. Are you sure?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onStopProtection()
                        LockWebDeviceAdminReceiver.removeAdmin(context)
                        app.container.guardSessionManager.grantTemporaryAccess(60_000L)
                        showDisableAllDialog = false
                        Toast.makeText(context, "Protection paused & Admin removed for 60 seconds", Toast.LENGTH_LONG).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Yes, Pause Protection")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDisableAllDialog = false }) { Text("Cancel") }
            }
        )
    }
}
