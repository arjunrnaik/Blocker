package com.lockweb.app.ui.onboarding

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.lockweb.app.LockWebApplication
import com.lockweb.app.admin.LockWebDeviceAdminReceiver
import com.lockweb.app.health.ProtectionHealth
import com.lockweb.app.ui.theme.StatusError
import com.lockweb.app.ui.theme.StatusProtected
import com.lockweb.app.ui.theme.StatusWarning
import com.lockweb.app.vpn.LockWebVpnService
import com.lockweb.app.vpn.VpnStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun OnboardingWizardScreen(
    health: ProtectionHealth,
    onRequestVpnConsent: () -> Unit,
    onCompleteOnboarding: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val app = context.applicationContext as LockWebApplication
    val scope = rememberCoroutineScope()

    var currentStep by rememberSaveable { mutableIntStateOf(1) }
    val totalSteps = 6

    // Secure sensitive screens (Password Creation & Recovery Code) against screenshots / recordings
    val activity = context as? Activity
    DisposableEffect(currentStep) {
        if (currentStep == 2 || currentStep == 3) {
            activity?.window?.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    // Automatically re-sync system permissions when returning from system setting screens
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                LockWebDeviceAdminReceiver.isActive(context)
                LockWebVpnService.refreshAlwaysOnState(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Form state
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf<String?>(null) }

    var recoveryCode by rememberSaveable { mutableStateOf("") }
    var recoverySavedChecked by rememberSaveable { mutableStateOf(false) }

    var enableAdultFilter by rememberSaveable { mutableStateOf(true) }
    var isProcessing by remember { mutableStateOf(false) }

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
            // Header: Step Progress
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LOCKWEB ONBOARDING",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Step $currentStep of $totalSteps",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
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

            Spacer(modifier = Modifier.height(28.dp))

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
                            if (passwordError != null) passwordError = null
                        },
                        onConfirmPasswordChange = {
                            confirmPassword = it
                            if (passwordError != null) passwordError = null
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

            Spacer(modifier = Modifier.height(28.dp))

            // Navigation Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (currentStep > 1) {
                    OutlinedButton(
                        onClick = { currentStep-- },
                        shape = RoundedCornerShape(12.dp),
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
                                } else if (!isProcessing) {
                                    passwordError = null
                                    isProcessing = true
                                    scope.launch(Dispatchers.Default) {
                                        val code = app.container.authManager.setupCredentials(password.toCharArray())
                                        withContext(Dispatchers.Main) {
                                            recoveryCode = code
                                            isProcessing = false
                                            currentStep++
                                        }
                                    }
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
                    shape = RoundedCornerShape(12.dp),
                    enabled = when (currentStep) {
                        2 -> password.isNotBlank() && confirmPassword.isNotBlank() && !isProcessing
                        3 -> recoverySavedChecked && !isProcessing
                        else -> !isProcessing
                    }
                ) {
                    if (isProcessing) {
                        Text("Securing Key...")
                    } else {
                        Text(if (currentStep == totalSteps) "Complete Setup" else "Continue")
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeStep() {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Autonomous Self-Control",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp
            )
            Text(
                text = "LockWeb is an uncompromising, on-device cyber defense utility designed to eliminate digital distractions and enforce unbreakable web discipline.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )
        }

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Core Architectural Invariants",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                GuaranteeRow(
                    icon = Icons.Rounded.Dns,
                    title = "DNS-Only Loopback",
                    desc = "Zero traffic proxying. Only port 53 DNS queries are filtered on-device."
                )
                GuaranteeRow(
                    icon = Icons.Rounded.PrivacyTip,
                    title = "Absolute Zero-Log Engine",
                    desc = "No queries or hostnames are ever written to storage or transmitted over network."
                )
                GuaranteeRow(
                    icon = Icons.Rounded.Security,
                    title = "Anti-Tamper Shield",
                    desc = "Prevents impulsive bypasses, force-stops, and unauthorized settings changes."
                )
                GuaranteeRow(
                    icon = Icons.Rounded.Lock,
                    title = "Hardware Keystore Guard",
                    desc = "Cryptographic protection backed by Android KeyStore AES-GCM & PBKDF2."
                )
            }
        }
    }
}

@Composable
private fun GuaranteeRow(icon: ImageVector, title: String, desc: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )
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
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Set Master Password",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "Guards access to rule configuration, settings, and bypass mechanisms. Choose a robust passphrase.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = { Text("Master Password") },
            placeholder = { Text("Minimum 8 characters") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = onToggleVisibility) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = if (passwordVisible) "Hide password" else "Show password",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = confirmPassword,
            onValueChange = onConfirmPasswordChange,
            label = { Text("Confirm Master Password") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )

        if (passwordError != null) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = passwordError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun RecoveryCodeStep(
    recoveryCode: String,
    isSavedChecked: Boolean,
    onSavedCheckChange: (Boolean) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Emergency Recovery Key",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "This 160-bit key is the sole mechanism to reset your credentials if forgotten. It is revealed only once.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = recoveryCode,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    letterSpacing = 1.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(recoveryCode))
                        copied = true
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (copied) "Copied to Clipboard" else "Copy Key")
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Rounded.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Store this key in a secure offline location or password manager. If both your password and key are lost, removing LockWeb requires a full device factory reset.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    lineHeight = 17.sp
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Checkbox(
                checked = isSavedChecked,
                onCheckedChange = onSavedCheckChange,
                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "I have safely recorded and stored this recovery key.",
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
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Adult Content Filter",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "Enforces a permanent system-wide block on known adult, explicit, and high-risk domains.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Enable Filter Seal",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (enabled) "Seal will be permanently locked on completion" else "Filter disabled",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
                )
            }
        }

        if (enabled) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Permanent Commitment Notice",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "Once enabled and sealed, this filter CANNOT be turned off. There is no disable toggle in the app or master password override. The only way to remove it is a complete phone factory reset.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            lineHeight = 17.sp
                        )
                    }
                }
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
    val context = LocalContext.current
    val powerManager = remember { context.getSystemService(Context.POWER_SERVICE) as? PowerManager }
    val isBatteryExempt = powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Activate Protection Layers",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "Grant mandatory platform permissions to establish filter and anti-tamper safeguards.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        ActivationCard(
            icon = Icons.Rounded.Dns,
            title = "1. DNS Filtering Engine",
            subtitle = "Local TUN interface required to route port 53 DNS traffic.",
            isActive = health.vpnStatus is VpnStatus.Active,
            activeText = "Active",
            inactiveText = "Permission Required",
            buttonText = "Grant",
            onAction = onRequestVpnConsent
        )

        ActivationCard(
            icon = Icons.Rounded.Security,
            title = "2. Anti-Tamper Guard",
            subtitle = "Accessibility service preventing uninstallation or clearing app storage.",
            isActive = health.isGuardRunning,
            activeText = "Running",
            inactiveText = "Enable in Settings",
            buttonText = "Enable",
            onAction = onOpenAccessibility
        )

        ActivationCard(
            icon = Icons.Rounded.AdminPanelSettings,
            title = "3. Device Administrator",
            subtitle = "Blocks direct launcher uninstallation without master password.",
            isActive = health.isDeviceAdminActive,
            activeText = "Active",
            inactiveText = "Activate Protection",
            buttonText = "Activate",
            onAction = onActivateDeviceAdmin
        )

        ActivationCard(
            icon = Icons.Rounded.BatterySaver,
            title = "4. Battery Exemption",
            subtitle = "Exempts LockWeb from Android battery hibernation.",
            isActive = isBatteryExempt,
            activeText = "Exempt",
            inactiveText = "Request Exemption",
            buttonText = "Exempt",
            onAction = onOpenBatteryOpt
        )
    }
}

@Composable
private fun ActivationCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isActive: Boolean,
    activeText: String,
    inactiveText: String,
    buttonText: String,
    onAction: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (isActive) StatusProtected.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isActive) StatusProtected else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isActive) activeText else inactiveText,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isActive) StatusProtected else StatusWarning,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (!isActive) {
                FilledTonalButton(
                    onClick = onAction,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(buttonText)
                }
            } else {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = StatusProtected,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun FinalVerificationStep(
    health: ProtectionHealth,
    enableAdultFilter: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "System Verification",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "Confirm the initial security state before activating continuous protection.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                VerificationRow(
                    label = "DNS VPN Engine",
                    status = if (health.vpnStatus is VpnStatus.Active) "Ready" else "Pending Consent",
                    isGood = health.vpnStatus is VpnStatus.Active
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                VerificationRow(
                    label = "Anti-Tamper Guard",
                    status = if (health.isGuardRunning) "Running" else "Not Configured",
                    isGood = health.isGuardRunning
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                VerificationRow(
                    label = "Device Administrator",
                    status = if (health.isDeviceAdminActive) "Active" else "Inactive",
                    isGood = health.isDeviceAdminActive
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                VerificationRow(
                    label = "Adult Content Filter",
                    status = if (enableAdultFilter) "Permanent Seal Locked" else "Disabled",
                    isGood = true
                )
            }
        }

        Text(
            text = "Tap Complete Setup to lock your initial configuration and launch the live defense dashboard.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun VerificationRow(label: String, status: String, isGood: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = (if (isGood) StatusProtected else StatusWarning).copy(alpha = 0.12f)
        ) {
            Text(
                text = status,
                style = MaterialTheme.typography.labelSmall,
                color = if (isGood) StatusProtected else StatusWarning,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}
