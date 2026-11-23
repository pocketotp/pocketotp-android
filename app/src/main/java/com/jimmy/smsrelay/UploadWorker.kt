package com.jimmy.smsrelay

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

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

        val url = try {
            val base = endpoint.trimEnd('/')
            URL("$base$path")
        } catch (_: Exception) {
            record(payload, skipped = true, reason = "bad_endpoint")
            return Result.failure()
        }

        return try {
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 15_000
                doOutput = true
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
            }
            OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { it.write(payload) }
            val code = conn.responseCode
            conn.disconnect()
            when {
                code in 200..299 -> {
                    if (path == "/sms") record(payload, skipped = false, reason = "ok")
                    Result.success()
                }
                code in 400..499 && code != 408 && code != 429 -> {
                    if (path == "/sms") record(payload, skipped = true, reason = "http_$code")
                    Result.failure()
                }
                else -> Result.retry()
            }
        } catch (_: Exception) {
            Result.retry()
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
        const val KEY_ENDPOINT = "endpoint"
        const val KEY_TOKEN = "token"
        const val KEY_PAYLOAD = "payload"
        const val KEY_PATH = "path"
    }
}
