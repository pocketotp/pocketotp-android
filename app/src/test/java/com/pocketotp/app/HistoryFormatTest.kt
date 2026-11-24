package com.pocketotp.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class HistoryFormatTest {
    private fun cal() = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"))

    @Test
    fun masksAllButLastTwo() {
        assertEquals("••••13", HistoryFormat.maskCode("482913"))
        assertEquals("••", HistoryFormat.maskCode("12"))
        assertEquals("—", HistoryFormat.maskCode("—"))
        assertEquals("—", HistoryFormat.maskCode(""))
    }

    @Test
    fun blockedItemsAreNeverRevealable() {
        val ok = HistoryItem("482913", "京东", 0L, skipped = false, reason = "ok")
        val blocked = HistoryItem("—", "工商银行", 0L, skipped = true, reason = "blacklist:银行")
        val failed = HistoryItem("123456", "x", 0L, skipped = true, reason = "http_401")
        assertTrue(HistoryFormat.canReveal(ok))
        assertFalse(HistoryFormat.canReveal(blocked))
        assertFalse(HistoryFormat.canReveal(failed))
        assertEquals(HistoryFormat.Outcome.BLOCKED, HistoryFormat.outcome(blocked))
        assertEquals(HistoryFormat.Outcome.FAILED, HistoryFormat.outcome(failed))
        assertEquals("银行", HistoryFormat.blockedKeyword(blocked))
    }

    @Test
    fun mapsSendStatuses() {
        assertEquals(HistoryFormat.SendState.DELIVERED, HistoryFormat.sendState("delivered"))
        assertEquals(HistoryFormat.SendState.QUEUED, HistoryFormat.sendState("queued"))
        assertEquals(HistoryFormat.SendState.CANCELLED, HistoryFormat.sendState("cancelled"))
        assertEquals(HistoryFormat.SendState.UNKNOWN, HistoryFormat.sendState("weird"))
    }

    @Test
    fun countsTodayAndGroupsByDay() {
        val c = cal()
        c.set(2026, Calendar.NOVEMBER, 24, 12, 0, 0)
        val now = c.timeInMillis
        val items = listOf(
            HistoryItem("1", "a", now - 60_000L),
            HistoryItem("2", "b", now - 2 * 3_600_000L, skipped = true, reason = "blacklist:x"),
            HistoryItem("3", "c", now - 24 * 3_600_000L),
        )
        assertEquals(1, HistoryFormat.forwardedToday(items, now, cal()))
        val groups = HistoryFormat.groupByDay(items, { it.timestamp }, cal())
        assertEquals(2, groups.size)
        assertEquals(2, groups[0].second.size)
    }
}
