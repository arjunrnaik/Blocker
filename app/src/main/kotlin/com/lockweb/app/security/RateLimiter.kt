package com.lockweb.app.security

interface RateLimitStore {
    fun getFailedAttempts(): Int
    fun getLockedUntilMillis(): Long
    fun saveState(failedAttempts: Int, lockedUntilMillis: Long)
    fun clear()
}

class InMemoryRateLimitStore : RateLimitStore {
    private var failedAttempts = 0
    private var lockedUntilMillis = 0L

    override fun getFailedAttempts(): Int = failedAttempts
    override fun getLockedUntilMillis(): Long = lockedUntilMillis

    override fun saveState(failedAttempts: Int, lockedUntilMillis: Long) {
        this.failedAttempts = failedAttempts
        this.lockedUntilMillis = lockedUntilMillis
    }

    override fun clear() {
        failedAttempts = 0
        lockedUntilMillis = 0L
    }
}

/**
 * File-backed rate limit store persisting lockout across process death and app restarts.
 * As required by MASTER_PROMPT.md Section 9.
 */
class FileRateLimitStore(
    private val filesDir: java.io.File,
    private val fileName: String = "rate_limit_state.txt"
) : RateLimitStore {

    private val file get() = java.io.File(filesDir, fileName)

    override fun getFailedAttempts(): Int {
        if (!file.exists()) return 0
        return try {
            val lines = file.readLines()
            lines.getOrNull(0)?.trim()?.toIntOrNull() ?: 0
        } catch (_: Exception) {
            0
        }
    }

    override fun getLockedUntilMillis(): Long {
        if (!file.exists()) return 0L
        return try {
            val lines = file.readLines()
            lines.getOrNull(1)?.trim()?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    override fun saveState(failedAttempts: Int, lockedUntilMillis: Long) {
        try {
            file.parentFile?.mkdirs()
            file.writeText("$failedAttempts\n$lockedUntilMillis")
        } catch (_: Exception) {}
    }

    override fun clear() {
        if (file.exists()) {
            file.delete()
        }
    }
}

/**
 * Exponential/stepped rate limiter preventing brute-force password guessing.
 *
 * Lockout progression:
 * - Attempts 1..4: Allowed (no lockout delay)
 * - Attempt 5: 30 seconds
 * - Attempt 6: 2 minutes (120 seconds)
 * - Attempt 7: 15 minutes (900 seconds)
 * - Attempt 8+: 1 hour (3600 seconds)
 */
class RateLimiter(
    private val store: RateLimitStore,
    private val clock: SecurityClock = SystemSecurityClock
) {

    sealed interface Status {
        data class Allowed(val attemptsRemainingBeforeLockout: Int) : Status
        data class LockedOut(val remainingSeconds: Long) : Status
    }

    companion object {
        const val MAX_FREE_ATTEMPTS = 4

        fun getLockoutDurationSeconds(failureCount: Int): Long {
            return when {
                failureCount <= 4 -> 0L
                failureCount == 5 -> 30L
                failureCount == 6 -> 120L
                failureCount == 7 -> 900L
                else -> 3600L
            }
        }
    }

    fun checkStatus(): Status {
        val now = clock.currentTimeMillis()
        val lockedUntil = store.getLockedUntilMillis()

        if (now < lockedUntil) {
            val remainingSec = ((lockedUntil - now) + 999) / 1000
            return Status.LockedOut(remainingSec)
        }

        val failed = store.getFailedAttempts()
        val remaining = (MAX_FREE_ATTEMPTS - (failed % (MAX_FREE_ATTEMPTS + 1))).coerceAtLeast(0)
        return Status.Allowed(remaining)
    }

    /**
     * Records a failed authentication attempt.
     * Computes any new lockout delay and persists state.
     * @return remaining lockout seconds (0 if still allowed).
     */
    fun recordFailure(): Long {
        val now = clock.currentTimeMillis()
        val newCount = store.getFailedAttempts() + 1
        val delaySec = getLockoutDurationSeconds(newCount)
        val lockedUntil = if (delaySec > 0) now + (delaySec * 1000L) else 0L

        store.saveState(newCount, lockedUntil)
        return delaySec
    }

    /**
     * Resets failure counter and lockout on successful authentication.
     */
    fun recordSuccess() {
        store.clear()
    }
}
