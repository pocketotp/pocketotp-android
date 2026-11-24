package com.pocketotp.app

import java.util.Calendar

/** Pure presentation helpers for the Home/History screens (unit-tested). */
object HistoryFormat {
    const val REVEAL_MS = 10_000L
    const val HOME_RECENT = 5

    /** Masks all but the last two characters, e.g. 482913 -> ••••13. */
    fun maskCode(code: String): String {
        val c = code.trim()
        if (c.isEmpty() || c == "—") return "—"
        if (c.length <= 2) return "••"
        return "•".repeat(c.length - 2) + c.takeLast(2)
    }

    /** A forward that never reached the relay has nothing worth revealing. */
    fun canReveal(item: HistoryItem): Boolean =
        !item.skipped && item.code.isNotBlank() && item.code != "—"

    enum class Outcome { FORWARDED, BLOCKED, FAILED }

    fun outcome(item: HistoryItem): Outcome = when {
        !item.skipped -> Outcome.FORWARDED
        item.reason?.startsWith("blacklist") == true -> Outcome.BLOCKED
        else -> Outcome.FAILED
    }

    /** Keyword that blocked a message, from reason "blacklist:<kw>". */
    fun blockedKeyword(item: HistoryItem): String? =
        item.reason?.takeIf { it.startsWith("blacklist:") }?.substringAfter(':')?.takeIf { it.isNotBlank() }

    enum class SendState { WAITING, QUEUED, SENT, DELIVERED, FAILED, CANCELLED, EXPIRED, UNKNOWN }

    fun sendState(status: String): SendState = when (status.lowercase()) {
        "awaiting_confirmation" -> SendState.WAITING
        "queued", "sending" -> SendState.QUEUED
        "sent" -> SendState.SENT
        "delivered" -> SendState.DELIVERED
        "failed" -> SendState.FAILED
        "cancelled", "canceled", "denied" -> SendState.CANCELLED
        "expired" -> SendState.EXPIRED
        else -> SendState.UNKNOWN
    }

    fun dayStart(ms: Long, cal: Calendar = Calendar.getInstance()): Long {
        cal.timeInMillis = ms
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun forwardedToday(items: List<HistoryItem>, nowMs: Long, cal: Calendar = Calendar.getInstance()): Int {
        val today = dayStart(nowMs, cal)
        return items.count { !it.skipped && dayStart(it.timestamp, cal) == today }
    }

    /** Groups newest-first items by local day, keeping order. */
    fun <T> groupByDay(items: List<T>, time: (T) -> Long, cal: Calendar = Calendar.getInstance()): List<Pair<Long, List<T>>> {
        val out = LinkedHashMap<Long, MutableList<T>>()
        for (item in items) out.getOrPut(dayStart(time(item), cal)) { mutableListOf() }.add(item)
        return out.map { it.key to it.value }
    }
}
