package com.lockweb.app.health

import com.lockweb.app.admin.LockWebDeviceAdminReceiver
import com.lockweb.app.guard.GuardSessionManager
import com.lockweb.app.vpn.LockWebVpnService
import com.lockweb.app.vpn.VpnStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

enum class OverallProtectionLevel {
    PROTECTED, // Both VPN Active and Anti-Tamper Guard Running
    DEGRADED,  // Weakened: VPN, Guard, or Device Admin is inactive
    STOPPED,   // Protection is currently idle/stopped
    ERROR      // An unrecoverable error occurred
}

/**
 * Honest, observable protection health model.
 * Per MASTER_PROMPT.md Section 10:
 * "Protected" requires ALL of:
 * 1. VPN TUN established and active
 * 2. AccessibilityGuardService running and connected
 * Otherwise, status is Degraded, Stopped, or Error. Never hardcoded.
 */
data class ProtectionHealth(
    val vpnStatus: VpnStatus,
    val isGuardRunning: Boolean,
    val isDeviceAdminActive: Boolean = false,
    val isAlwaysOnVpn: Boolean = false,
    val isLockdownEnabled: Boolean = false
) {
    val isFullyProtected: Boolean
        get() = vpnStatus is VpnStatus.Active && isGuardRunning

    val overallLevel: OverallProtectionLevel
        get() = when {
            vpnStatus is VpnStatus.Error -> OverallProtectionLevel.ERROR
            isFullyProtected && isDeviceAdminActive -> OverallProtectionLevel.PROTECTED
            vpnStatus is VpnStatus.Active && (!isGuardRunning || !isDeviceAdminActive) -> OverallProtectionLevel.DEGRADED
            vpnStatus is VpnStatus.Degraded -> OverallProtectionLevel.DEGRADED
            else -> OverallProtectionLevel.STOPPED
        }

    val summaryText: String
        get() = when (overallLevel) {
            OverallProtectionLevel.PROTECTED -> "Fully Protected (VPN Active + Guard + Device Admin)"
            OverallProtectionLevel.DEGRADED -> {
                when {
                    vpnStatus is VpnStatus.Active && !isGuardRunning -> "Protection Weakened: Anti-Tamper Guard is Inactive"
                    vpnStatus is VpnStatus.Active && !isDeviceAdminActive -> "Protection Weakened: Device Administrator Inactive"
                    vpnStatus is VpnStatus.Degraded -> "Protection Degraded: ${vpnStatus.reason}"
                    else -> "Protection Weakened"
                }
            }
            OverallProtectionLevel.ERROR -> "Protection Error: ${(vpnStatus as? VpnStatus.Error)?.reason ?: "Unknown"}"
            OverallProtectionLevel.STOPPED -> "Protection Stopped"
        }
}

object HealthMonitor {
    fun observeProtectionHealth(): Flow<ProtectionHealth> {
        return combine(
            LockWebVpnService.status,
            GuardSessionManager.instance.isGuardRunning,
            LockWebDeviceAdminReceiver.isAdminActive,
            LockWebVpnService.isAlwaysOn,
            LockWebVpnService.isLockdownEnabled
        ) { vpn, guard, admin, alwaysOn, lockdown ->
            ProtectionHealth(
                vpnStatus = vpn,
                isGuardRunning = guard,
                isDeviceAdminActive = admin,
                isAlwaysOnVpn = alwaysOn,
                isLockdownEnabled = lockdown
            )
        }
    }
}
