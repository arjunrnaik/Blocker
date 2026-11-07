package com.lockweb.app.health

import com.lockweb.app.vpn.VpnStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtectionHealthTest {

    @Test
    fun testFullyProtected_whenBothVpnActiveAndGuardRunning() {
        val health = ProtectionHealth(
            vpnStatus = VpnStatus.Active,
            isGuardRunning = true,
            isDeviceAdminActive = true
        )
        assertTrue(health.isFullyProtected)
        assertEquals(OverallProtectionLevel.PROTECTED, health.overallLevel)
    }

    @Test
    fun testDegraded_whenDeviceAdminInactive() {
        val health = ProtectionHealth(
            vpnStatus = VpnStatus.Active,
            isGuardRunning = true,
            isDeviceAdminActive = false
        )
        assertTrue(health.isFullyProtected)
        assertEquals(OverallProtectionLevel.DEGRADED, health.overallLevel)
    }

    @Test
    fun testDegraded_whenVpnActiveButGuardInactive() {
        val health = ProtectionHealth(
            vpnStatus = VpnStatus.Active,
            isGuardRunning = false
        )
        assertFalse(health.isFullyProtected)
        assertEquals(OverallProtectionLevel.DEGRADED, health.overallLevel)
    }

    @Test
    fun testDegraded_whenVpnDegradedEvenIfGuardRunning() {
        val health = ProtectionHealth(
            vpnStatus = VpnStatus.Degraded("Upstream DNS timeout"),
            isGuardRunning = true
        )
        assertFalse(health.isFullyProtected)
        assertEquals(OverallProtectionLevel.DEGRADED, health.overallLevel)
    }

    @Test
    fun testStopped_whenVpnIsIdle() {
        val health = ProtectionHealth(
            vpnStatus = VpnStatus.Idle,
            isGuardRunning = true
        )
        assertFalse(health.isFullyProtected)
        assertEquals(OverallProtectionLevel.STOPPED, health.overallLevel)
    }

    @Test
    fun testError_whenVpnHasError() {
        val health = ProtectionHealth(
            vpnStatus = VpnStatus.Error("TUN interface failed"),
            isGuardRunning = true
        )
        assertFalse(health.isFullyProtected)
        assertEquals(OverallProtectionLevel.ERROR, health.overallLevel)
    }
}
