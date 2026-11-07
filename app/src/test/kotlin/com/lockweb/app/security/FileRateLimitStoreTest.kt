package com.lockweb.app.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileRateLimitStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testFileRateLimitStore_persistsAcrossInstances() {
        val dir = tempFolder.newFolder("rate_limit_test")
        val store1 = FileRateLimitStore(dir)

        assertEquals(0, store1.getFailedAttempts())
        assertEquals(0L, store1.getLockedUntilMillis())

        store1.saveState(failedAttempts = 5, lockedUntilMillis = 1700000000000L)

        // Instantiate second instance pointing to same file
        val store2 = FileRateLimitStore(dir)
        assertEquals(5, store2.getFailedAttempts())
        assertEquals(1700000000000L, store2.getLockedUntilMillis())

        // Clear
        store2.clear()
        assertEquals(0, store2.getFailedAttempts())
        assertEquals(0L, store2.getLockedUntilMillis())
    }
}
