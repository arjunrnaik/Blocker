package com.lockweb.app.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class PasswordSecurityTest {

    private val testClock = object : SecurityClock {
        var time = 1_000_000L
        override fun currentTimeMillis(): Long = time
    }

    @Test
    fun hashingAndVerification_succeedsWithCorrectPassword() {
        val hasher = PasswordHasher(iterations = 10_000) // Lower iterations for fast unit test
        val password = "SuperSecretPassword123!".toCharArray()

        val record = hasher.hashPassword(password)
        assertEquals(PasswordHasher.SALT_LENGTH_BYTES, record.salt.size)
        assertEquals(PasswordHasher.HASH_LENGTH_BYTES, record.hash.size)
        assertEquals(10_000, record.iterations)

        assertTrue(hasher.verifyPassword(password, record))
    }

    @Test
    fun verification_failsWithWrongPassword() {
        val hasher = PasswordHasher(iterations = 10_000)
        val password = "CorrectPassword".toCharArray()
        val wrongPassword = "WrongPassword".toCharArray()

        val record = hasher.hashPassword(password)
        assertFalse(hasher.verifyPassword(wrongPassword, record))
    }

    @Test
    fun hashRecord_serializationRoundtrip() {
        val hasher = PasswordHasher(iterations = 10_000)
        val originalRecord = hasher.hashPassword("TestPass".toCharArray())

        val serialized = originalRecord.toByteArray()
        assertEquals(52, serialized.size)

        val deserialized = PasswordHasher.HashRecord.fromByteArray(serialized)
        assertEquals(originalRecord, deserialized)
    }

    @Test
    fun aesGcmVault_encryptsAndDecryptsCorrectly() {
        val vault = JvmAesGcmVault(JvmAesGcmVault.generateTestKey())
        val plaintext = "SensitiveCredentialBlobData".toByteArray(Charsets.UTF_8)

        val encrypted = vault.encrypt(plaintext)
        assertNotEquals(plaintext.toList(), encrypted.toList())

        val decrypted = vault.decrypt(encrypted)
        assertEquals(String(plaintext), String(decrypted))
    }

    @Test
    fun authManager_fullLifecycle() {
        val tempDir = Files.createTempDirectory("auth_test").toFile()
        try {
            val hasher = PasswordHasher(iterations = 5_000)
            val vault = JvmAesGcmVault(JvmAesGcmVault.generateTestKey())
            val storage = FileCredentialStorage(tempDir)
            val rateLimiter = RateLimiter(InMemoryRateLimitStore(), testClock)
            val recoveryManager = RecoveryCodeManager(hasher)

            val authManager = AuthManager(
                passwordHasher = hasher,
                keyStoreVault = vault,
                storage = storage,
                rateLimiter = rateLimiter,
                recoveryCodeManager = recoveryManager,
                clock = testClock
            )

            assertFalse(authManager.isInitialized())
            assertEquals(AuthResult.Uninitialized, authManager.authenticate("test".toCharArray()))

            // Setup
            val recoveryCode = authManager.setupCredentials("MasterPass123".toCharArray())
            assertTrue(authManager.isInitialized())
            assertFalse(authManager.isAuthenticated())
            assertTrue(recoveryCode.isNotEmpty())

            // Failed authentication
            val failResult = authManager.authenticate("WrongPass".toCharArray())
            assertTrue(failResult is AuthResult.WrongPassword)
            assertFalse(authManager.isAuthenticated())

            // Successful authentication
            val successResult = authManager.authenticate("MasterPass123".toCharArray())
            assertEquals(AuthResult.Success, successResult)
            assertTrue(authManager.isAuthenticated())

            // Advance time past session expiration (60 seconds)
            testClock.time += 61_000L
            assertFalse(authManager.isAuthenticated())
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
