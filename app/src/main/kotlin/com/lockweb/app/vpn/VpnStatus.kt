package com.lockweb.app.vpn

/**
 * Honest, observable health status of the LockWeb VPN service.
 */
sealed interface VpnStatus {
    /**
     * Service is completely stopped.
     */
    data object Idle : VpnStatus

    /**
     * Service is establishing the TUN interface and initializing.
     */
    data object Starting : VpnStatus

    /**
     * TUN interface is established, read loop is active, and self-test passed.
     */
    data object Active : VpnStatus

    /**
     * VPN was temporarily paused by the user.
     */
    data object Paused : VpnStatus

    /**
     * User has not yet granted VPN consent via the system dialog.
     */
    data object PermissionRequired : VpnStatus

    /**
     * TUN is running, but upstream DNS resolution is experiencing failures.
     */
    data class Degraded(val reason: String) : VpnStatus

    /**
     * Unrecoverable error encountered.
     */
    data class Error(val reason: String) : VpnStatus
}
