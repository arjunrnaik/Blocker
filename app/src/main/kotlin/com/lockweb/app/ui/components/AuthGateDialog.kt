package com.lockweb.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lockweb.app.security.AuthManager
import com.lockweb.app.security.AuthResult
import com.lockweb.app.security.RateLimiter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AuthGateDialog(
    title: String = "Authentication Required",
    subtitle: String = "Enter your master password to proceed with this sensitive action.",
    authManager: AuthManager,
    onAuthenticated: () -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var lockoutSeconds by remember { mutableLongStateOf(0L) }
    var isVerifying by remember { mutableStateOf(false) }

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

    fun submit() {
        if (lockoutSeconds > 0 || isVerifying) return
        if (password.isBlank()) {
            errorMessage = "Password cannot be empty"
            return
        }

        isVerifying = true
        scope.launch(Dispatchers.Default) {
            val res = authManager.verifyPassword(password)
            withContext(Dispatchers.Main) {
                isVerifying = false
                when (res) {
                    is AuthResult.Success -> {
                        onAuthenticated()
                    }
                    is AuthResult.WrongPassword -> {
                        errorMessage = "Incorrect password. ${res.attemptsRemainingBeforeLockout} attempts remaining."
                        password = ""
                    }
                    is AuthResult.LockedOut -> {
                        lockoutSeconds = res.remainingSeconds
                        errorMessage = "Locked out for ${res.remainingSeconds} seconds."
                        password = ""
                    }
                    is AuthResult.Uninitialized -> {
                        // If not yet initialized, allow action
                        onAuthenticated()
                    }
                    is AuthResult.InvalidRecoveryCode -> {}
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = title, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(4.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        if (errorMessage != null && lockoutSeconds == 0L) errorMessage = null
                    },
                    label = { Text("Master Password") },
                    enabled = lockoutSeconds == 0L && !isVerifying,
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Text(if (passwordVisible) "Hide" else "Show", fontSize = 12.sp)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { submit() },
                enabled = lockoutSeconds == 0L && password.isNotBlank() && !isVerifying
            ) {
                Text(if (isVerifying) "Verifying..." else "Confirm")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                enabled = !isVerifying
            ) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}
