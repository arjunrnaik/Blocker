package com.lockweb.app.guard

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardSessionManagerTest {

    @Test
    fun testTemporaryAuthorizationWindow_expiresAfterDuration() {
        var fakeTime = 10_000L
        val manager = GuardSessionManager(clock = { fakeTime })

        // Initially unauthorized
        assertFalse(manager.isTemporarilyAuthorized())
        assertEquals(0L, manager.getRemainingAuthorizedSeconds())

        // Grant 60 seconds access
        manager.grantTemporaryAccess(60_000L)
        assertTrue(manager.isTemporarilyAuthorized())
        assertEquals(60L, manager.getRemainingAuthorizedSeconds())

        // Advance 30 seconds -> still authorized
        fakeTime += 30_000L
        assertTrue(manager.isTemporarilyAuthorized())
        assertEquals(30L, manager.getRemainingAuthorizedSeconds())

        // Advance another 31 seconds (total 61s) -> expired
        fakeTime += 31_000L
        assertFalse(manager.isTemporarilyAuthorized())
        assertEquals(0L, manager.getRemainingAuthorizedSeconds())
    }

    @Test
    fun testRevokeTemporaryAccess_immediatelyRevokes() {
        var fakeTime = 1_000L
        val manager = GuardSessionManager(clock = { fakeTime })

        manager.grantTemporaryAccess(60_000L)
        assertTrue(manager.isTemporarilyAuthorized())

        manager.revokeTemporaryAccess()
        assertFalse(manager.isTemporarilyAuthorized())
    }

    @Test
    fun testGuardRunningFlow_updatesCorrectly() = runBlocking {
        val manager = GuardSessionManager()

        assertFalse(manager.isGuardRunning.first())

        manager.setGuardRunning(true)
        assertTrue(manager.isGuardRunning.first())

        manager.setGuardRunning(false)
        assertFalse(manager.isGuardRunning.first())
    }
}
