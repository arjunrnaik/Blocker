package com.lockweb.app.guard

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import java.lang.ref.WeakReference

/**
 * AccessibilityGuardService — critical self-control enforcement layer.
 * Monitors foreground window transitions to intercept access to settings,
 * device admin, VPN, private DNS, and uninstallation vectors.
 *
 * Privacy Promise (docs/PRIVACY.md):
 * - canRetrieveWindowContent = false: Does NOT read screen text, inputs, or personal data.
 * - Zero logging of activities or websites.
 * - Only inspects package and activity class name to detect guarded system settings.
 */
class AccessibilityGuardService : AccessibilityService() {

    companion object {
        private var currentInstance: WeakReference<AccessibilityGuardService>? = null

        val instance: AccessibilityGuardService?
            get() = currentInstance?.get()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        currentInstance = WeakReference(this)
        GuardSessionManager.instance.setGuardRunning(true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return
        }

        val packageName = event.packageName?.toString() ?: return
        val className = event.className?.toString() ?: ""

        // Never intercept LockWeb itself
        if (packageName == applicationContext.packageName) {
            return
        }

        // If user is currently authorized (within 60-second window), permit access
        if (GuardSessionManager.instance.isTemporarilyAuthorized()) {
            return
        }

        // Check if the foreground window is a guarded target
        if (GuardedTargets.isGuarded(packageName, className)) {
            launchPasswordGate(packageName, className)
        }
    }

    private fun launchPasswordGate(targetPkg: String, targetCls: String) {
        val intent = Intent(this, LockWebPasswordGateActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            )
            putExtra(LockWebPasswordGateActivity.EXTRA_TARGET_PACKAGE, targetPkg)
            putExtra(LockWebPasswordGateActivity.EXTRA_TARGET_CLASS, targetCls)
        }
        startActivity(intent)
    }

    /**
     * Sends the device back to the home screen.
     * Invoked when password gate is cancelled, dismissed, or failed.
     */
    fun sendHome(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_HOME)
    }

    override fun onInterrupt() {
        // Intentionally no-op: onInterrupt() is called for transient accessibility feedback interruptions.
        // The service remains active and connected; true shutdown is handled in onDestroy().
    }

    override fun onDestroy() {
        super.onDestroy()
        if (currentInstance?.get() == this) {
            currentInstance = null
        }
        GuardSessionManager.instance.setGuardRunning(false)
    }
}
