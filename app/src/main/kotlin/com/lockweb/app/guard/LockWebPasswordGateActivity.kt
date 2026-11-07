package com.lockweb.app.guard

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lockweb.app.LockWebApplication
import com.lockweb.app.security.AuthResult
import com.lockweb.app.security.RateLimiter
import kotlinx.coroutines.delay

class LockWebPasswordGateActivity : ComponentActivity() {

    companion object {
        const val EXTRA_TARGET_PACKAGE = "extra_target_package"
        const val EXTRA_TARGET_CLASS = "extra_target_class"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Mandatory FLAG_SECURE to prevent screenshots and screen scraping
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        enableEdgeToEdge()

        // Handle back press: dismissing password gate MUST return the user to Home screen
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                sendHomeAndFinish()
            }
        })

        val targetPkg = intent.getStringExtra(EXTRA_TARGET_PACKAGE) ?: "Settings"

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PasswordGateScreen(
                        targetPackage = targetPkg,
                        onUnlockSuccess = {
                            // Grant 60-second window and proceed to target
                            GuardSessionManager.instance.grantTemporaryAccess(60_000L)
                            finish()
                        },
                        onCancel = {
                            sendHomeAndFinish()
                        }
                    )
                }
            }
        }
    }

    private fun sendHomeAndFinish() {
        val sent = AccessibilityGuardService.instance?.sendHome() ?: false
        if (!sent) {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(homeIntent)
        }
        finish()
    }
}

@Composable
fun PasswordGateScreen(
    targetPackage: String,
    onUnlockSuccess: () -> Unit,
    onCancel: () -> Unit
) {
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as LockWebApplication
    val authManager = app.container.authManager

    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var lockoutSeconds by remember { mutableLongStateOf(0L) }

    // Check rate limit on start
    fun refreshLockout() {
        when (val status = authManager.checkRateLimit()) {
            is RateLimiter.Status.LockedOut -> {
                lockoutSeconds = status.remainingSeconds
                errorMessage = "Too many failed attempts. Locked out for $lockoutSeconds seconds."
            }
            is RateLimiter.Status.Allowed -> {
                lockoutSeconds = 0L
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshLockout()
    }

    // Countdown timer during lockout
    LaunchedEffect(lockoutSeconds) {
        if (lockoutSeconds > 0) {
            delay(1000L)
            lockoutSeconds--
            if (lockoutSeconds == 0L) {
                errorMessage = null
            } else {
                errorMessage = "Too many failed attempts. Locked out for $lockoutSeconds seconds."
            }
        }
    }

    fun submitPassword() {
        if (lockoutSeconds > 0) return
        if (password.isBlank()) {
            errorMessage = "Password cannot be empty"
            return
        }

        when (val result = authManager.verifyPassword(password)) {
            is AuthResult.Success -> {
                onUnlockSuccess()
            }
            is AuthResult.WrongPassword -> {
                errorMessage = "Incorrect password. ${result.attemptsRemainingBeforeLockout} attempts remaining before lockout."
                password = ""
            }
            is AuthResult.LockedOut -> {
                lockoutSeconds = result.remainingSeconds
                errorMessage = "Locked out for ${result.remainingSeconds} seconds."
                password = ""
            }
            is AuthResult.Uninitialized -> {
                // If no password was created yet, permit temporary setup access
                onUnlockSuccess()
            }
            is AuthResult.InvalidRecoveryCode -> {}
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "🔒",
                        fontSize = 44.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "LockWeb Security Gate",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Guarded system settings were accessed.\nEnter your master password to unlock a 60-second administrative session.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            if (errorMessage != null && lockoutSeconds == 0L) {
                                errorMessage = null
                            }
                        },
                        label = { Text("Master Password") },
                        enabled = lockoutSeconds == 0L,
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { submitPassword() }
                        ),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Text(if (passwordVisible) "Hide" else "Show", fontSize = 12.sp)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = errorMessage ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel (Home)")
                        }

                        Button(
                            onClick = { submitPassword() },
                            enabled = lockoutSeconds == 0L && password.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Unlock")
                        }
                    }
                }
            }
        }
    }
}
