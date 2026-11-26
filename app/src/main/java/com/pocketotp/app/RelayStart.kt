package com.pocketotp.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

object RelayStart {
    private const val TAG = "PocketOTP"
    private const val CHANNEL_ID = "pocketotp_account"
    private const val NOTIFICATION_ID = 44

    fun shouldRun(context: Context): Boolean {
        val prefs = Preferences(context)
        return prefs.enabled && prefs.endpoint.isNotBlank() && prefs.token.isNotBlank()
    }

    /**
     * Starts the relay connection if the phone is paired and forwarding is on.
     * [reason] is logged only (boot, package_replaced, app_start, …).
     * If Android refuses a background start (e.g. vendor battery rules after an update), the user
     * gets a "tap to reconnect" notification instead of a silent offline phone. Returns true if started.
     */
    fun tryStart(context: Context, reason: String = "app"): Boolean {
        if (!shouldRun(context)) {
            Log.i(TAG, "relay start skipped ($reason): not paired or disabled")
            return false
        }
        return try {
            ContextCompat.startForegroundService(
                context.applicationContext,
                Intent(context, RelayService::class.java),
            )
            Log.i(TAG, "relay start requested ($reason)")
            true
        } catch (e: Exception) {
            Log.w(TAG, "cannot start RelayService ($reason): ${e.javaClass.simpleName}")
            notifyStartFailed(context)
            false
        }
    }

    fun clearStartFailure(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
    }

    private fun notifyStartFailed(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.account_channel), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val open = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_relay)
            .setContentTitle(context.getString(R.string.start_failed_title))
            .setContentText(context.getString(R.string.start_failed_body))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.start_failed_body)))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            nm.notify(NOTIFICATION_ID, n)
        } catch (_: SecurityException) {
            // Notifications not allowed; nothing else we can do from the background.
        }
    }
}
