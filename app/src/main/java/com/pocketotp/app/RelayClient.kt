package com.pocketotp.app

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object RelayClient {
    private val io = Executors.newSingleThreadExecutor { r ->
        Thread(r, "pocketotp-result").apply { isDaemon = true }
    }

    data class Payload(
        val code: String?,
        val sender: String?,
        val brand: String?,
        val source: String?,
        val body: String?,
        val timestamp: Long,
        val subscriptionId: Int?,
        val slot: Int?,
        val carrier: String?,
        val test: Boolean,
    ) {
        fun toJson(): String {
            val o = JSONObject()
            o.put("code", code)
            o.put("sender", sender)
            o.put("brand", brand)
            o.put("source", source)
            o.put("body", body)
            o.put("timestamp", timestamp)
            if (subscriptionId != null) o.put("subscriptionId", subscriptionId)
            if (slot != null) o.put("slot", slot)
            o.put("carrier", carrier)
            o.put("test", test)
            return o.toString()
        }
    }

    fun enqueueUpload(context: Context, endpoint: String, token: String, payload: Payload) {
        if (endpoint.isBlank() || token.isBlank()) {
            Log.w("PocketOTP", "endpoint or token missing; drop upload")
            Preferences(context).addHistory(
                HistoryItem(
                    code = payload.code ?: "—",
                    source = payload.source ?: payload.sender ?: "unknown",
                    timestamp = payload.timestamp,
                    skipped = true,
                    reason = "not_configured",
                ),
            )
            return
        }
        enqueueJson(context, endpoint, token, payload.toJson(), "/sms")
    }

    fun reportSendResult(
        context: Context,
        endpoint: String,
        token: String,
        id: String,
        status: String,
        error: String?,
        subscriptionId: Int?,
        slot: Int?,
        carrier: String?,
    ) {
        if (endpoint.isBlank() || token.isBlank()) return
        val payload = JSONObject()
            .put("id", id)
            .put("status", status)
            .put("error", error)
        if (subscriptionId != null) payload.put("subscriptionId", subscriptionId)
        if (slot != null) payload.put("slot", slot)
        if (carrier != null) payload.put("carrier", carrier)
        val body = payload.toString()
        val app = context.applicationContext
        // Post immediately with the same OkHttp stack as confirm/WebSocket.
        // WorkManager is only a fallback. Never block the main thread
        // (SmsSentReceiver / SmsDeliveredReceiver run there).
        io.execute {
            val posted = RelayHttp.postJson(endpoint, token, "/phone/result", body)
            if (!posted.ok) {
                enqueueJson(app, endpoint, token, body, "/phone/result")
            }
        }
    }

    private fun enqueueJson(context: Context, endpoint: String, token: String, payload: String, path: String) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<UploadWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .setInputData(
                workDataOf(
                    UploadWorker.KEY_ENDPOINT to endpoint,
                    UploadWorker.KEY_TOKEN to token,
                    UploadWorker.KEY_PAYLOAD to payload,
                    UploadWorker.KEY_PATH to path,
                ),
            )
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }
}
