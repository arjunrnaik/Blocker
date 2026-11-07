package com.lockweb.app

import android.app.Application
import com.lockweb.app.blocklist.OisdBlocklistUpdateWorker
import com.lockweb.app.di.AppContainer
import com.lockweb.app.guard.GuardWatchdog
import com.lockweb.app.guard.GuardWatchdogWorker

class LockWebApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // Schedule weekly blocklist update via WorkManager
        OisdBlocklistUpdateWorker.scheduleWeekly(this)

        // Schedule watchdog for Accessibility Guard
        GuardWatchdog.createNotificationChannel(this)
        GuardWatchdogWorker.schedulePeriodic(this)

        // Copy snapshot asset if missing on first launch
        container.blocklistManager.ensureSnapshotCopied()
    }
}
