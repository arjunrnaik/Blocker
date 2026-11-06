package com.lockweb.app.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnStatusTest {

    @Test
    fun idleStatus_isCorrectObject() {
        val status: VpnStatus = VpnStatus.Idle
        assertEquals(VpnStatus.Idle, status)
    }

    @Test
    fun activeStatus_isCorrectObject() {
        val status: VpnStatus = VpnStatus.Active
        assertEquals(VpnStatus.Active, status)
    }

    @Test
    fun degradedStatus_storesReasonCorrectly() {
        val status = VpnStatus.Degraded("Upstream DNS unreachable")
        assertEquals("Upstream DNS unreachable", status.reason)
        assertEquals(VpnStatus.Degraded("Upstream DNS unreachable"), status)
        assertNotEquals(VpnStatus.Degraded("Different reason"), status)
    }

    @Test
    fun errorStatus_storesReasonCorrectly() {
        val status = VpnStatus.Error("TUN interface failed")
        assertEquals("TUN interface failed", status.reason)
        assertEquals(VpnStatus.Error("TUN interface failed"), status)
    }

    @Test
    fun statusHierarchy_coversAllBranchesExhaustively() {
        val statuses: List<VpnStatus> = listOf(
            VpnStatus.Idle,
            VpnStatus.Starting,
            VpnStatus.Active,
            VpnStatus.Paused,
            VpnStatus.PermissionRequired,
            VpnStatus.Degraded("test"),
            VpnStatus.Error("test")
        )

        for (status in statuses) {
            val label = when (status) {
                is VpnStatus.Idle -> "Idle"
                is VpnStatus.Starting -> "Starting"
                is VpnStatus.Active -> "Active"
                is VpnStatus.Paused -> "Paused"
                is VpnStatus.PermissionRequired -> "PermissionRequired"
                is VpnStatus.Degraded -> "Degraded: ${status.reason}"
                is VpnStatus.Error -> "Error: ${status.reason}"
            }
            assertTrue(label.isNotEmpty())
        }
    }
}
