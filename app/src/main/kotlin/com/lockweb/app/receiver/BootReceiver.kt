package com.lockweb.app.receiver

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.lockweb.app.MainActivity
import com.lockweb.app.R
import com.lockweb.app.guard.GuardWatchdog
import com.lockweb.app.vpn.LockWebVpnService

/**
 * BootReceiver handles device startup (BOOT_COMPLETED) and app package updates (MY_PACKAGE_REPLACED).
 * In accordance with MASTER_PROMPT.md Section 10:
 * - Restarts VPN automatically if consent was already granted.
 * - Handles Android 14+ background start restrictions gracefully by falling back to high-priority notification.
 * - Checks Anti-Tamper Guard status (Android automatically restarts enabled accessibility services).
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        const val BOOT_NOTIFICATION_ID = 2002
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }

        // 1. Verify Anti-Tamper Guard watchdog
        GuardWatchdog.checkAndNotify(context)

        // 2. Check if VPN consent was already granted
        val prepareIntent = VpnService.prepare(context)
        if (prepareIntent == null) {
            // Consent is granted: attempt to resume VPN filtering
            val startIntent = Intent(context, LockWebVpnService::class.java).apply {
                this.action = LockWebVpnService.ACTION_START
            }

            try {
                ContextCompat.startForegroundService(context, startIntent)
            } catch (_: Exception) {
                // If Android 14 background start restriction blocks immediate foreground start,
                // notify user to tap and resume
                postResumeNotification(context)
            }
        } else {
            // Consent was not granted yet: ask user to open app
            postResumeNotification(context)
        }
    }

    private fun postResumeNotification(context: Context) {
        GuardWatchdog.createNotificationChannel(context)

        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, GuardWatchdog.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle(context.getString(R.string.boot_resume_title))
            .setContentText(context.getString(R.string.boot_resume_text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(BOOT_NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {}
    }
}
