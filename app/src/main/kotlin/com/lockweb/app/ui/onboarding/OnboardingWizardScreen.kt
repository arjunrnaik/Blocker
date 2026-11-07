package com.lockweb.app.ui.onboarding

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import com.lockweb.app.admin.LockWebDeviceAdminReceiver
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lockweb.app.LockWebApplication
import com.lockweb.app.health.ProtectionHealth
import com.lockweb.app.vpn.VpnStatus
import kotlinx.coroutines.launch

@Composable
fun OnboardingWizardScreen(
    health: ProtectionHealth,
    onRequestVpnConsent: () -> Unit,
    onCompleteOnboarding: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as LockWebApplication
    val scope = rememberCoroutineScope()

    var currentStep by remember { mutableIntStateOf(1) }
    val totalSteps = 6

    // Form state
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf<String?>(null) }

    var recoveryCode by remember { mutableStateOf("") }
    var recoverySavedChecked by remember { mutableStateOf(false) }

    var enableAdultFilter by remember { mutableStateOf(true) }

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Progress
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LockWeb Setup",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Step $currentStep of $totalSteps",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (i in 1..totalSteps) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    if (i <= currentStep) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Step Content
            Box(modifier = Modifier.weight(1f, fill = false)) {
                when (currentStep) {
                    1 -> WelcomeStep()
                    2 -> PasswordCreationStep(
                        password = password,
                        confirmPassword = confirmPassword,
                        passwordVisible = passwordVisible,
                        passwordError = passwordError,
                        onPasswordChange = {
                            password = it
                            passwordError = null
                        },
                        onConfirmPasswordChange = {
                            confirmPassword = it
                            passwordError = null
                        },
                        onToggleVisibility = { passwordVisible = !passwordVisible }
                    )
                    3 -> RecoveryCodeStep(
                        recoveryCode = recoveryCode,
                        isSavedChecked = recoverySavedChecked,
                        onSavedCheckChange = { recoverySavedChecked = it }
                    )
                    4 -> AdultFilterOptInStep(
                        enabled = enableAdultFilter,
                        onToggle = { enableAdultFilter = it }
                    )
                    5 -> ComponentActivationStep(
                        health = health,
                        onRequestVpnConsent = onRequestVpnConsent,
                        onOpenAccessibility = {
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        },
                        onActivateDeviceAdmin = {
                            context.startActivity(LockWebDeviceAdminReceiver.createActivationIntent(context))
                        },
                        onOpenBatteryOpt = {
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
                    )
                    6 -> FinalVerificationStep(
                        health = health,
                        enableAdultFilter = enableAdultFilter
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Navigation Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (currentStep > 1) {
                    OutlinedButton(
                        onClick = { currentStep-- },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Back")
                    }
                }

                Button(
                    onClick = {
                        when (currentStep) {
                            1 -> currentStep++
                            2 -> {
                                if (password.length < 8) {
                                    passwordError = "Password must be at least 8 characters."
                                } else if (password != confirmPassword) {
                                    passwordError = "Passwords do not match."
                                } else {
                                    passwordError = null
                                    // Generate credentials
                                    recoveryCode = app.container.authManager.setupCredentials(password.toCharArray())
                                    currentStep++
                                }
                            }
                            3 -> {
                                if (recoverySavedChecked) {
                                    currentStep++
                                }
                            }
                            4 -> {
                                currentStep++
                            }
                            5 -> {
                                currentStep++
                            }
                            6 -> {
                                scope.launch {
                                    if (enableAdultFilter) {
                                        app.container.settingsRepository.enableAdultBlocklistPermanently()
                                    }
                                    app.container.settingsRepository.completeOnboarding()
                                    onCompleteOnboarding()
                                }
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = when (currentStep) {
                        2 -> password.isNotBlank() && confirmPassword.isNotBlank()
                        3 -> recoverySavedChecked
                        else -> true
                    }
                ) {
                    Text(if (currentStep == totalSteps) "Complete Setup" else "Continue")
                }
            }
        }
    }
}

@Composable
private fun WelcomeStep() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "🛡️ Welcome to LockWeb",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "LockWeb is designed with one purpose: uncompromising self-control.",
            style = MaterialTheme.typography.bodyLarge
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Key Guarantees:",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(text = "• DNS-only local VPN: zero battery drain, zero remote proxying.", style = MaterialTheme.typography.bodyMedium)
                Text(text = "• Zero Logging: No domain names are ever written to disk or sent anywhere.", style = MaterialTheme.typography.bodyMedium)
                Text(text = "• Anti-Tamper Guard: Prevents impulsive disabling in Android Settings.", style = MaterialTheme.typography.bodyMedium)
                Text(text = "• Permanent Adult Filter: Once enabled, it cannot be turned off.", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun PasswordCreationStep(
    password: String,
    confirmPassword: String,
    passwordVisible: Boolean,
    passwordError: String?,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onToggleVisibility: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "Create Master Password",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "This password protects changes to your blocking rules and device settings. Choose a strong password.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = { Text("Master Password") },
            placeholder = { Text("Minimum 8 characters") },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = onToggleVisibility) {
                    Text(if (passwordVisible) "Hide" else "Show", fontSize = 12.sp)
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = confirmPassword,
            onValueChange = onConfirmPasswordChange,
            label = { Text("Confirm Master Password") },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )

        if (passwordError != null) {
            Text(
                text = passwordError,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun RecoveryCodeStep(
    recoveryCode: String,
    isSavedChecked: Boolean,
    onSavedCheckChange: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "Emergency Recovery Code",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "This 160-bit code is your ONLY way to reset your master password if you forget it. It will never be shown again.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = recoveryCode,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        ) {
            Text(
                text = "⚠️ WARNING: Write this code down on paper or save it in a secure password manager. If you lose both your password and this code, the ONLY way to remove LockWeb is a full factory reset of your device.",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Checkbox(
                checked = isSavedChecked,
                onCheckedChange = onSavedCheckChange
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "I have safely written down and stored this recovery code.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun AdultFilterOptInStep(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "Adult Content Filter",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (enabled) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Block Adult Content",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Curated OISD NSFW blocklist (100,000+ domains). Updated weekly via background tasks.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onToggle
                )
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "🔒 Permanent Self-Control Warning",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "Once you enable this filter, it CANNOT be turned off. There is no disable switch in the app, even with the master password. The only way to remove it is a full phone factory reset.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
private fun ComponentActivationStep(
    health: ProtectionHealth,
    onRequestVpnConsent: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onActivateDeviceAdmin: () -> Unit,
    onOpenBatteryOpt: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "Activate Protection Layers",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        // 1. VPN Consent
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("1. DNS Filtering (VPN)", fontWeight = FontWeight.Bold)
                    Text(
                        text = if (health.vpnStatus is VpnStatus.Active) "Active ✅" else "Permission Required",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Button(
                    onClick = onRequestVpnConsent,
                    enabled = health.vpnStatus !is VpnStatus.Active
                ) {
                    Text(if (health.vpnStatus is VpnStatus.Active) "Granted" else "Grant")
                }
            }
        }

        // 2. Anti-Tamper Guard
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("2. Anti-Tamper Guard", fontWeight = FontWeight.Bold)
                    Text(
                        text = if (health.isGuardRunning) "Running ✅" else "Enable in Accessibility Settings",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Button(
                    onClick = onOpenAccessibility,
                    enabled = !health.isGuardRunning
                ) {
                    Text(if (health.isGuardRunning) "Active" else "Enable")
                }
            }
        }

        // 3. Device Administrator (Anti-Uninstall)
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("3. Device Administrator", fontWeight = FontWeight.Bold)
                    Text(
                        text = if (health.isDeviceAdminActive) "Active ✅" else "Blocks app uninstallation without master password.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Button(
                    onClick = onActivateDeviceAdmin,
                    enabled = !health.isDeviceAdminActive
                ) {
                    Text(if (health.isDeviceAdminActive) "Active" else "Activate")
                }
            }
        }

        // 4. Battery Optimization
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("4. Battery Exemption", fontWeight = FontWeight.Bold)
                    Text("Prevent Android from killing the background filter.", style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(onClick = onOpenBatteryOpt) {
                    Text("Exempt")
                }
            }
        }
    }
}

@Composable
private fun FinalVerificationStep(
    health: ProtectionHealth,
    enableAdultFilter: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "Final Verification",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("DNS VPN Engine:")
                    Text(
                        if (health.vpnStatus is VpnStatus.Active) "Active ✅" else "Starting...",
                        fontWeight = FontWeight.Bold
                    )
                }
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Anti-Tamper Guard:")
                    Text(
                        if (health.isGuardRunning) "Running ✅" else "Inactive ⚠️",
                        fontWeight = FontWeight.Bold
                    )
                }
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Device Administrator:")
                    Text(
                        if (health.isDeviceAdminActive) "Active ✅" else "Inactive ⚠️",
                        fontWeight = FontWeight.Bold
                    )
                }
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Adult Content Filter:")
                    Text(
                        if (enableAdultFilter) "Permanent Lock 🔒" else "Disabled",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Text(
            text = "You are now ready to activate full LockWeb protection. Tap Complete Setup to proceed to your Dashboard.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
