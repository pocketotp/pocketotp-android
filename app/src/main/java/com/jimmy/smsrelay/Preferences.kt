package com.jimmy.smsrelay

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
        const val PREFS_NAME = "sms_relay_prefs"
        const val KEY_ENDPOINT = "endpoint"
        const val KEY_TOKEN = "token"
        const val KEY_ENABLED = "enabled"
        const val KEY_BLACKLIST = "blacklist"
        const val KEY_HISTORY = "history"
        const val KEY_SENDS = "sends"
        const val KEY_CONNECTION = "connection"
        // Hosted relay URL is entered in the app (or via the debug CONFIGURE intent). Not baked in.
        const val DEFAULT_ENDPOINT = ""
        const val DEFAULT_BLACKLIST = """银行
支付宝
微信支付
银联
转账
余额
Bank
card ending"""
        private const val MAX_HISTORY = 10
    }
}
