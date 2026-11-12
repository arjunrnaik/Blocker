package com.lockweb.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon
) {
    data object Onboarding : Screen(
        route = "onboarding",
        title = "Setup",
        icon = Icons.Rounded.RocketLaunch
    )
    data object Dashboard : Screen(
        route = "dashboard",
        title = "Dashboard",
        icon = Icons.Outlined.Shield,
        selectedIcon = Icons.Rounded.Shield
    )
    data object Blocklist : Screen(
        route = "blocklist",
        title = "Blocklist",
        icon = Icons.Outlined.Block,
        selectedIcon = Icons.Rounded.Block
    )
    data object Status : Screen(
        route = "status",
        title = "Protection",
        icon = Icons.Outlined.HealthAndSafety,
        selectedIcon = Icons.Rounded.HealthAndSafety
    )
    data object Settings : Screen(
        route = "settings",
        title = "Settings",
        icon = Icons.Outlined.Settings,
        selectedIcon = Icons.Rounded.Settings
    )
}
