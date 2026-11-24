package com.pocketotp.app

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.format.DateTimeParseException

data class ConfirmRequest(
    val id: String,
    val to: String,
    val toMasked: String,
    val simLabel: String,
    val text: String,
    val textLength: Int,
    val dryRun: Boolean,
    val createdAt: String?,
    val expiresAt: String?,
    val expiresAtMs: Long?,
) {
    fun isExpired(nowMs: Long = System.currentTimeMillis()): Boolean {
        val deadline = expiresAtMs ?: return false
        return nowMs >= deadline
    }

    companion object {
        fun fromJson(obj: JSONObject?): ConfirmRequest? {
            if (obj == null) return null
            val id = obj.optString("id").trim()
            if (id.isEmpty()) return null
            val to = obj.optString("to")
            val toMasked = obj.optString("toMasked").ifBlank { maskFallback(to) }
            val sim = obj.optJSONObject("sim")
            val text = obj.optString("text")
            val expiresAt = obj.optString("expiresAt").ifBlank { null }
            return ConfirmRequest(
                id = id,
                to = to,
                toMasked = toMasked,
                simLabel = simLabel(sim),
                text = text,
                textLength = if (obj.has("textLength")) obj.optInt("textLength") else text.length,
                dryRun = obj.optBoolean("dryRun") || obj.optBoolean("dry_run"),
                createdAt = obj.optString("createdAt").ifBlank { null },
                expiresAt = expiresAt,
                expiresAtMs = parseIsoMillis(expiresAt),
            )
        }

        fun parseList(arr: JSONArray?): List<ConfirmRequest> {
            if (arr == null) return emptyList()
            val out = ArrayList<ConfirmRequest>(arr.length())
            for (i in 0 until arr.length()) {
                fromJson(arr.optJSONObject(i))?.let { out.add(it) }
            }
            return out
        }

        fun parseIsoMillis(value: String?): Long? {
            if (value.isNullOrBlank()) return null
            val trimmed = value.trim()
            return try {
                Instant.parse(trimmed).toEpochMilli()
            } catch (_: DateTimeParseException) {
                try {
                    Instant.parse(trimmed.replace(" ", "T")).toEpochMilli()
                } catch (_: DateTimeParseException) {
                    null
                }
            }
        }

        fun simLabel(sim: JSONObject?): String {
            if (sim == null) return ""
            val label = sim.optString("label")
            if (label.isNotBlank()) return label
            if (sim.has("slot") && !sim.isNull("slot")) return "SIM ${sim.optInt("slot")}"
            return sim.optString("kind")
        }

        fun maskFallback(to: String): String {
            if (to == "other_sim" || to.isBlank()) return to.ifBlank { "****" }
            val digits = to.filter { it.isDigit() }
            if (digits.length <= 4) return "****"
            val prefix = if (to.startsWith("+")) "+" else ""
            return prefix + "****" + digits.takeLast(4)
        }
    }
}
