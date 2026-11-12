package com.lockweb.app.security

sealed interface AuthResult {
    data object Success : AuthResult
    data class WrongPassword(val attemptsRemainingBeforeLockout: Int) : AuthResult
    data class LockedOut(val remainingSeconds: Long) : AuthResult
    data object InvalidRecoveryCode : AuthResult
    data object Uninitialized : AuthResult
}

sealed interface AuthState {
    data object Uninitialized : AuthState
    data object Locked : AuthState
    data class Authenticated(val sessionExpiresAtMillis: Long) : AuthState
}

fun interface SecurityClock {
    fun currentTimeMillis(): Long
}

object SystemSecurityClock : SecurityClock {
    override fun currentTimeMillis(): Long {
        return try {
            android.os.SystemClock.elapsedRealtime()
        } catch (_: Throwable) {
            System.currentTimeMillis()
        }
    }
}
