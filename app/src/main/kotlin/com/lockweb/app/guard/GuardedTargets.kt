package com.lockweb.app.guard

/**
 * Represents a rule that matches guarded Android activities.
 */
data class GuardRule(
    val id: String,
    val description: String,
    val targetPackage: String,
    val classPatterns: List<Regex>
)

/**
 * Defines the guarded targets that could weaken, bypass, or uninstall LockWeb.
 * In accordance with MASTER_PROMPT.md Section 7.2.
 */
object GuardedTargets {

    val RULES: List<GuardRule> = listOf(
        // 1. LockWeb's App Info page in Android Settings (Force Stop, Clear Data, Uninstall)
        GuardRule(
            id = "app_info_aosp",
            description = "Android Settings App Info / Applications Management",
            targetPackage = "com.android.settings",
            classPatterns = listOf(
                Regex(".*InstalledAppDetails.*", RegexOption.IGNORE_CASE),
                Regex(".*AppInfo.*", RegexOption.IGNORE_CASE),
                Regex(".*AppDetails.*", RegexOption.IGNORE_CASE),
                Regex(".*ManageApplications.*", RegexOption.IGNORE_CASE),
                Regex(".*spa.*SpaActivity.*", RegexOption.IGNORE_CASE) // Android 14+ Compose Settings
            )
        ),

        // 2. Device Admin settings screen
        GuardRule(
            id = "device_admin_aosp",
            description = "Android Settings Device Admin",
            targetPackage = "com.android.settings",
            classPatterns = listOf(
                Regex(".*DeviceAdmin.*", RegexOption.IGNORE_CASE)
            )
        ),

        // 3. VPN settings screen
        GuardRule(
            id = "vpn_settings_aosp",
            description = "Android Settings VPN preferences and disconnection",
            targetPackage = "com.android.settings",
            classPatterns = listOf(
                Regex(".*VpnSettings.*", RegexOption.IGNORE_CASE),
                Regex(".*vpn2.*", RegexOption.IGNORE_CASE)
            )
        ),

        // 4. Private DNS settings screen
        GuardRule(
            id = "private_dns_aosp",
            description = "Android Settings Private DNS Mode Dialog",
            targetPackage = "com.android.settings",
            classPatterns = listOf(
                Regex(".*PrivateDns.*", RegexOption.IGNORE_CASE)
            )
        ),

        // 5. Accessibility settings screen (Self-protection against guard disabling)
        GuardRule(
            id = "accessibility_settings_aosp",
            description = "Android Settings Accessibility Services",
            targetPackage = "com.android.settings",
            classPatterns = listOf(
                Regex(".*AccessibilitySettings.*", RegexOption.IGNORE_CASE),
                Regex(".*AccessibilityPicker.*", RegexOption.IGNORE_CASE)
            )
        ),

        // Samsung One UI variants
        GuardRule(
            id = "samsung_settings",
            description = "Samsung One UI Settings Guard",
            targetPackage = "com.samsung.android.settings",
            classPatterns = listOf(
                Regex(".*AppInfo.*", RegexOption.IGNORE_CASE),
                Regex(".*InstalledAppDetails.*", RegexOption.IGNORE_CASE),
                Regex(".*DeviceAdmin.*", RegexOption.IGNORE_CASE),
                Regex(".*Vpn.*", RegexOption.IGNORE_CASE),
                Regex(".*PrivateDns.*", RegexOption.IGNORE_CASE),
                Regex(".*Accessibility.*", RegexOption.IGNORE_CASE)
            )
        ),

        // Xiaomi MIUI Security Center variants
        GuardRule(
            id = "miui_securitycenter",
            description = "Xiaomi MIUI Security Center / App Manager",
            targetPackage = "com.miui.securitycenter",
            classPatterns = listOf(
                Regex(".*InstalledAppDetails.*", RegexOption.IGNORE_CASE),
                Regex(".*ApplicationsDetails.*", RegexOption.IGNORE_CASE),
                Regex(".*AppControl.*", RegexOption.IGNORE_CASE)
            )
        ),

        // 6. Google Play Store LockWeb app detail / uninstall
        GuardRule(
            id = "play_store",
            description = "Google Play Store App Details & Uninstall Dialog",
            targetPackage = "com.android.vending",
            classPatterns = listOf(
                Regex(".*AppDetails.*", RegexOption.IGNORE_CASE),
                Regex(".*DetailsSummary.*", RegexOption.IGNORE_CASE),
                Regex(".*Uninstall.*", RegexOption.IGNORE_CASE)
            )
        ),

        // 7. Google Chrome Secure DNS / Privacy settings
        GuardRule(
            id = "chrome_privacy",
            description = "Google Chrome Privacy & Secure DNS settings",
            targetPackage = "com.android.chrome",
            classPatterns = listOf(
                Regex(".*PrivacySettings.*", RegexOption.IGNORE_CASE),
                Regex(".*SecureDns.*", RegexOption.IGNORE_CASE)
            )
        )
    )

    /**
     * Determines whether the given package name and class name represents a guarded target.
     * Never guards our own application or general non-targeted apps.
     */
    fun isGuarded(packageName: CharSequence?, className: CharSequence?): Boolean {
        if (packageName.isNullOrBlank() || className.isNullOrBlank()) return false
        val pkgStr = packageName.toString()
        val clsStr = className.toString()

        // Never intercept LockWeb itself
        if (pkgStr == "com.lockweb.app") return false

        for (rule in RULES) {
            if (rule.targetPackage.equals(pkgStr, ignoreCase = true)) {
                if (rule.classPatterns.any { it.matches(clsStr) }) {
                    return true
                }
            }
        }
        return false
    }
}
