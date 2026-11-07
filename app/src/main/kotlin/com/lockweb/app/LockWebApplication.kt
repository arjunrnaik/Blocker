package com.lockweb.app

import android.app.Application
import com.lockweb.app.blocklist.OisdBlocklistUpdateWorker
import com.lockweb.app.di.AppContainer

class LockWebApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // Schedule weekly blocklist update via WorkManager
        OisdBlocklistUpdateWorker.scheduleWeekly(this)

        // Copy snapshot asset if missing on first launch
        container.blocklistManager.ensureSnapshotCopied()
    }
}
