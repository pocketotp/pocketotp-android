package com.pocketotp.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class RelayService : Service() {
    private val running = AtomicBoolean(false)
    private val loop = AtomicBoolean(false)
    private var thread: Thread? = null
    private var webSocket: WebSocket? = null
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    private val pollClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startInForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()
        startLoop()
        return START_STICKY
    }

    override fun onDestroy() {
        loop.set(false)
        running.set(false)
        webSocket?.close(1000, "stop")
        webSocket = null
        setConnection("offline")
        super.onDestroy()
    }

    private fun startInForeground() {
        val pending = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_stat_relay)
            .setContentIntent(pending)
            .setOngoing(true)
            .setSilent(true)
            .setGroup(STATUS_GROUP)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            )
            channel.setShowBadge(false)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun startLoop() {
        if (!running.compareAndSet(false, true)) return
        loop.set(true)
        thread = Thread({
            var backoffMs = 1_000L
            while (loop.get()) {
                val prefs = Preferences(this)
                if (!prefs.enabled || prefs.endpoint.isBlank() || prefs.token.isBlank()) {
                    setConnection("offline")
                    sleepQuiet(5_000)
                    continue
                }
                val result = runWebSocket(prefs)
                if (!loop.get()) break
                when (result) {
                    WsResult.REVOKED -> continue // token cleared; next pass goes offline
                    WsResult.SUSPENDED -> {
                        // Presence still works while suspended; keep it fresh so un-suspending is noticed.
                        setConnection("suspended")
                        postPresence(prefs, transport = null)
                        sleepQuiet(60_000)
                        continue
                    }
                    WsResult.FAILED -> {
                        setConnection("polling")
                        runLongPoll(prefs)
                    }
                    WsResult.OPENED -> Unit
                }
                if (!loop.get()) break
                setConnection("reconnecting")
                sleepQuiet(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(30_000L)
            }
            running.set(false)
        }, "pocketotp-socket").apply { isDaemon = true; start() }
    }

    private enum class WsResult { OPENED, FAILED, REVOKED, SUSPENDED }

    private fun runWebSocket(prefs: Preferences): WsResult {
        val url = toWsUrl(prefs.endpoint) ?: return WsResult.FAILED
        val token = prefs.token
        val opened = AtomicBoolean(false)
        val revoked = AtomicBoolean(false)
        val suspended = AtomicBoolean(false)
        val closed = AtomicBoolean(false)
        val latch = Object()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${prefs.token}")
            .build()
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                opened.set(true)
                backoffReset()
                setConnection("websocket")
                webSocket.send(helloPayload(prefs).toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleSocketMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                if (RelayState.isRevokedClose(code, reason)) revoked.set(true)
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (RelayState.isRevokedClose(code, reason)) revoked.set(true)
                closed.set(true)
                synchronized(latch) { latch.notifyAll() }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val http = response?.code ?: 0
                Log.w(TAG, "websocket failure: ${t.javaClass.simpleName} http=$http")
                if (http in 400..499) {
                    val error = RelayState.errorOf(runCatching { response?.peekBody(4096)?.string() }.getOrNull())
                    if (RelayState.isRevoked(http, error)) revoked.set(true)
                    if (RelayState.issueOf(http, error) == RelayState.Issue.DEVICE_SUSPENDED) suspended.set(true)
                }
                closed.set(true)
                synchronized(latch) { latch.notifyAll() }
            }
        }
        val ws = client.newWebSocket(request, listener)
        webSocket = ws
        var lastBeat = 0L
        var lastPresence = 0L
        while (loop.get() && !closed.get()) {
            if (opened.get() && System.currentTimeMillis() - lastBeat >= 25_000L) {
                ws.send(helloPayload(prefs, type = "heartbeat").toString())
                lastBeat = System.currentTimeMillis()
            }
            // Plan state (allowedSlots / suspended) only comes back on HTTP presence. Absent on the old relay.
            if (opened.get() && System.currentTimeMillis() - lastPresence >= PRESENCE_EVERY_MS) {
                lastPresence = System.currentTimeMillis()
                postPresence(prefs, transport = null)
            }
            synchronized(latch) {
                try { latch.wait(5_000) } catch (_: InterruptedException) { }
            }
        }
        try { ws.cancel() } catch (_: Exception) { }
        webSocket = null
        return when {
            revoked.get() -> {
                RelayEvents.revoked(this, token)
                WsResult.REVOKED
            }
            suspended.get() -> {
                Preferences(this).planSuspended = true
                WsResult.SUSPENDED
            }
            opened.get() -> WsResult.OPENED
            else -> WsResult.FAILED
        }
    }

    @Volatile private var backoffFloor = 1_000L
    private fun backoffReset() { backoffFloor = 1_000L }

    private fun runLongPoll(prefs: Preferences) {
        val base = prefs.endpoint.trimEnd('/')
        if (base.isBlank()) return
        postPresence(prefs, transport = "poll")
        if (prefs.token.isBlank()) return
        val deadline = System.currentTimeMillis() + 25_000
        while (loop.get() && System.currentTimeMillis() < deadline) {
            val url = "$base/phone/poll?timeout=20"
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer ${prefs.token}")
                .get()
                .build()
            try {
                pollClient.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        val err = RelayState.errorOf(runCatching { resp.peekBody(4096).string() }.getOrNull())
                        if (RelayState.isRevoked(resp.code, err)) RelayEvents.revoked(this@RelayService, prefs.token)
                        if (RelayState.issueOf(resp.code, err) == RelayState.Issue.DEVICE_SUSPENDED) prefs.planSuspended = true
                        return
                    }
                    val body = resp.body?.string() ?: return
                    val json = JSONObject(body)
                    ConfirmManager.handleIncoming(this@RelayService, ConfirmRequest.parseList(json.optJSONArray("confirm_requests")))
                    val commands = json.optJSONArray("commands") ?: JSONArray()
                    handleCommands(commands)
                }
            } catch (e: IOException) {
                Log.w(TAG, "poll failed: ${e.javaClass.simpleName}")
                return
            }
        }
    }

    /** POST /phone/presence through [RelayHttp] (which also handles revoked / suspended) and apply plan fields. */
    private fun postPresence(prefs: Preferences, transport: String?) {
        val token = prefs.token
        if (token.isBlank()) return
        val payload = helloPayload(prefs, type = "presence", transport = transport).toString()
        val resp = RelayHttp.postJson(prefs.endpoint, token, "/phone/presence", payload)
        if (resp.ok) RelayEvents.onPresence(this, token, resp.body)
    }

    private fun handleSocketMessage(text: String) {
        try {
            val json = JSONObject(text)
            when (json.optString("type")) {
                "command" -> json.optJSONObject("command")?.let { SendSms.handleCommand(this, it) }
                "commands" -> handleCommands(json.optJSONArray("commands") ?: JSONArray())
                "confirm_request" -> ConfirmRequest.fromJson(json.optJSONObject("request"))?.let {
                    ConfirmManager.handleIncoming(this, listOf(it))
                }
                "confirm_requests" -> ConfirmManager.handleIncoming(
                    this,
                    ConfirmRequest.parseList(json.optJSONArray("requests")),
                )
                "pong" -> { /* heartbeat ok */ }
            }
        } catch (e: Exception) {
            Log.w(TAG, "bad socket message")
        }
    }

    private fun handleCommands(commands: JSONArray) {
        for (i in 0 until commands.length()) {
            val cmd = commands.optJSONObject(i) ?: continue
            SendSms.handleCommand(this, cmd)
        }
    }

    private fun helloPayload(prefs: Preferences, type: String = "hello", transport: String? = null): JSONObject {
        val o = JSONObject()
            .put("type", type)
            .put("appVersion", BuildConfig.VERSION_NAME)
            .put("caps", JSONArray().put("confirm"))
            .put("sims", SimInfo.toPublicArray(SimInfo.list(this)))
        if (transport != null) o.put("transport", transport)
        return o
    }

    private fun toWsUrl(endpoint: String): String? {
        val base = endpoint.trim()
        if (base.isEmpty()) return null
        val ws = when {
            base.startsWith("https://") -> "wss://" + base.removePrefix("https://")
            base.startsWith("http://") -> "ws://" + base.removePrefix("http://")
            else -> return null
        }
        return ws.trimEnd('/') + "/phone/ws"
    }

    private fun setConnection(state: String) {
        Preferences(this).connectionState = state
    }

    private fun sleepQuiet(ms: Long) {
        try { Thread.sleep(ms) } catch (_: InterruptedException) { }
    }

    companion object {
        private const val TAG = "PocketOTP"
        private const val CHANNEL_ID = "pocketotp_status"
        private const val STATUS_GROUP = "pocketotp_status"
        private const val NOTIFICATION_ID = 42
        private const val PRESENCE_EVERY_MS = 10 * 60_000L
    }
}
