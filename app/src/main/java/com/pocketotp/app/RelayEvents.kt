package com.pocketotp.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat

/** Applies relay account states (revoked / suspended / plan limits) to local state. */
object RelayEvents {
    private const val TAG = "PocketOTP"
    private const val CHANNEL_ID = "pocketotp_account"
    private const val NOTIFICATION_ID = 43

    /** Called for every authenticated HTTP response. [token] is the token the request used. */
    fun onResponse(context: Context, token: String, path: String, httpCode: Int, body: String?) {
        val error = RelayState.errorOf(body)
        if (RelayState.isRevoked(httpCode, error)) {
            revoked(context, token)
            return
        }
        val prefs = Preferences(context)
        if (token.isNotBlank() && prefs.token != token) return // stale request from an old pairing
        val issue = RelayState.issueOf(httpCode, error)
        when {
            issue == RelayState.Issue.DEVICE_SUSPENDED -> prefs.planSuspended = true
            issue != null -> {
                prefs.relayIssue = issue.key
                prefs.relayIssueSlot = if (issue == RelayState.Issue.NUMBER_NOT_ON_PLAN) RelayState.slotOf(body) ?: 0 else 0
                prefs.relayIssueAt = System.currentTimeMillis()
            }
            httpCode in 200..299 && (path == "/sms" || path == "/phone/sms") -> {
                // A successful upload proves the quota and that slot are fine.
                if (prefs.relayIssue.isNotEmpty()) prefs.relayIssue = ""
                if (prefs.planSuspended) prefs.planSuspended = false
            }
        }
    }

    fun onPresence(context: Context, token: String, body: String?) {
        val p = RelayState.parsePresence(body) ?: return
        val prefs = Preferences(context)
        if (token.isNotBlank() && prefs.token != token) return
        p.suspended?.let { prefs.planSuspended = it }
        p.plan?.let { prefs.plan = it }
        p.allowedSlots?.let { slots ->
            prefs.allowedSlots = RelayState.storeSlots(slots)
            if (prefs.relayIssue == RelayState.Issue.NUMBER_NOT_ON_PLAN.key && prefs.relayIssueSlot in slots) prefs.relayIssue = ""
        }
    }

    /** Device token revoked by the relay: forget it and ask the user to pair again. */
    fun revoked(context: Context, token: String) {
        val prefs = Preferences(context)
        if (prefs.token.isBlank() || (token.isNotBlank() && prefs.token != token)) return
        Log.w(TAG, "device token revoked by relay; cleared")
        prefs.token = ""
        prefs.deviceId = ""
        prefs.revoked = true
        clearAccountState(prefs)
        prefs.connectionState = "offline"
        notifyRevoked(context)
    }

    fun clearAccountState(prefs: Preferences) {
        prefs.planSuspended = false
        prefs.plan = ""
        prefs.allowedSlots = ""
        prefs.relayIssue = ""
        prefs.relayIssueSlot = 0
    }

    /** New connection (pairing or manual save): forget everything learned from the previous one. */
    fun onNewConnection(context: Context) {
        val prefs = Preferences(context)
        prefs.revoked = false
        clearAccountState(prefs)
        context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
    }

    private fun notifyRevoked(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.account_channel), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val open = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_REPAIR, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_relay)
            .setContentTitle(context.getString(R.string.revoked_title))
            .setContentText(context.getString(R.string.revoked_body))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.revoked_body)))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            nm.notify(NOTIFICATION_ID, n)
        } catch (_: SecurityException) {
            // Notifications not allowed; the Home banner still shows it.
        }
    }
}
