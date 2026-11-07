package com.lockweb.app.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RateLimiterTest {

    private val testClock = object : SecurityClock {
        var currentTime = 100_000L
        override fun currentTimeMillis(): Long = currentTime
    }

    @Test
    fun rateLimiter_progressionAndLockoutDelays() {
        val store = InMemoryRateLimitStore()
        val limiter = RateLimiter(store, testClock)

        // Attempts 1 to 4 should give 0 lockout delay
        for (i in 1..4) {
            val status = limiter.checkStatus()
            assertTrue(status is RateLimiter.Status.Allowed)
            val delay = limiter.recordFailure()
            assertEquals("Attempt $i should have 0 delay", 0L, delay)
        }

        // 5th failure: 30 seconds delay
        val delay5 = limiter.recordFailure()
        assertEquals(30L, delay5)
        val status5 = limiter.checkStatus()
        assertTrue(status5 is RateLimiter.Status.LockedOut)
        assertEquals(30L, (status5 as RateLimiter.Status.LockedOut).remainingSeconds)

        // Advance clock by 15 seconds -> still locked out
        testClock.currentTime += 15_000L
        val status5Mid = limiter.checkStatus()
        assertTrue(status5Mid is RateLimiter.Status.LockedOut)
        assertEquals(15L, (status5Mid as RateLimiter.Status.LockedOut).remainingSeconds)

        // Advance clock past 30 seconds -> allowed again
        testClock.currentTime += 16_000L
        val status5After = limiter.checkStatus()
        assertTrue(status5After is RateLimiter.Status.Allowed)

        // 6th failure: 120 seconds (2 minutes)
        val delay6 = limiter.recordFailure()
        assertEquals(120L, delay6)

        // Advance past delay6
        testClock.currentTime += 121_000L

        // 7th failure: 900 seconds (15 minutes)
        val delay7 = limiter.recordFailure()
        assertEquals(900L, delay7)

        // Advance past delay7
        testClock.currentTime += 901_000L

        // 8th failure: 3600 seconds (1 hour)
        val delay8 = limiter.recordFailure()
        assertEquals(3600L, delay8)
    }

    @Test
    fun rateLimiter_statePersistsAcrossInstances() {
        val store = InMemoryRateLimitStore()
        val limiter1 = RateLimiter(store, testClock)

        // Record 5 failures
        for (i in 1..5) {
            limiter1.recordFailure()
        }

        // Create new limiter instance pointing to the same persistent store
        val limiter2 = RateLimiter(store, testClock)
        val status = limiter2.checkStatus()
        assertTrue("State must survive across limiter reinstantiation", status is RateLimiter.Status.LockedOut)
        assertEquals(30L, (status as RateLimiter.Status.LockedOut).remainingSeconds)
    }

    @Test
    fun recordSuccess_resetsFailureCountAndLockout() {
        val store = InMemoryRateLimitStore()
        val limiter = RateLimiter(store, testClock)

        for (i in 1..5) {
            limiter.recordFailure()
        }
        assertTrue(limiter.checkStatus() is RateLimiter.Status.LockedOut)

        limiter.recordSuccess()
        assertEquals(0, store.getFailedAttempts())
        assertEquals(0L, store.getLockedUntilMillis())
        assertTrue(limiter.checkStatus() is RateLimiter.Status.Allowed)
    }
}
