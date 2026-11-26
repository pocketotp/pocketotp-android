package com.pocketotp.app

import org.json.JSONObject

/**
 * Pure interpretation of hosted-relay account states (docs/pairing.md §5, §8).
 * Every field is optional: the old single-user relay sends none of them, and absent means "ignore".
 */
object RelayState {
    const val WS_CLOSE_REVOKED = 4001
    const val ERR_REVOKED = "device_revoked"

    enum class Issue(val key: String) {
        QUOTA_EXCEEDED("quota_exceeded"),
        NUMBER_NOT_ON_PLAN("number_not_on_plan"),
        DEVICE_SUSPENDED("device_suspended"),
        ;

        companion object {
            fun fromKey(key: String?): Issue? = entries.firstOrNull { it.key == key }
        }
    }

    data class Presence(val suspended: Boolean?, val plan: String?, val allowedSlots: List<Int>?)

    /** `error` field of a JSON body, or null. */
    fun errorOf(body: String?): String? =
        if (body.isNullOrBlank()) null else runCatching { JSONObject(body).optString("error").ifBlank { null } }.getOrNull()

    /** Only the explicit `device_revoked` error clears the token; a plain 401 (old relay, typo) never does. */
    fun isRevoked(httpCode: Int, error: String?): Boolean = httpCode == 401 && error == ERR_REVOKED

    fun isRevokedClose(code: Int, reason: String?): Boolean = code == WS_CLOSE_REVOKED || reason == ERR_REVOKED

    fun issueOf(httpCode: Int, error: String?): Issue? = when {
        httpCode == 402 && error == Issue.QUOTA_EXCEEDED.key -> Issue.QUOTA_EXCEEDED
        httpCode == 403 && error == Issue.NUMBER_NOT_ON_PLAN.key -> Issue.NUMBER_NOT_ON_PLAN
        httpCode == 402 && error == Issue.DEVICE_SUSPENDED.key -> Issue.DEVICE_SUSPENDED
        else -> null
    }

    /** `slot` in a `number_not_on_plan` reply, if any. */
    fun slotOf(body: String?): Int? =
        if (body.isNullOrBlank()) null else runCatching {
            val o = JSONObject(body)
            if (o.has("slot")) o.getInt("slot") else null
        }.getOrNull()

    /** Fields from a presence (or /phone/me) reply; null when the relay sends none of them. */
    fun parsePresence(body: String?): Presence? {
        val o = if (body.isNullOrBlank()) return null else runCatching { JSONObject(body) }.getOrNull() ?: return null
        val suspended = if (o.has("suspended") && !o.isNull("suspended")) o.optBoolean("suspended") else null
        val plan = o.optString("plan").ifBlank { null }?.takeIf { o.opt("plan") is String }
        val slots = o.optJSONArray("allowedSlots")?.let { arr ->
            (0 until arr.length()).mapNotNull { i -> arr.opt(i).let { (it as? Number)?.toInt() } }.distinct().sorted()
        }
        if (suspended == null && plan == null && slots == null) return null
        return Presence(suspended, plan, slots)
    }

    fun encodeSlots(slots: List<Int>?): String = slots?.joinToString(",") ?: ""

    /** "" = unknown (old relay) → null; "," style list otherwise. A known empty list is stored as "-". */
    fun decodeSlots(raw: String): List<Int>? = when (raw) {
        "" -> null
        "-" -> emptyList()
        else -> raw.split(',').mapNotNull { it.trim().toIntOrNull() }
    }

    fun storeSlots(slots: List<Int>?): String = when {
        slots == null -> ""
        slots.isEmpty() -> "-"
        else -> encodeSlots(slots)
    }

    /** Pairing claim error → message category. */
    enum class ClaimError { CODE, DEVICE_LIMIT, REPLACE_LIMIT, RATE, NETWORK, OTHER }

    fun claimErrorOf(error: String): ClaimError = when (error) {
        "code_invalid", "code_expired", "code_invalid_or_used", "bad_request" -> ClaimError.CODE
        "device_limit" -> ClaimError.DEVICE_LIMIT
        "replace_limit" -> ClaimError.REPLACE_LIMIT
        "rate_limited" -> ClaimError.RATE
        "network" -> ClaimError.NETWORK
        else -> ClaimError.OTHER
    }
}
