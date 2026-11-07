package com.lockweb.app.ui.blocklist

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lockweb.app.LockWebApplication
import com.lockweb.app.data.AddRuleResult
import com.lockweb.app.data.BlockRuleEntity
import com.lockweb.app.ui.components.AddRuleDialog
import com.lockweb.app.ui.components.AuthGateDialog
import kotlinx.coroutines.launch

@Composable
fun BlocklistScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as LockWebApplication
    val scope = rememberCoroutineScope()

    val userRules by app.container.ruleRepository.userRules.collectAsState(initial = emptyList())
    val adultEnabled by app.container.settingsRepository.isAdultBlocklistEnabled.collectAsState(initial = false)
    val adultCount by app.container.settingsRepository.lastBlocklistDomainCount.collectAsState(initial = 0)

    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }

    var showAddRuleDialog by remember { mutableStateOf(false) }
    var rulePendingDeletion by remember { mutableStateOf<BlockRuleEntity?>(null) }

    val filteredRules = remember(userRules, searchQuery) {
        if (searchQuery.isBlank()) userRules
        else userRules.filter { it.normalizedHost.contains(searchQuery.trim().lowercase()) }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Blocklists",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Manage blocking rules and adult filter",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(onClick = { showAddRuleDialog = true }) {
                    Text("+ Add")
                }
            }

            // Tabs
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Your Rules (${userRules.size})") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Adult Filter") }
                )
            }

            when (selectedTab) {
                0 -> {
                    // Search bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search your rules...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (filteredRules.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (searchQuery.isBlank()) "No rules yet. Tap '+ Add' above."
                                else "No matching rules found.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredRules, key = { it.id }) { rule ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = rule.normalizedHost,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodyLarge
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Text(
                                                    text = rule.mode,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                if (!rule.note.isNullOrBlank()) {
                                                    Text(
                                                        text = "• ${rule.note}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.outline
                                                    )
                                                }
                                            }
                                        }

                                        // Delete Rule requires authentication
                                        OutlinedButton(
                                            onClick = { rulePendingDeletion = rule },
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                contentColor = MaterialTheme.colorScheme.error
                                            )
                                        ) {
                                            Text("Delete", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> {
                    // Adult Filter View
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (adultEnabled) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Permanent Adult Content Filter",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (adultEnabled) "ACTIVE 🔒" else "INACTIVE",
                                        fontWeight = FontWeight.Bold,
                                        color = if (adultEnabled) MaterialTheme.colorScheme.primary else Color.Gray
                                    )
                                }

                                Text(
                                    text = "Curated OISD NSFW blocklist containing adult, pornographic, and high-risk NSFW domain names.",
                                    style = MaterialTheme.typography.bodyMedium
                                )

                                HorizontalDivider()

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Active Domains in Filter:")
                                    Text(
                                        text = if (adultEnabled) "${if (adultCount > 0) adultCount else "100,000+"} domains" else "0 domains",
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Automatic Updates:")
                                    Text("Weekly (Wi-Fi + Charging)", fontWeight = FontWeight.Medium)
                                }
                            }
                        }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "🔒 Irreversible Self-Control Lock",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "In accordance with LockWeb's core mandate, once this blocklist is enabled, it cannot be disabled even after entering the master password. The only removal vector is a full device factory reset.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Rule Dialog
    if (showAddRuleDialog) {
        AddRuleDialog(
            onAddRule = { domain, mode, note ->
                scope.launch {
                    val result = app.container.ruleRepository.addRule(domain, mode, note)
                    if (result is AddRuleResult.Success) {
                        showAddRuleDialog = false
                    }
                }
            },
            onDismiss = { showAddRuleDialog = false }
        )
    }

    // Delete Rule Auth Gate
    if (rulePendingDeletion != null) {
        AuthGateDialog(
            title = "Delete Block Rule",
            subtitle = "Enter your master password to delete rule for '${rulePendingDeletion?.normalizedHost}'.",
            authManager = app.container.authManager,
            onAuthenticated = {
                val ruleToDelete = rulePendingDeletion
                rulePendingDeletion = null
                if (ruleToDelete != null) {
                    scope.launch {
                        app.container.ruleRepository.deleteRule(ruleToDelete.id)
                    }
                }
            },
            onDismiss = { rulePendingDeletion = null }
        )
    }
}
