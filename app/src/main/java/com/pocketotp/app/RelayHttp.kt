package com.pocketotp.app

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object RelayHttp {
    /** Set by [PocketOtpApp] so every response can update account state (revoked / plan limits). */
    @Volatile var appContext: android.content.Context? = null

    data class Response(
        val code: Int,
        val ok: Boolean,
        val body: String = "",
        val error: String? = null,
    )

    private const val TAG = "PocketOTP"
    private val media = "application/json; charset=utf-8".toMediaType()
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    fun postJson(endpoint: String, token: String, path: String, payload: String): Response {
        val url = endpoint.trimEnd('/') + path
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .post(payload.toRequestBody(media))
            .build()
        return try {
            client.newCall(request).execute().use { resp ->
                val code = resp.code
                val body = resp.body?.string().orEmpty()
                val err = try {
                    if (body.isNotBlank()) JSONObject(body).optString("error") else ""
                } catch (_: Exception) {
                    ""
                }
                if (err.isNotBlank()) {
                    Log.i(TAG, "POST $path http=$code error=$err")
                } else {
                    Log.i(TAG, "POST $path http=$code")
                }
                appContext?.let { RelayEvents.onResponse(it, token, path, code, body) }
                Response(code = code, ok = code in 200..299, body = body, error = err.ifBlank { null })
            }
        } catch (e: Exception) {
            Log.w(TAG, "POST $path failed: ${e.javaClass.simpleName}")
            Response(code = 0, ok = false, error = e.javaClass.simpleName)
        }
    }
}
