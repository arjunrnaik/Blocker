package com.lockweb.app

import android.app.Application
import com.lockweb.app.admin.LockWebDeviceAdminReceiver
import com.lockweb.app.blocklist.OisdBlocklistUpdateWorker
import com.lockweb.app.di.AppContainer
import com.lockweb.app.guard.GuardWatchdog
import com.lockweb.app.guard.GuardWatchdogWorker
import com.lockweb.app.vpn.LockWebVpnService
import com.lockweb.corefilter.RuleMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LockWebApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // Eagerly sync Device Administrator and Always-On VPN state so the
        // ProtectionHealth flow emits correct values from the very first frame,
        // even before MainActivity.onResume() runs.
        LockWebDeviceAdminReceiver.isActive(this)
        LockWebVpnService.refreshAlwaysOnState(this)

        // Schedule weekly blocklist update via WorkManager
        OisdBlocklistUpdateWorker.scheduleWeekly(this)

        // Schedule watchdog for Accessibility Guard
        GuardWatchdog.createNotificationChannel(this)
        GuardWatchdogWorker.schedulePeriodic(this)

        // Copy snapshot asset if missing on first launch
        container.blocklistManager.ensureSnapshotCopied()

        // Initialize persistent rules repository and seed initial verification rule if database is empty
        CoroutineScope(Dispatchers.IO).launch {
            val dao = container.database.blockRuleDao()
            if (dao.count() == 0) {
                container.ruleRepository.addRule("reddit.com", RuleMode.SUBDOMAINS, note = "Default verification rule")
            } else {
                container.ruleRepository.recompileAndPush()
            }
        }
    }
}
