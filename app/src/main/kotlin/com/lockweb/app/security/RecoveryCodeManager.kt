package com.lockweb.app.security

import java.security.SecureRandom
import java.util.Locale

/**
 * Manages 160-bit random recovery codes for emergency account resets.
 * Stored ONLY as a PBKDF2 hash.
 */
class RecoveryCodeManager(
    private val passwordHasher: PasswordHasher,
    private val secureRandom: SecureRandom = SecureRandom()
) {

    companion object {
        const val CODE_BYTE_LENGTH = 20 // 160 bits
        private val HEX_CHARS = "0123456789ABCDEF".toCharArray()

        /**
         * Normalizes user-entered recovery code: strips spaces/hyphens and uppercases.
         */
        fun normalizeCode(input: String): String {
            return input.replace("-", "")
                .replace(" ", "")
                .trim()
                .uppercase(Locale.ROOT)
        }
    }

    data class GeneratedCode(
        val rawCode: String,
        val formattedCode: String,
        val hashRecord: PasswordHasher.HashRecord
    )

    /**
     * Generates a new 160-bit random recovery code and its PBKDF2 hash record.
     */
    fun generateRecoveryCode(): GeneratedCode {
        val randomBytes = ByteArray(CODE_BYTE_LENGTH)
        secureRandom.nextBytes(randomBytes)

        val hexBuilder = StringBuilder(CODE_BYTE_LENGTH * 2)
        for (b in randomBytes) {
            val v = b.toInt() and 0xFF
            hexBuilder.append(HEX_CHARS[v ushr 4])
            hexBuilder.append(HEX_CHARS[v and 0x0F])
        }
        val rawHex = hexBuilder.toString() // 40 hex chars = 160 bits

        // Format as 8 blocks of 5 or 5 blocks of 8:
        // E.g. AAAA-BBBB-CCCC-DDDD-EEEE-FFFF-GGGG-HHHH (8 groups of 4 = 32)
        // Or 10 groups of 4:
        val formatted = rawHex.chunked(4).joinToString("-")

        val hashRecord = passwordHasher.hashPassword(rawHex.toCharArray())
        return GeneratedCode(
            rawCode = rawHex,
            formattedCode = formatted,
            hashRecord = hashRecord
        )
    }

    /**
     * Verifies user input against stored recovery code hash.
     */
    fun verifyCode(userInput: String, storedRecord: PasswordHasher.HashRecord): Boolean {
        val normalized = normalizeCode(userInput)
        if (normalized.length != CODE_BYTE_LENGTH * 2) {
            return false
        }
        return passwordHasher.verifyPassword(normalized.toCharArray(), storedRecord)
    }
}
