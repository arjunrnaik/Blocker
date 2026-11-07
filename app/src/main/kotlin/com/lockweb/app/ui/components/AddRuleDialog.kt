package com.lockweb.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lockweb.corefilter.DomainNormalizer
import com.lockweb.corefilter.ParseResult
import com.lockweb.corefilter.RuleMode

@Composable
fun AddRuleDialog(
    onAddRule: (domain: String, mode: RuleMode, note: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var rawInput by remember { mutableStateOf("") }
    var selectedMode by remember { mutableStateOf(RuleMode.SUBDOMAINS) }
    var note by remember { mutableStateOf("") }

    val parseResult = remember(rawInput) {
        if (rawInput.isBlank()) null else DomainNormalizer.parseInput(rawInput.trim())
    }

    val isValid = parseResult is ParseResult.Success

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Add Block Rule", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Enter a website domain or URL to block:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = rawInput,
                    onValueChange = { rawInput = it },
                    placeholder = { Text("e.g. reddit.com or https://x.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Live Preview & Validation Feedback
                if (parseResult != null) {
                    when (parseResult) {
                        is ParseResult.Success -> {
                            Text(
                                text = "Normalized Host: ${parseResult.normalizedHost}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        is ParseResult.Failure -> {
                            Text(
                                text = "Invalid: ${parseResult.reason}",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Rule Mode:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedMode == RuleMode.SUBDOMAINS,
                        onClick = { selectedMode = RuleMode.SUBDOMAINS }
                    )
                    Text(
                        text = "Include Subdomains (*.domain.com)",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedMode == RuleMode.EXACT,
                        onClick = { selectedMode = RuleMode.EXACT }
                    )
                    Text(
                        text = "Exact Hostname Only",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { if (it.length <= 100) note = it },
                    label = { Text("Optional Note") },
                    placeholder = { Text("e.g. Distracting social media") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (parseResult is ParseResult.Success) {
                        onAddRule(rawInput.trim(), selectedMode, note.takeIf { it.isNotBlank() })
                    }
                },
                enabled = isValid
            ) {
                Text("Add Rule")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}
