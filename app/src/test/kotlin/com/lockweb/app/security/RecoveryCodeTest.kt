package com.lockweb.app.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class RecoveryCodeTest {

    @Test
    fun generationAndVerification_succeedsWithHyphensSpacesAndCase() {
        val hasher = PasswordHasher(iterations = 5_000)
        val manager = RecoveryCodeManager(hasher)

        val codeData = manager.generateRecoveryCode()
        assertEquals(40, codeData.rawCode.length) // 20 bytes = 40 hex chars = 160 bits
        assertTrue(codeData.formattedCode.contains("-"))

        // Exact match
        assertTrue(manager.verifyCode(codeData.rawCode, codeData.hashRecord))

        // Formatted match with hyphens
        assertTrue(manager.verifyCode(codeData.formattedCode, codeData.hashRecord))

        // Lowercase and spaces
        val messyInput = codeData.formattedCode.lowercase().replace("-", " - ")
        assertTrue(manager.verifyCode(messyInput, codeData.hashRecord))

        // Wrong code fails
        assertFalse(manager.verifyCode("0000-1111-2222-3333-4444-5555-6666-7777-8888-9999", codeData.hashRecord))
    }

    @Test
    fun recoveryResetFlow_rotatesCodeAndUpdatesPassword() {
        val tempDir = Files.createTempDirectory("rec_test").toFile()
        try {
            val hasher = PasswordHasher(iterations = 5_000)
            val vault = JvmAesGcmVault(JvmAesGcmVault.generateTestKey())
            val storage = FileCredentialStorage(tempDir)
            val rateLimiter = RateLimiter(InMemoryRateLimitStore())
            val recoveryManager = RecoveryCodeManager(hasher)

            val authManager = AuthManager(
                passwordHasher = hasher,
                keyStoreVault = vault,
                storage = storage,
                rateLimiter = rateLimiter,
                recoveryCodeManager = recoveryManager
            )

            val initialRecoveryCode = authManager.setupCredentials("OldPassword".toCharArray())

            // Try reset with invalid recovery code -> fails
            val failReset = authManager.resetPasswordWithRecovery("INVALID-CODE-XXXX", "NewPassword".toCharArray())
            assertEquals(null, failReset)

            // Reset with valid recovery code -> succeeds and yields new recovery code
            val newRecoveryCode = authManager.resetPasswordWithRecovery(initialRecoveryCode, "NewPassword".toCharArray())
            assertNotNull(newRecoveryCode)
            assertNotEquals(initialRecoveryCode, newRecoveryCode)

            // Verify old password no longer works
            val oldAuth = authManager.authenticate("OldPassword".toCharArray())
            assertTrue(oldAuth is AuthResult.WrongPassword)

            // Verify new password works
            val newAuth = authManager.authenticate("NewPassword".toCharArray())
            assertEquals(AuthResult.Success, newAuth)

            // Verify old recovery code no longer works (it was rotated!)
            val secondReset = authManager.resetPasswordWithRecovery(initialRecoveryCode, "AnotherPass".toCharArray())
            assertEquals(null, secondReset)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
