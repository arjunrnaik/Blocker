package com.lockweb.app.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

interface CredentialStorage {
    fun savePasswordBlob(encryptedBytes: ByteArray)
    fun loadPasswordBlob(): ByteArray?
    fun saveRecoveryBlob(encryptedBytes: ByteArray)
    fun loadRecoveryBlob(): ByteArray?
    fun hasCredentials(): Boolean
    fun clear()
}

class FileCredentialStorage(
    private val filesDir: File,
    private val passwordFileName: String = "cred_pwd.bin",
    private val recoveryFileName: String = "cred_rec.bin"
) : CredentialStorage {

    private val pwdFile get() = File(filesDir, passwordFileName)
    private val recFile get() = File(filesDir, recoveryFileName)

    override fun savePasswordBlob(encryptedBytes: ByteArray) {
        pwdFile.parentFile?.mkdirs()
        pwdFile.writeBytes(encryptedBytes)
    }

    override fun loadPasswordBlob(): ByteArray? {
        return if (pwdFile.exists()) pwdFile.readBytes() else null
    }

    override fun saveRecoveryBlob(encryptedBytes: ByteArray) {
        recFile.parentFile?.mkdirs()
        recFile.writeBytes(encryptedBytes)
    }

    override fun loadRecoveryBlob(): ByteArray? {
        return if (recFile.exists()) recFile.readBytes() else null
    }

    override fun hasCredentials(): Boolean {
        return pwdFile.exists() && recFile.exists()
    }

    override fun clear() {
        if (pwdFile.exists()) pwdFile.delete()
        if (recFile.exists()) recFile.delete()
    }
}

/**
 * Central security manager controlling authentication state, password checks,
 * rate limiting, and recovery code resets.
 */
class AuthManager(
    private val passwordHasher: PasswordHasher,
    private val keyStoreVault: KeyStoreVault,
    private val storage: CredentialStorage,
    private val rateLimiter: RateLimiter,
    private val recoveryCodeManager: RecoveryCodeManager,
    private val clock: SecurityClock = SystemSecurityClock
) {

    companion object {
        const val DEFAULT_SESSION_DURATION_MS = 60_000L // 60 seconds
    }

    private val _authState = MutableStateFlow<AuthState>(
        if (storage.hasCredentials()) AuthState.Locked else AuthState.Uninitialized
    )
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    fun isInitialized(): Boolean = storage.hasCredentials()

    fun checkRateLimit(): RateLimiter.Status = rateLimiter.checkStatus()

    fun verifyPassword(password: String): AuthResult = authenticate(password.toCharArray())

    fun isAuthenticated(): Boolean {
        val current = _authState.value
        if (current is AuthState.Authenticated) {
            if (clock.currentTimeMillis() < current.sessionExpiresAtMillis) {
                return true
            } else {
                lockImmediately()
            }
        }
        return false
    }

    /**
     * Initializes master password and generates a one-time recovery code.
     * @return Formatted recovery code string to show the user ONCE.
     */
    fun setupCredentials(password: CharArray): String {
        require(password.isNotEmpty()) { "Password cannot be empty" }

        val pwdRecord = passwordHasher.hashPassword(password)
        val encryptedPwd = keyStoreVault.encrypt(pwdRecord.toByteArray())
        storage.savePasswordBlob(encryptedPwd)

        val recovery = recoveryCodeManager.generateRecoveryCode()
        val encryptedRec = keyStoreVault.encrypt(recovery.hashRecord.toByteArray())
        storage.saveRecoveryBlob(encryptedRec)

        _authState.value = AuthState.Locked
        return recovery.formattedCode
    }

    /**
     * Authenticates with master password.
     */
    fun authenticate(password: CharArray): AuthResult {
        if (!storage.hasCredentials()) {
            return AuthResult.Uninitialized
        }

        // 1. Check Rate Limiter
        val limitStatus = rateLimiter.checkStatus()
        if (limitStatus is RateLimiter.Status.LockedOut) {
            return AuthResult.LockedOut(limitStatus.remainingSeconds)
        }

        // 2. Load and decrypt stored record
        val encryptedPwd = storage.loadPasswordBlob() ?: return AuthResult.Uninitialized
        val decryptedBytes = try {
            keyStoreVault.decrypt(encryptedPwd)
        } catch (_: Exception) {
            return AuthResult.Uninitialized
        }

        val storedRecord = PasswordHasher.HashRecord.fromByteArray(decryptedBytes)
            ?: return AuthResult.Uninitialized

        // 3. Verify password
        val isValid = passwordHasher.verifyPassword(password, storedRecord)

        return if (isValid) {
            rateLimiter.recordSuccess()
            val expiresAt = clock.currentTimeMillis() + DEFAULT_SESSION_DURATION_MS
            _authState.value = AuthState.Authenticated(expiresAt)
            AuthResult.Success
        } else {
            val lockoutSec = rateLimiter.recordFailure()
            if (lockoutSec > 0) {
                AuthResult.LockedOut(lockoutSec)
            } else {
                val attemptsLeft = (limitStatus as? RateLimiter.Status.Allowed)?.attemptsRemainingBeforeLockout ?: 0
                AuthResult.WrongPassword(attemptsLeft)
            }
        }
    }

    /**
     * Resets password using the one-time recovery code.
     * @return New recovery code generated for the reset account.
     */
    fun resetPasswordWithRecovery(recoveryCode: String, newPassword: CharArray): String? {
        val encryptedRec = storage.loadRecoveryBlob() ?: return null
        val decryptedBytes = try {
            keyStoreVault.decrypt(encryptedRec)
        } catch (_: Exception) {
            return null
        }

        val storedRecord = PasswordHasher.HashRecord.fromByteArray(decryptedBytes) ?: return null
        val isValid = recoveryCodeManager.verifyCode(recoveryCode, storedRecord)

        if (!isValid) {
            return null
        }

        // Valid recovery code: reset credentials and issue a new recovery code
        rateLimiter.recordSuccess()
        return setupCredentials(newPassword)
    }

    fun lockImmediately() {
        if (_authState.value !is AuthState.Uninitialized) {
            _authState.value = AuthState.Locked
        }
    }

    fun onAppBackgrounded(autoLockTimeoutSeconds: Int) {
        if (autoLockTimeoutSeconds <= 0) {
            lockImmediately()
        }
    }

    /**
     * Executes sensitive action only if authenticated.
     */
    fun <T> requiresAuthentication(action: () -> T): T {
        if (!isAuthenticated()) {
            throw SecurityException("Action requires authentication.")
        }
        return action()
    }
}
