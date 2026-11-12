package com.lockweb.app.ui.settings

import android.widget.Toast
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.lockweb.app.BuildConfig
import com.lockweb.app.LockWebApplication
import com.lockweb.app.admin.LockWebDeviceAdminReceiver
import com.lockweb.app.blocklist.ImportResult
import com.lockweb.app.blocklist.RuleImportExport
import com.lockweb.app.ui.components.AuthGateDialog
import com.lockweb.app.ui.theme.StatusError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    var newRecoveryCodeToDisplay by remember { mutableStateOf<String?>(null) }

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
                    text = "Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = "Preferences, security, and administrative actions",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Theme Setting
            SettingsGroup(title = "Appearance") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemeOptionPill(
                        label = "System",
                        icon = Icons.Rounded.BrightnessAuto,
                        selected = currentTheme == "SYSTEM",
                        onClick = {
                            scope.launch { app.container.settingsRepository.setAppTheme("SYSTEM") }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    ThemeOptionPill(
                        label = "Light",
                        icon = Icons.Rounded.LightMode,
                        selected = currentTheme == "LIGHT",
                        onClick = {
                            scope.launch { app.container.settingsRepository.setAppTheme("LIGHT") }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    ThemeOptionPill(
                        label = "Dark",
                        icon = Icons.Rounded.DarkMode,
                        selected = currentTheme == "DARK",
                        onClick = {
                            scope.launch { app.container.settingsRepository.setAppTheme("DARK") }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Network & DNS Settings
            SettingsGroup(title = "Network & Security") {
                SettingActionRow(
                    icon = Icons.Rounded.Dns,
                    title = "Upstream DNS Resolver",
                    subtitle = "$primaryDns (Primary) • $fallbackDns (Fallback)",
                    buttonText = "Change",
                    onClick = { showAuthGateFor = "dns" }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                SettingActionRow(
                    icon = Icons.Rounded.Key,
                    title = "Master Password",
                    subtitle = "Protects app configuration and security settings",
                    buttonText = "Update",
                    onClick = { showAuthGateFor = "change_password" }
                )
            }

            // Rule Import / Export
            SettingsGroup(title = "Backup & Data") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.Backup,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Rule Backup & Transfer",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Export user-defined rules to JSON or import additively.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showAuthGateFor = "export" },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Export JSON")
                        }
                        OutlinedButton(
                            onClick = { showAuthGateFor = "import" },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Import JSON")
                        }
                    }
                }
            }

            // Maintenance / Admin Action
            SettingsGroup(title = "Maintenance Window") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = StatusError.copy(alpha = 0.15f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.Warning,
                                    contentDescription = null,
                                    tint = StatusError,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Pause All Defense Layers",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = StatusError
                            )
                            Text(
                                text = "Requires master password. Grants a temporary 60-second administrative window to configure or remove components.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Button(
                        onClick = { showAuthGateFor = "disable_all" },
                        colors = ButtonDefaults.buttonColors(containerColor = StatusError),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Unlock 60-Second Admin Window", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Footer
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "LockWeb v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Local DNS Loopback • Zero Telemetry",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }

    // Auth Gate Dialog
    if (showAuthGateFor != null) {
        AuthGateDialog(
            title = "Master Key Required",
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
            title = { Text("Upstream DNS Resolver", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Select a provider preset or enter custom IPv4 DNS resolver addresses:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilledTonalButton(
                            onClick = { newPrimary = "1.1.1.1"; newFallback = "1.0.0.1" },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cloudflare", fontSize = 11.sp)
                        }
                        FilledTonalButton(
                            onClick = { newPrimary = "8.8.8.8"; newFallback = "8.8.4.4" },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Google", fontSize = 11.sp)
                        }
                        FilledTonalButton(
                            onClick = { newPrimary = "9.9.9.9"; newFallback = "149.112.112.112" },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Quad9", fontSize = 11.sp)
                        }
                    }

                    OutlinedTextField(
                        value = newPrimary,
                        onValueChange = { newPrimary = it },
                        label = { Text("Primary DNS") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newFallback,
                        onValueChange = { newFallback = it },
                        label = { Text("Fallback DNS") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
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

    // Change Password Dialog (H-5)
    if (showPasswordChangeDialog) {
        var newPassword by remember { mutableStateOf("") }
        var confirmNewPassword by remember { mutableStateOf("") }
        var passwordVisible by remember { mutableStateOf(false) }
        var changePasswordError by remember { mutableStateOf<String?>(null) }
        var isChanging by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isChanging) showPasswordChangeDialog = false },
            properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
            title = { Text("Change Master Password", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Enter a new master password (minimum 8 characters). A new emergency recovery code will be generated.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = {
                            newPassword = it
                            changePasswordError = null
                        },
                        label = { Text("New Password") },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Text(if (passwordVisible) "Hide" else "Show", fontSize = 12.sp)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = confirmNewPassword,
                        onValueChange = {
                            confirmNewPassword = it
                            changePasswordError = null
                        },
                        label = { Text("Confirm New Password") },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (changePasswordError != null) {
                        Text(
                            text = changePasswordError ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPassword.length < 8) {
                            changePasswordError = "Password must be at least 8 characters"
                        } else if (newPassword != confirmNewPassword) {
                            changePasswordError = "Passwords do not match"
                        } else if (!isChanging) {
                            isChanging = true
                            scope.launch(Dispatchers.Default) {
                                val generatedCode = app.container.authManager.setupCredentials(newPassword.toCharArray())
                                withContext(Dispatchers.Main) {
                                    isChanging = false
                                    showPasswordChangeDialog = false
                                    newRecoveryCodeToDisplay = generatedCode
                                }
                            }
                        }
                    },
                    enabled = newPassword.isNotBlank() && confirmNewPassword.isNotBlank() && !isChanging
                ) {
                    Text(if (isChanging) "Updating..." else "Update Password")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showPasswordChangeDialog = false },
                    enabled = !isChanging
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Display New Recovery Code Dialog after password change
    if (newRecoveryCodeToDisplay != null) {
        val codeToRecord = newRecoveryCodeToDisplay ?: ""
        AlertDialog(
            onDismissRequest = { /* user must acknowledge */ },
            properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
            title = { Text("New Recovery Key", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Your master password has been updated. Below is your NEW 160-bit emergency recovery key. Record it in a secure location.",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = codeToRecord,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Text(
                        "Your old recovery key is now deactivated.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        newRecoveryCodeToDisplay = null
                        Toast.makeText(context, "Password updated successfully", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("I Have Saved This Key")
                }
            }
        )
    }

    // Export Dialog
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Exported Rules JSON", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Copy your user-defined block rules:", style = MaterialTheme.typography.bodySmall)
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
            title = { Text("Import Rules JSON", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Paste JSON containing rules to add (additive only):", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = importJsonInput,
                        onValueChange = { importJsonInput = it; importError = null },
                        placeholder = { Text("{\"version\":1,\"rules\":[...]}") },
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
                                Toast.makeText(context, "Imported ${res.rulesToAdd.size} rules (${res.skippedCount} skipped)", Toast.LENGTH_SHORT).show()
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
            title = { Text("Grant Admin Window?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This temporarily stops the local DNS filter and allows 60 seconds of unintercepted access to system settings. After 60 seconds, anti-tamper enforcement automatically re-engages.",
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
                        Toast.makeText(context, "60-second administrative window active", Toast.LENGTH_LONG).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Pause Protection")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDisableAllDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SettingsGroup(
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
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
private fun ThemeOptionPill(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    buttonText: String,
    onClick: () -> Unit
) {
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
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
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

        FilledTonalButton(onClick = onClick) {
            Text(buttonText)
        }
    }
}
