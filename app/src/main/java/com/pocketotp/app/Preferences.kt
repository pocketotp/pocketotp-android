package com.pocketotp.app

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class SendItem(
    val id: String,
    val toMasked: String,
    val simLabel: String,
    val status: String,
    val timestamp: Long,
    val error: String? = null,
)

data class HistoryItem(
    val code: String,
    val source: String,
    val timestamp: Long,
    val skipped: Boolean = false,
    val reason: String? = null,
)

class Preferences(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var endpoint: String
        get() = prefs.getString(KEY_ENDPOINT, DEFAULT_ENDPOINT).orEmpty()
        set(value) = prefs.edit().putString(KEY_ENDPOINT, value.trim()).apply()

    var token: String
        get() = prefs.getString(KEY_TOKEN, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_TOKEN, value.trim()).apply()

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    var blacklistText: String
        get() = prefs.getString(KEY_BLACKLIST, DEFAULT_BLACKLIST) ?: DEFAULT_BLACKLIST
        set(value) = prefs.edit().putString(KEY_BLACKLIST, value).apply()

    /** The relay revoked this device's token; Home shows a re-pair prompt. */
    var revoked: Boolean
        get() = prefs.getBoolean(KEY_REVOKED, false)
        set(value) = prefs.edit().putBoolean(KEY_REVOKED, value).apply()

    var planSuspended: Boolean
        get() = prefs.getBoolean(KEY_SUSPENDED, false)
        set(value) = prefs.edit().putBoolean(KEY_SUSPENDED, value).apply()

    /** "" when the relay doesn't report a plan (old single-user relay). */
    var plan: String
        get() = prefs.getString(KEY_PLAN, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_PLAN, value).apply()

    /** Encoded by [RelayState.storeSlots]; "" = unknown. */
    var allowedSlots: String
        get() = prefs.getString(KEY_ALLOWED_SLOTS, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_ALLOWED_SLOTS, value).apply()

    /** Last /sms plan error key ([RelayState.Issue.key]) or "". */
    var relayIssue: String
        get() = prefs.getString(KEY_RELAY_ISSUE, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_RELAY_ISSUE, value).apply()

    var relayIssueSlot: Int
        get() = prefs.getInt(KEY_RELAY_ISSUE_SLOT, 0)
        set(value) = prefs.edit().putInt(KEY_RELAY_ISSUE_SLOT, value).apply()

    var relayIssueAt: Long
        get() = prefs.getLong(KEY_RELAY_ISSUE_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_RELAY_ISSUE_AT, value).apply()

    fun blacklistKeywords(): List<String> =
        blacklistText.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()

    fun history(): List<HistoryItem> {
        val raw = prefs.getString(KEY_HISTORY, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        HistoryItem(
                            code = o.optString("code"),
                            source = o.optString("source"),
                            timestamp = o.optLong("timestamp"),
                            skipped = o.optBoolean("skipped"),
                            reason = o.optString("reason").ifEmpty { null },
                        ),
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun addHistory(item: HistoryItem) {
        val next = (listOf(item) + history()).take(MAX_HISTORY)
        val arr = JSONArray()
        next.forEach { it ->
            arr.put(
                JSONObject()
                    .put("code", it.code)
                    .put("source", it.source)
                    .put("timestamp", it.timestamp)
                    .put("skipped", it.skipped)
                    .put("reason", it.reason),
            )
        }
        prefs.edit().putString(KEY_HISTORY, arr.toString()).apply()
    }

    var onboardingDone: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_DONE, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_DONE, value).apply()

    var permissionsAsked: Boolean
        get() = prefs.getBoolean(KEY_PERMS_ASKED, false)
        set(value) = prefs.edit().putBoolean(KEY_PERMS_ASKED, value).apply()

    /** Set when the relay issues a device token through QR pairing. */
    var deviceId: String
        get() = prefs.getString(KEY_DEVICE_ID, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_DEVICE_ID, value).apply()

    var connectionState: String
        get() = prefs.getString(KEY_CONNECTION, "offline") ?: "offline"
        set(value) = prefs.edit().putString(KEY_CONNECTION, value).apply()

    fun sends(): List<SendItem> {
        val raw = prefs.getString(KEY_SENDS, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        SendItem(
                            id = o.optString("id"),
                            toMasked = o.optString("toMasked"),
                            simLabel = o.optString("simLabel"),
                            status = o.optString("status"),
                            timestamp = o.optLong("timestamp"),
                            error = o.optString("error").ifEmpty { null },
                        ),
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun addSend(item: SendItem) {
        val next = (listOf(item) + sends().filter { it.id != item.id }).take(MAX_HISTORY)
        writeSends(next)
    }

    fun updateSend(id: String, status: String, error: String?) {
        val next = sends().map {
            if (it.id == id) it.copy(status = status, error = error, timestamp = System.currentTimeMillis()) else it
        }
        writeSends(next)
    }

    private fun writeSends(items: List<SendItem>) {
        val arr = JSONArray()
        items.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("toMasked", it.toMasked)
                    .put("simLabel", it.simLabel)
                    .put("status", it.status)
                    .put("timestamp", it.timestamp)
                    .put("error", it.error),
            )
        }
        prefs.edit().putString(KEY_SENDS, arr.toString()).apply()
    }

    fun register(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregister(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    companion object {
        const val PREFS_NAME = "pocketotp_prefs"
        const val KEY_ENDPOINT = "endpoint"
        const val KEY_TOKEN = "token"
        const val KEY_ENABLED = "enabled"
        const val KEY_BLACKLIST = "blacklist"
        const val KEY_HISTORY = "history"
        const val KEY_SENDS = "sends"
        const val KEY_CONNECTION = "connection"
        const val KEY_ONBOARDING_DONE = "onboarding_done"
        const val KEY_PERMS_ASKED = "permissions_asked"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_REVOKED = "revoked"
        const val KEY_SUSPENDED = "plan_suspended"
        const val KEY_PLAN = "plan"
        const val KEY_ALLOWED_SLOTS = "allowed_slots"
        const val KEY_RELAY_ISSUE = "relay_issue"
        const val KEY_RELAY_ISSUE_SLOT = "relay_issue_slot"
        const val KEY_RELAY_ISSUE_AT = "relay_issue_at"
        // Hosted relay URL is entered in the app (or via the debug CONFIGURE intent). Not baked in.
        const val DEFAULT_ENDPOINT = ""
        const val DEFAULT_BLACKLIST = """银行
支付宝
微信支付
银联
转账
余额
Bank
card ending
account ending
credit card
debit card
transfer
withdrawal
deposit
payment
transaction
balance
PayPal
Venmo
Zelle
Cash App
Coinbase
Binance
brokerage
loan
Social Security"""
        private const val MAX_HISTORY = 10
    }
}
