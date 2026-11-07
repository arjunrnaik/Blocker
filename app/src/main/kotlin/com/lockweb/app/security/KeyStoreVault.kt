package com.lockweb.app.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface KeyStoreVault {
    fun encrypt(plaintext: ByteArray): ByteArray
    fun decrypt(ciphertextWithIv: ByteArray): ByteArray
}

/**
 * Android Keystore implementation protecting sensitive credential blobs with AES-256-GCM.
 * As required by MASTER_PROMPT.md Section 7:
 * "Store hash, salt, and iteration count in an AES-GCM blob protected by an Android Keystore key.
 * Do not claim Keystore hashes anything."
 */
class AndroidKeyStoreVault(
    private val keyAlias: String = KEY_ALIAS
) : KeyStoreVault {

    companion object {
        const val KEY_ALIAS = "LockWebMasterCredentialKey"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val GCM_IV_LENGTH_BYTES = 12
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!keyStore.containsAlias(keyAlias)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )
            val spec = KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGenerator.init(spec)
            return keyGenerator.generateKey()
        }
        val entry = keyStore.getEntry(keyAlias, null) as KeyStore.SecretKeyEntry
        return entry.secretKey
    }

    override fun encrypt(plaintext: ByteArray): ByteArray {
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)

        val iv = cipher.iv // 12-byte GCM IV
        val ciphertext = cipher.doFinal(plaintext)

        // Prepend 12-byte IV to ciphertext
        val result = ByteArray(iv.size + ciphertext.size)
        System.arraycopy(iv, 0, result, 0, iv.size)
        System.arraycopy(ciphertext, 0, result, iv.size, ciphertext.size)
        return result
    }

    override fun decrypt(ciphertextWithIv: ByteArray): ByteArray {
        require(ciphertextWithIv.size > GCM_IV_LENGTH_BYTES) { "Ciphertext blob too short" }
        val key = getOrCreateKey()

        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        System.arraycopy(ciphertextWithIv, 0, iv, 0, GCM_IV_LENGTH_BYTES)

        val ciphertext = ByteArray(ciphertextWithIv.size - GCM_IV_LENGTH_BYTES)
        System.arraycopy(ciphertextWithIv, GCM_IV_LENGTH_BYTES, ciphertext, 0, ciphertext.size)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)

        return cipher.doFinal(ciphertext)
    }
}

/**
 * JVM AES-GCM Vault for local JVM unit testing where AndroidKeyStore is not present.
 */
class JvmAesGcmVault(
    private val key: SecretKey
) : KeyStoreVault {

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val GCM_IV_LENGTH_BYTES = 12

        fun generateTestKey(): SecretKey {
            val keyGen = KeyGenerator.getInstance("AES")
            keyGen.init(256)
            return keyGen.generateKey()
        }
    }

    private val secureRandom = SecureRandom()

    override fun encrypt(plaintext: ByteArray): ByteArray {
        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext)

        val result = ByteArray(iv.size + ciphertext.size)
        System.arraycopy(iv, 0, result, 0, iv.size)
        System.arraycopy(ciphertext, 0, result, iv.size, ciphertext.size)
        return result
    }

    override fun decrypt(ciphertextWithIv: ByteArray): ByteArray {
        require(ciphertextWithIv.size > GCM_IV_LENGTH_BYTES) { "Ciphertext blob too short" }
        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        System.arraycopy(ciphertextWithIv, 0, iv, 0, GCM_IV_LENGTH_BYTES)

        val ciphertext = ByteArray(ciphertextWithIv.size - GCM_IV_LENGTH_BYTES)
        System.arraycopy(ciphertextWithIv, GCM_IV_LENGTH_BYTES, ciphertext, 0, ciphertext.size)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        return cipher.doFinal(ciphertext)
    }
}
