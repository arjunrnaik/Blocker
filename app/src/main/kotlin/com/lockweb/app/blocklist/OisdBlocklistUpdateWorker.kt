package com.lockweb.app.blocklist

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lockweb.app.data.SettingsRepository
import com.lockweb.app.data.settingsDataStore
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * WorkManager periodic worker that downloads and refreshes the OISD NSFW blocklist.
 * Constrained to unmetered network (Wi-Fi) and non-low battery.
 */
class OisdBlocklistUpdateWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "OisdBlocklistWeeklyUpdate"

        fun scheduleWeekly(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .setRequiresBatteryNotLow(true)
                .build()

            val updateRequest = PeriodicWorkRequestBuilder<OisdBlocklistUpdateWorker>(
                7, TimeUnit.DAYS,
                12, TimeUnit.HOURS // Flex interval
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                updateRequest
            )
        }
    }

    override suspend fun doWork(): Result {
        val manager = BlocklistManager(applicationContext)
        val settingsRepo = SettingsRepository(applicationContext.settingsDataStore)

        return try {
            val downloadedText = downloadBlocklist(BlocklistManager.OISD_UPDATE_URL) ?: return Result.retry()

            val success = manager.atomicUpdate(downloadedText)
            if (success) {
                val updatedDomains = manager.loadDomains()
                settingsRepo.recordBlocklistUpdate(
                    timestamp = System.currentTimeMillis(),
                    domainCount = updatedDomains.size
                )
                Result.success()
            } else {
                Result.retry()
            }
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun downloadBlocklist(urlString: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = true

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return null
            }

            BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use { reader ->
                reader.readText()
            }
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }
}
