package com.lockweb.app.admin

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.lockweb.app.R
import com.lockweb.app.guard.GuardWatchdog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Device Administrator implementation for LockWeb.
 * In accordance with MASTER_PROMPT.md Section 10:
 * Prevents direct uninstallation from the launcher while active.
 */
class LockWebDeviceAdminReceiver : DeviceAdminReceiver() {

    companion object {
        private val _isAdminActive = MutableStateFlow(false)
        val isAdminActive: StateFlow<Boolean> = _isAdminActive.asStateFlow()

        fun getComponentName(context: Context): ComponentName {
            return ComponentName(context, LockWebDeviceAdminReceiver::class.java)
        }

        fun isActive(context: Context): Boolean {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            val active = dpm?.isAdminActive(getComponentName(context)) ?: false
            _isAdminActive.value = active
            return active
        }

        fun createActivationIntent(context: Context): Intent {
            return Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, getComponentName(context))
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    context.getString(R.string.device_admin_description)
                )
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        }

        fun removeAdmin(context: Context) {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            dpm?.removeActiveAdmin(getComponentName(context))
            _isAdminActive.value = false
        }
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        _isAdminActive.value = true
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return context.getString(R.string.device_admin_disable_warning)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        _isAdminActive.value = false
        // Post high-priority notification that protection is weakened
        GuardWatchdog.checkAndNotify(context)
    }
}
