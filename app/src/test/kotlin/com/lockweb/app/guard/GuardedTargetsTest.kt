package com.lockweb.app.guard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardedTargetsTest {

    @Test
    fun testAospSettingsTargets_areGuarded() {
        // App Info / Manage Apps
        assertTrue(GuardedTargets.isGuarded("com.android.settings", "com.android.settings.applications.InstalledAppDetails"))
        assertTrue(GuardedTargets.isGuarded("com.android.settings", "com.android.settings.applications.appinfo.AppInfoDashboardFragment"))
        assertTrue(GuardedTargets.isGuarded("com.android.settings", "com.android.settings.Settings\$AppDetailsActivity"))
        assertTrue(GuardedTargets.isGuarded("com.android.settings", "com.android.settings.spa.SpaActivity"))

        // Device Admin
        assertTrue(GuardedTargets.isGuarded("com.android.settings", "com.android.settings.DeviceAdminSettings"))
        assertTrue(GuardedTargets.isGuarded("com.android.settings", "com.android.settings.DeviceAdminAdd"))

        // VPN Settings
        assertTrue(GuardedTargets.isGuarded("com.android.settings", "com.android.settings.VpnSettings"))
        assertTrue(GuardedTargets.isGuarded("com.android.settings", "com.android.settings.vpn2.VpnSettings"))

        // Private DNS Settings
        assertTrue(GuardedTargets.isGuarded("com.android.settings", "com.android.settings.network.PrivateDnsModeDialog"))

        // Accessibility Settings (Self-protection)
        assertTrue(GuardedTargets.isGuarded("com.android.settings", "com.android.settings.accessibility.AccessibilitySettings"))
    }

    @Test
    fun testOemVariants_areGuarded() {
        // Samsung One UI
        assertTrue(GuardedTargets.isGuarded("com.samsung.android.settings", "com.samsung.android.settings.applications.AppInfoDashboardFragment"))
        assertTrue(GuardedTargets.isGuarded("com.samsung.android.settings", "com.samsung.android.settings.vpn.VpnSettings"))
        assertTrue(GuardedTargets.isGuarded("com.samsung.android.settings", "com.samsung.android.settings.accessibility.AccessibilitySettings"))

        // Xiaomi MIUI Security Center
        assertTrue(GuardedTargets.isGuarded("com.miui.securitycenter", "com.miui.appmanager.ApplicationsDetailsActivity"))
        assertTrue(GuardedTargets.isGuarded("com.miui.securitycenter", "com.miui.permcenter.permissions.AppControlActivity"))
    }

    @Test
    fun testChromeAndPlayStore_areGuarded() {
        // Chrome Privacy & Secure DNS
        assertTrue(GuardedTargets.isGuarded("com.android.chrome", "org.chromium.chrome.browser.privacy.settings.PrivacySettings"))
        assertTrue(GuardedTargets.isGuarded("com.android.chrome", "org.chromium.chrome.browser.privacy.settings.SecureDnsSettings"))

        // Play Store App Details
        assertTrue(GuardedTargets.isGuarded("com.android.vending", "com.google.android.finsky.activities.AppDetailsActivity"))
    }

    @Test
    fun testLockWebAppItself_isNeverGuarded() {
        assertFalse(GuardedTargets.isGuarded("com.lockweb.app", "com.lockweb.app.MainActivity"))
        assertFalse(GuardedTargets.isGuarded("com.lockweb.app", "com.lockweb.app.guard.LockWebPasswordGateActivity"))
    }

    @Test
    fun testNormalApps_areNotGuarded() {
        assertFalse(GuardedTargets.isGuarded("com.google.android.calculator", "com.android.calculator2.Calculator"))
        assertFalse(GuardedTargets.isGuarded("org.wikipedia", "org.wikipedia.main.MainActivity"))
        assertFalse(GuardedTargets.isGuarded("com.google.android.dialer", "com.android.dialer.main.impl.MainActivity"))
        assertFalse(GuardedTargets.isGuarded("com.google.android.GoogleCamera", "com.android.camera.CameraActivity"))
        assertFalse(GuardedTargets.isGuarded("com.whatsapp", "com.whatsapp.HomeActivity"))
    }

    @Test
    fun testNullOrBlankInputs_returnFalse() {
        assertFalse(GuardedTargets.isGuarded(null, null))
        assertFalse(GuardedTargets.isGuarded("", ""))
        assertFalse(GuardedTargets.isGuarded("com.android.settings", null))
        assertFalse(GuardedTargets.isGuarded(null, "com.android.settings.VpnSettings"))
    }
}
