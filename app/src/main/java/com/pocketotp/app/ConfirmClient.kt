package com.pocketotp.app

import org.json.JSONObject

object ConfirmClient {
    data class Result(
        val ok: Boolean,
        val httpCode: Int,
        val status: String?,
        val error: String?,
    )

    fun post(endpoint: String, token: String, id: String, decision: String): Result {
        val payload = JSONObject().put("id", id).put("decision", decision).toString()
        val resp = RelayHttp.postJson(endpoint, token, "/phone/confirm", payload)
        val json = try {
            if (resp.body.isNotBlank()) JSONObject(resp.body) else JSONObject()
        } catch (_: Exception) {
            JSONObject()
        }
        val error = json.optString("error").ifBlank { resp.error }
        val status = json.optString("status").ifBlank { null }
        val ok = json.has("ok") && json.optBoolean("ok")
        return Result(ok = ok, httpCode = resp.code, status = status, error = error)
    }
}
