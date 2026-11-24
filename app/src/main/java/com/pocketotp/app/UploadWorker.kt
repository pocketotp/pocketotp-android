package com.pocketotp.app

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.json.JSONObject

class UploadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val endpoint = inputData.getString(KEY_ENDPOINT).orEmpty().trim()
        val token = inputData.getString(KEY_TOKEN).orEmpty().trim()
        val payload = inputData.getString(KEY_PAYLOAD).orEmpty()
        val path = inputData.getString(KEY_PATH)?.takeIf { it.isNotBlank() } ?: "/sms"
        if (endpoint.isEmpty() || token.isEmpty() || payload.isEmpty()) {
            return Result.failure()
        }

        val resp = RelayHttp.postJson(endpoint, token, path, payload)
        return when {
            resp.ok -> {
                if (path == "/sms") record(payload, skipped = false, reason = "ok")
                Result.success()
            }
            resp.code in 400..499 && resp.code != 408 && resp.code != 429 -> {
                if (path == "/sms") record(payload, skipped = true, reason = "http_${resp.code}")
                Log.w(TAG, "upload $path giving up http=${resp.code}")
                Result.failure()
            }
            else -> {
                Log.w(TAG, "upload $path retry http=${resp.code} err=${resp.error}")
                Result.retry()
            }
        }
    }

    private fun record(payload: String, skipped: Boolean, reason: String) {
        val parsed = try {
            JSONObject(payload)
        } catch (_: Exception) {
            return
        }
        Preferences(applicationContext).addHistory(
            HistoryItem(
                code = parsed.optString("code").ifEmpty { "—" },
                source = parsed.optString("source").ifEmpty {
                    parsed.optString("brand").ifEmpty { parsed.optString("sender") }
                },
                timestamp = parsed.optLong("timestamp", System.currentTimeMillis()),
                skipped = skipped,
                reason = reason,
            ),
        )
    }

    companion object {
        private const val TAG = "PocketOTP"
        const val KEY_ENDPOINT = "endpoint"
        const val KEY_TOKEN = "token"
        const val KEY_PAYLOAD = "payload"
        const val KEY_PATH = "path"
    }
}
