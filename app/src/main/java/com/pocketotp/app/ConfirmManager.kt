package com.pocketotp.app

import android.app.ActivityOptions
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Idempotent confirm-request UI: one high-priority notification per id,
 * until the user decides or [ConfirmRequest.expiresAt] is reached.
 */
object ConfirmManager {
    const val ACTION_DENY = "com.pocketotp.app.CONFIRM_DENY"
    const val EXTRA_ID = "confirm_id"
    const val EXTRA_TO = "confirm_to"
    const val EXTRA_TO_MASKED = "confirm_to_masked"
    const val EXTRA_SIM_LABEL = "confirm_sim_label"
    const val EXTRA_TEXT = "confirm_text"
    const val EXTRA_DRY_RUN = "confirm_dry_run"
    const val EXTRA_EXPIRES_AT = "confirm_expires_at"
    const val EXTRA_CREATED_AT = "confirm_created_at"

    private const val TAG = "PocketOTP"
    private const val CHANNEL_ID = "pocketotp_confirm"
    private const val CONFIRM_GROUP = "pocketotp_confirms"
    private const val NOTIFICATION_BASE = 1000

    private val shown = ConcurrentHashMap<String, ConfirmRequest>()
    private val decided = ConcurrentHashMap.newKeySet<String>()
    private val launchedActivities = ConcurrentHashMap.newKeySet<String>()
    private val expiryRuns = ConcurrentHashMap<String, Runnable>()
    private val handler = Handler(Looper.getMainLooper())

    fun handleIncoming(context: Context, requests: List<ConfirmRequest>) {
        if (requests.isEmpty()) return
        val app = context.applicationContext
        ensureChannel(app)
        for (req in requests) {
            if (req.isExpired()) {
                dismiss(app, req.id)
                continue
            }
            if (decided.contains(req.id)) continue
            if (shown.putIfAbsent(req.id, req) != null) {
                maybeLaunchConfirmActivity(app, req)
                continue
            }
            postNotification(app, req)
            scheduleExpiry(app, req)
            maybeLaunchConfirmActivity(app, req)
        }
    }

    fun pendingRequests(): List<ConfirmRequest> =
        shown.values.filter { !it.isExpired() && !decided.contains(it.id) }

    fun launchPendingIfForeground(context: Context) {
        val req = pendingRequests().firstOrNull() ?: return
        maybeLaunchConfirmActivity(context.applicationContext, req)
    }

    fun request(id: String): ConfirmRequest? = shown[id]

    fun requestFromIntent(intent: Intent?): ConfirmRequest? {
        val id = intent?.getStringExtra(EXTRA_ID) ?: return null
        shown[id]?.let { return it }
        if (id.isBlank()) return null
        val expiresAt = intent.getStringExtra(EXTRA_EXPIRES_AT)
        return ConfirmRequest(
            id = id,
            to = intent.getStringExtra(EXTRA_TO).orEmpty(),
            toMasked = intent.getStringExtra(EXTRA_TO_MASKED).orEmpty(),
            simLabel = intent.getStringExtra(EXTRA_SIM_LABEL).orEmpty(),
            text = intent.getStringExtra(EXTRA_TEXT).orEmpty(),
            textLength = intent.getStringExtra(EXTRA_TEXT)?.length ?: 0,
            dryRun = intent.getBooleanExtra(EXTRA_DRY_RUN, false),
            createdAt = intent.getStringExtra(EXTRA_CREATED_AT),
            expiresAt = expiresAt,
            expiresAtMs = ConfirmRequest.parseIsoMillis(expiresAt),
        )
    }

    fun putExtras(intent: Intent, req: ConfirmRequest): Intent {
        return intent
            .putExtra(EXTRA_ID, req.id)
            .putExtra(EXTRA_TO, req.to)
            .putExtra(EXTRA_TO_MASKED, req.toMasked)
            .putExtra(EXTRA_SIM_LABEL, req.simLabel)
            .putExtra(EXTRA_TEXT, req.text)
            .putExtra(EXTRA_DRY_RUN, req.dryRun)
            .putExtra(EXTRA_EXPIRES_AT, req.expiresAt)
            .putExtra(EXTRA_CREATED_AT, req.createdAt)
    }

    fun submitDecision(context: Context, id: String, decision: String): ConfirmClient.Result {
        val app = context.applicationContext
        val req = shown[id]
        if (req != null && req.isExpired()) {
            finish(app, id, cancelledNotification = true)
            toast(app, app.getString(R.string.confirm_expired))
            return ConfirmClient.Result(ok = false, httpCode = 409, status = "expired", error = "expired")
        }
        if (decided.contains(id)) {
            return ConfirmClient.Result(ok = true, httpCode = 200, status = "already_decided", error = null)
        }
        val prefs = Preferences(app)
        if (prefs.endpoint.isBlank() || prefs.token.isBlank()) {
            toast(app, app.getString(R.string.need_endpoint_token))
            return ConfirmClient.Result(ok = false, httpCode = 0, status = null, error = "not_configured")
        }
        val result = ConfirmClient.post(prefs.endpoint, prefs.token, id, decision)
        when {
            result.ok && decision == "allow" -> {
                finish(app, id, cancelledNotification = true)
                prefs.addSend(
                    SendItem(
                        id = id,
                        toMasked = req?.toMasked ?: "****",
                        simLabel = req?.simLabel ?: "",
                        status = "queued",
                        timestamp = System.currentTimeMillis(),
                        error = if (req?.dryRun == true) "dry_run" else null,
                    ),
                )
                toast(app, app.getString(R.string.confirm_allowed))
            }
            result.ok && decision == "deny" -> {
                finish(app, id, cancelledNotification = true)
                prefs.addSend(
                    SendItem(
                        id = id,
                        toMasked = req?.toMasked ?: "****",
                        simLabel = req?.simLabel ?: "",
                        status = "cancelled",
                        timestamp = System.currentTimeMillis(),
                        error = "denied",
                    ),
                )
                toast(app, app.getString(R.string.confirm_denied))
            }
            result.error == "expired" -> {
                finish(app, id, cancelledNotification = true)
                toast(app, app.getString(R.string.confirm_expired))
            }
            result.error == "not_awaiting_confirmation" -> {
                finish(app, id, cancelledNotification = true)
                toast(app, app.getString(R.string.confirm_already_decided))
            }
            result.error == "rate_limited_hour" || result.error == "rate_limited_day" -> {
                toast(app, app.getString(R.string.confirm_rate_limited))
            }
            else -> {
                Log.w(TAG, "confirm $decision failed error=${result.error} http=${result.httpCode}")
                toast(app, app.getString(R.string.confirm_failed))
            }
        }
        return result
    }

    private fun finish(context: Context, id: String, cancelledNotification: Boolean) {
        decided.add(id)
        shown.remove(id)
        launchedActivities.remove(id)
        expiryRuns.remove(id)?.let { handler.removeCallbacks(it) }
        if (cancelledNotification) cancelNotification(context, id)
    }

    private fun dismiss(context: Context, id: String) {
        shown.remove(id)
        launchedActivities.remove(id)
        expiryRuns.remove(id)?.let { handler.removeCallbacks(it) }
        cancelNotification(context, id)
    }

    private fun scheduleExpiry(context: Context, req: ConfirmRequest) {
        val deadline = req.expiresAtMs ?: return
        val delay = (deadline - System.currentTimeMillis()).coerceAtLeast(0L)
        val run = Runnable {
            if (decided.contains(req.id)) return@Runnable
            if (shown[req.id]?.isExpired() == true || shown.containsKey(req.id)) {
                dismiss(context, req.id)
            }
        }
        expiryRuns[req.id]?.let { handler.removeCallbacks(it) }
        expiryRuns[req.id] = run
        handler.postDelayed(run, delay + 50L)
    }


    private fun maybeLaunchConfirmActivity(context: Context, req: ConfirmRequest) {
        if (decided.contains(req.id) || req.isExpired()) return
        if (!launchedActivities.add(req.id)) return
        handler.post {
            if (decided.contains(req.id) || req.isExpired()) return@post
            val intent = confirmIntent(context, req)
            try {
                // Samsung One UI often suppresses heads-up when a silent FGS
                // notification is showing, even for a HIGH confirm channel.
                // After notify(), send the activity PendingIntent with BAL
                // options so ConfirmActivity comes up from the background too.
                val pi = PendingIntent.getActivity(
                    context,
                    requestCode(req.id, "open"),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                if (Build.VERSION.SDK_INT >= 34) {
                    val opts = ActivityOptions.makeBasic()
                    opts.setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
                    )
                    pi.send(context, 0, null, null, null, null, opts.toBundle())
                } else {
                    pi.send()
                }
                Log.i(TAG, "launched ConfirmActivity id=${req.id}")
            } catch (e: Exception) {
                Log.w(TAG, "pending-intent launch failed: ${e.javaClass.simpleName}; trying startActivity")
                try {
                    context.startActivity(intent)
                    Log.i(TAG, "startActivity ConfirmActivity id=${req.id}")
                } catch (e2: Exception) {
                    Log.w(TAG, "cannot start ConfirmActivity: ${e2.javaClass.simpleName}")
                }
            }
        }
    }

    private fun confirmIntent(context: Context, req: ConfirmRequest): Intent {
        return putExtras(Intent(context, ConfirmActivity::class.java), req)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
    }

    private fun postNotification(context: Context, req: ConfirmRequest) {
        val contentIntent = PendingIntent.getActivity(
            context,
            requestCode(req.id, "open"),
            confirmIntent(context, req),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val denyIntent = PendingIntent.getBroadcast(
            context,
            requestCode(req.id, "deny"),
            putExtras(Intent(context, ConfirmActionReceiver::class.java).setAction(ACTION_DENY), req),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = context.getString(if (req.dryRun) R.string.confirm_title_dry else R.string.confirm_title)
        val line = context.getString(R.string.confirm_line, displayNumber(req), req.simLabel.ifBlank { "—" })
        val preview = req.text.ifBlank { "—" }
        val until = formatExpiry(req)
        val big = context.getString(R.string.confirm_big_text, displayNumber(req), req.simLabel.ifBlank { "—" }, preview, until)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_relay)
            .setContentTitle(title)
            .setContentText(line)
            .setStyle(NotificationCompat.BigTextStyle().bigText(big))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setOngoing(false)
            .setGroup(CONFIRM_GROUP)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_ALL)
            .setDefaults(NotificationCompat.DEFAULT_VIBRATE or NotificationCompat.DEFAULT_SOUND)
            .setContentIntent(contentIntent)
            .setFullScreenIntent(contentIntent, true)
            .setTimeoutAfter(((req.expiresAtMs ?: (System.currentTimeMillis() + 5 * 60_000L)) - System.currentTimeMillis()).coerceAtLeast(1_000L))
            // Deny only: allowing requires the full confirm screen and a 1 s hold.
            .addAction(0, context.getString(R.string.confirm_deny), denyIntent)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(notificationId(req.id), notification)
            Log.i(TAG, "posted confirm notification id=${req.id}")
        } catch (e: SecurityException) {
            Log.w(TAG, "cannot post confirm notification: missing POST_NOTIFICATIONS")
        }
    }

    fun displayNumber(req: ConfirmRequest): String =
        req.toMasked.ifBlank { ConfirmRequest.maskFallback(req.to) }.ifBlank { "****" }

    fun formatExpiry(req: ConfirmRequest, nowMs: Long = System.currentTimeMillis()): String {
        val ms = req.expiresAtMs ?: return "—"
        if (nowMs >= ms) return "已过期"
        val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return fmt.format(Date(ms))
    }

    private fun cancelNotification(context: Context, id: String) {
        NotificationManagerCompat.from(context).cancel(notificationId(id))
    }

    private fun notificationId(id: String): Int = NOTIFICATION_BASE + (id.hashCode() and 0x0fff)

    private fun requestCode(id: String, verb: String): Int = (id + ":" + verb).hashCode()

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.confirm_channel),
            NotificationManager.IMPORTANCE_HIGH,
        )
        channel.description = context.getString(R.string.confirm_channel_desc)
        channel.setShowBadge(true)
        channel.enableVibration(true)
        channel.vibrationPattern = longArrayOf(0, 250, 200, 250)
        channel.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        manager.createNotificationChannel(channel)
    }

    private fun toast(context: Context, message: String) {
        handler.post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}
