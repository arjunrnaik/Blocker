package com.lockweb.app.guard

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Background WorkManager worker that verifies AccessibilityGuardService status.
 */
class GuardWatchdogWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val UNIQUE_WORK_NAME = "LockWebGuardWatchdog"

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<GuardWatchdogWorker>(15, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }

    override suspend fun doWork(): Result {
        GuardWatchdog.checkAndNotify(applicationContext)
        return Result.success()
    }
}
