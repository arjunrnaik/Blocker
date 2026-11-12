package com.lockweb.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.lockweb.app.LockWebApplication
import com.lockweb.app.health.HealthMonitor
import com.lockweb.app.health.ProtectionHealth
import com.lockweb.app.ui.blocklist.BlocklistScreen
import com.lockweb.app.ui.dashboard.DashboardScreen
import com.lockweb.app.ui.navigation.Screen
import com.lockweb.app.ui.onboarding.OnboardingWizardScreen
import com.lockweb.app.ui.settings.SettingsScreen
import com.lockweb.app.ui.status.ProtectionStatusScreen
import com.lockweb.app.vpn.LockWebVpnService

@Composable
fun LockWebApp(
    onStartVpn: () -> Unit,
    onStopVpn: () -> Unit,
    onRequestVpnConsent: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as LockWebApplication

    val isOnboardingCompleted by app.container.settingsRepository.isOnboardingCompleted.collectAsState(initial = true)
    val isAuthInitialized = remember { app.container.authManager.isInitialized() }

    val status by LockWebVpnService.status.collectAsState()
    val blockedCount by LockWebVpnService.blockedCount.collectAsState()
    val guardRunning by app.container.guardSessionManager.isGuardRunning.collectAsState()

    val health by HealthMonitor.observeProtectionHealth().collectAsState(
        initial = ProtectionHealth(vpnStatus = status, isGuardRunning = guardRunning)
    )

    var currentScreen by remember { mutableStateOf<Screen>(Screen.Dashboard) }

    // If onboarding was never completed and auth is not initialized, show onboarding wizard
    if (!isOnboardingCompleted && !isAuthInitialized) {
        OnboardingWizardScreen(
            health = health,
            onRequestVpnConsent = onRequestVpnConsent,
            onCompleteOnboarding = {
                currentScreen = Screen.Dashboard
                onStartVpn()
            }
        )
    } else {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    listOf(
                        Screen.Dashboard,
                        Screen.Blocklist,
                        Screen.Status,
                        Screen.Settings
                    ).forEach { screen ->
                        val selected = currentScreen.route == screen.route
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = if (selected) screen.selectedIcon else screen.icon,
                                    contentDescription = screen.title
                                )
                            },
                            label = {
                                Text(
                                    text = screen.title,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            },
                            selected = selected,
                            onClick = { currentScreen = screen }
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (currentScreen) {
                    Screen.Dashboard -> DashboardScreen(
                        health = health,
                        blockedCount = blockedCount,
                        onStartVpn = onStartVpn,
                        onStopVpn = onStopVpn,
                        onNavigateToBlocklist = { currentScreen = Screen.Blocklist },
                        onNavigateToStatus = { currentScreen = Screen.Status }
                    )
                    Screen.Blocklist -> BlocklistScreen()
                    Screen.Status -> ProtectionStatusScreen(
                        health = health,
                        onStartVpn = onStartVpn,
                        onStopVpn = onStopVpn
                    )
                    Screen.Settings -> SettingsScreen(
                        onStopProtection = onStopVpn
                    )
                    Screen.Onboarding -> OnboardingWizardScreen(
                        health = health,
                        onRequestVpnConsent = onRequestVpnConsent,
                        onCompleteOnboarding = { currentScreen = Screen.Dashboard }
                    )
                }
            }
        }
    }
}
