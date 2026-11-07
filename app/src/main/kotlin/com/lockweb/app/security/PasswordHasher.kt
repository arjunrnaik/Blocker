package com.lockweb.app.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Robust, production-grade PBKDF2 password hasher conforming to MASTER_PROMPT.md Section 7.
 *
 * Algorithm: PBKDF2WithHmacSHA256
 * Iterations: 600,000
 * Salt: 16-byte cryptographically secure random
 * Key length: 256 bits (32 bytes)
 * Comparison: Constant-time MessageDigest.isEqual()
 */
class PasswordHasher(
    val iterations: Int = DEFAULT_ITERATIONS,
    private val secureRandom: SecureRandom = SecureRandom()
) {

    companion object {
        const val DEFAULT_ITERATIONS = 600_000
        const val SALT_LENGTH_BYTES = 16
        const val HASH_LENGTH_BITS = 256
        const val HASH_LENGTH_BYTES = HASH_LENGTH_BITS / 8
        private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    }

    data class HashRecord(
        val hash: ByteArray,
        val salt: ByteArray,
        val iterations: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as HashRecord
            return iterations == other.iterations &&
                    salt.contentEquals(other.salt) &&
                    hash.contentEquals(other.hash)
        }

        override fun hashCode(): Int {
            var result = iterations
            result = 31 * result + salt.contentHashCode()
            result = 31 * result + hash.contentHashCode()
            return result
        }

        /**
         * Serializes the record into a compact 52-byte payload:
         * [4 bytes iterations][16 bytes salt][32 bytes hash]
         */
        fun toByteArray(): ByteArray {
            val bytes = ByteArray(4 + salt.size + hash.size)
            bytes[0] = ((iterations shr 24) and 0xFF).toByte()
            bytes[1] = ((iterations shr 16) and 0xFF).toByte()
            bytes[2] = ((iterations shr 8) and 0xFF).toByte()
            bytes[3] = (iterations and 0xFF).toByte()
            System.arraycopy(salt, 0, bytes, 4, salt.size)
            System.arraycopy(hash, 0, bytes, 4 + salt.size, hash.size)
            return bytes
        }

        companion object {
            fun fromByteArray(bytes: ByteArray): HashRecord? {
                if (bytes.size != 4 + SALT_LENGTH_BYTES + HASH_LENGTH_BYTES) {
                    return null
                }
                val iterations = ((bytes[0].toInt() and 0xFF) shl 24) or
                        ((bytes[1].toInt() and 0xFF) shl 16) or
                        ((bytes[2].toInt() and 0xFF) shl 8) or
                        (bytes[3].toInt() and 0xFF)
                val salt = ByteArray(SALT_LENGTH_BYTES)
                System.arraycopy(bytes, 4, salt, 0, SALT_LENGTH_BYTES)
                val hash = ByteArray(HASH_LENGTH_BYTES)
                System.arraycopy(bytes, 4 + SALT_LENGTH_BYTES, hash, 0, HASH_LENGTH_BYTES)
                return HashRecord(hash, salt, iterations)
            }
        }
    }

    /**
     * Hashes a plaintext password with a fresh random salt.
     */
    fun hashPassword(password: CharArray): HashRecord {
        val salt = ByteArray(SALT_LENGTH_BYTES)
        secureRandom.nextBytes(salt)
        val hash = computeHash(password, salt, iterations)
        return HashRecord(hash = hash, salt = salt, iterations = iterations)
    }

    /**
     * Verifies a candidate password against an existing HashRecord using constant-time comparison.
     */
    fun verifyPassword(candidate: CharArray, record: HashRecord): Boolean {
        val candidateHash = computeHash(candidate, record.salt, record.iterations)
        return MessageDigest.isEqual(candidateHash, record.hash)
    }

    private fun computeHash(password: CharArray, salt: ByteArray, iterCount: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterCount, HASH_LENGTH_BITS)
        val skf = SecretKeyFactory.getInstance(ALGORITHM)
        return try {
            skf.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
