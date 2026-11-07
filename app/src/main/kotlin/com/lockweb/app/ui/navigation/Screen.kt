package com.lockweb.app.ui.navigation

sealed class Screen(val route: String, val title: String, val icon: String) {
    data object Onboarding : Screen("onboarding", "Setup", "🚀")
    data object Dashboard : Screen("dashboard", "Dashboard", "🛡️")
    data object Blocklist : Screen("blocklist", "Blocklist", "🚫")
    data object Status : Screen("status", "Protection", "📊")
    data object Settings : Screen("settings", "Settings", "⚙️")
}
