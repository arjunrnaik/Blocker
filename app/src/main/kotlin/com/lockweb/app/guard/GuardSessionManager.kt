package com.lockweb.app.guard

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.os.SystemClock

/**
 * Manages the temporary authorization window granted when the user enters their master password.
 * Per MASTER_PROMPT.md Section 7.2:
 * "If the user enters the correct password -> allow navigation (do not intercept for 60 seconds)."
 */
class GuardSessionManager(
    private val clock: () -> Long = { SystemClock.elapsedRealtime() }
) {
    companion object {
        const val DEFAULT_WINDOW_MS = 60_000L
        val instance = GuardSessionManager()
    }

    private val _isGuardRunning = MutableStateFlow(false)
    val isGuardRunning: StateFlow<Boolean> = _isGuardRunning.asStateFlow()

    @Volatile
    private var authorizedUntilMillis = 0L

    fun setGuardRunning(running: Boolean) {
        _isGuardRunning.value = running
    }

    fun syncGuardStatus(context: android.content.Context) {
        val isInstanceRunning = AccessibilityGuardService.instance != null
        val isSystemEnabled = isAccessibilityServiceEnabled(context)
        _isGuardRunning.value = isInstanceRunning || isSystemEnabled
    }

    private fun isAccessibilityServiceEnabled(context: android.content.Context): Boolean {
        return try {
            val am = context.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE) as? android.view.accessibility.AccessibilityManager
                ?: return false
            val enabledServices = am.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                ?: return false
            enabledServices.any {
                it.resolveInfo?.serviceInfo?.packageName == context.packageName &&
                it.resolveInfo?.serviceInfo?.name == AccessibilityGuardService::class.java.name
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Grants temporary authorization window (default 60 seconds).
     */
    fun grantTemporaryAccess(durationMillis: Long = DEFAULT_WINDOW_MS) {
        authorizedUntilMillis = clock() + durationMillis
    }

    /**
     * Revokes temporary authorization immediately.
     */
    fun revokeTemporaryAccess() {
        authorizedUntilMillis = 0L
    }

    /**
     * Checks if user is within the temporary authorization window.
     */
    fun isTemporarilyAuthorized(): Boolean {
        return clock() < authorizedUntilMillis
    }

    /**
     * Returns remaining authorized seconds (0 if expired).
     */
    fun getRemainingAuthorizedSeconds(): Long {
        val rem = authorizedUntilMillis - clock()
        return if (rem > 0) (rem + 999) / 1000 else 0L
    }
}
