package com.pocketotp.app

import android.net.Uri
import android.os.Build
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder

/**
 * QR / deep-link pairing. Contract: docs/pairing.md.
 *
 *   pocketotp://pair?v=1&relay=<https base URL>&code=<pairing code>[&exp=<unix seconds>]
 *
 * The code is short-lived and single-use; the app exchanges it at
 * POST {relay}/pair/claim for a long-lived device token. Tokens never appear in QR codes.
 */
object Pairing {
    const val SCHEME = "pocketotp"
    const val HOST = "pair"
    const val VERSION = 1
    const val CLAIM_PATH = "/pair/claim"
    private const val TAG = "PocketOTP"

    // Crockford base32 without I, L, O, U.
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    const val CODE_MIN = 8
    const val CODE_MAX = 16

    data class Offer(val relay: String, val code: String, val expiresAtSec: Long?) {
        val relayHost: String get() = runCatching { URI(relay).host }.getOrNull().orEmpty()
        /** Code grouped by four for display, e.g. 7K3M-Q9TD-2XHF. */
        val displayCode: String get() = code.chunked(4).joinToString("-")
    }

    sealed class Parsed {
        data class Ok(val offer: Offer) : Parsed()
        data class Error(val reason: Reason) : Parsed()
    }

    enum class Reason { NOT_POCKETOTP, UNSUPPORTED_VERSION, BAD_RELAY, BAD_CODE, EXPIRED }

    /** Normalizes a typed or scanned code: uppercase, strip separators, map look-alikes. */
    fun normalizeCode(raw: String): String? {
        val s = raw.trim().uppercase()
            .replace("-", "").replace(" ", "")
            .map { when (it) { 'O' -> '0'; 'I', 'L' -> '1'; else -> it } }
            .joinToString("")
        if (s.length !in CODE_MIN..CODE_MAX) return null
        if (s.any { it !in ALPHABET }) return null
        return s
    }

    /** Accepts only an https URL with a host, no credentials, query or fragment. Returns it without a trailing slash. */
    fun normalizeRelay(raw: String): String? {
        val uri = runCatching { URI(raw.trim()) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        if (uri.host.isNullOrBlank() || uri.rawUserInfo != null) return null
        if (uri.rawQuery != null || uri.rawFragment != null) return null
        return raw.trim().trimEnd('/')
    }

    fun parse(text: String, nowSec: Long = System.currentTimeMillis() / 1000): Parsed {
        val raw = text.trim()
        val uri = runCatching { URI(raw) }.getOrNull()
            ?: return Parsed.Error(Reason.NOT_POCKETOTP)
        if (!SCHEME.equals(uri.scheme, ignoreCase = true) || !HOST.equals(uri.host ?: uri.authority, ignoreCase = true)) {
            return Parsed.Error(Reason.NOT_POCKETOTP)
        }
        val q = query(uri.rawQuery)
        val v = q["v"]?.toIntOrNull() ?: VERSION
        if (v != VERSION) return Parsed.Error(Reason.UNSUPPORTED_VERSION)
        val relay = q["relay"]?.let { normalizeRelay(it) } ?: return Parsed.Error(Reason.BAD_RELAY)
        val code = q["code"]?.let { normalizeCode(it) } ?: return Parsed.Error(Reason.BAD_CODE)
        val exp = q["exp"]?.toLongOrNull()
        if (exp != null && exp <= nowSec) return Parsed.Error(Reason.EXPIRED)
        return Parsed.Ok(Offer(relay, code, exp))
    }

    fun parse(uri: Uri?): Parsed = if (uri == null) Parsed.Error(Reason.NOT_POCKETOTP) else parse(uri.toString())

    private fun query(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        val out = LinkedHashMap<String, String>()
        for (part in raw.split('&')) {
            val i = part.indexOf('=')
            if (i <= 0) continue
            val k = URLDecoder.decode(part.substring(0, i), "UTF-8")
            val v = URLDecoder.decode(part.substring(i + 1), "UTF-8")
            out.putIfAbsent(k, v) // first wins; duplicates are ignored
        }
        return out
    }

    // ---------- claim ----------

    data class Claimed(val token: String, val deviceId: String?, val relay: String, val accountLabel: String?)

    sealed class ClaimResult {
        data class Ok(val claimed: Claimed) : ClaimResult()
        data class Failed(val error: String, val httpCode: Int) : ClaimResult()
    }

    fun claimRequestJson(code: String, deviceName: String, model: String, appVersion: String): String =
        JSONObject()
            .put("v", VERSION)
            .put("code", code)
            .put("device", JSONObject()
                .put("name", deviceName)
                .put("model", model)
                .put("platform", "android")
                .put("appVersion", appVersion)
                .put("caps", JSONArray().put("confirm")))
            .toString()

    /** Interprets the relay's reply. The relay URL in the reply may only narrow to the same host. */
    fun parseClaimResponse(offer: Offer, httpCode: Int, body: String): ClaimResult {
        val json = runCatching { JSONObject(body) }.getOrNull()
        if (httpCode !in 200..299) {
            val err = json?.optString("error")?.ifBlank { null } ?: when (httpCode) {
                0 -> "network"
                400 -> "bad_request"
                402 -> "device_limit"
                404 -> "code_invalid"
                410 -> "code_expired"
                429 -> "rate_limited"
                else -> "http_$httpCode"
            }
            return ClaimResult.Failed(err, httpCode)
        }
        if (json == null) return ClaimResult.Failed("bad_response", httpCode)
        val token = json.optString("token")
        if (token.isBlank()) return ClaimResult.Failed("bad_response", httpCode)
        val replyRelay = json.optString("relay").ifBlank { null }?.let { normalizeRelay(it) }
        val relay = if (replyRelay != null && runCatching { URI(replyRelay).host }.getOrNull().equals(offer.relayHost, ignoreCase = true)) {
            replyRelay
        } else {
            offer.relay
        }
        return ClaimResult.Ok(
            Claimed(
                token = token,
                deviceId = json.optString("deviceId").ifBlank { null },
                relay = relay,
                accountLabel = json.optJSONObject("account")?.optString("label")?.ifBlank { null },
            ),
        )
    }

    /** Blocking; call off the main thread. */
    fun claim(offer: Offer): ClaimResult {
        val payload = claimRequestJson(
            code = offer.code,
            deviceName = Build.MODEL.orEmpty(),
            model = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            appVersion = BuildConfig.VERSION_NAME,
        )
        val request = Request.Builder()
            .url(offer.relay + CLAIM_PATH)
            .header("Accept", "application/json")
            .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        return try {
            RelayHttp.client.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                Log.i(TAG, "POST $CLAIM_PATH http=${resp.code}")
                parseClaimResponse(offer, resp.code, body)
            }
        } catch (e: Exception) {
            Log.w(TAG, "POST $CLAIM_PATH failed: ${e.javaClass.simpleName}")
            ClaimResult.Failed("network", 0)
        }
    }
}
