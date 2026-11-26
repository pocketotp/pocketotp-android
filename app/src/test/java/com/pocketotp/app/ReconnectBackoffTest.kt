package com.pocketotp.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ReconnectBackoffTest {
    @Test
    fun doublesUpToTheCap() {
        val b = ReconnectBackoff()
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L), List(7) { b.next() })
    }

    @Test
    fun resetAfterASuccessfulOpenStartsOver() {
        val b = ReconnectBackoff()
        repeat(10) { b.next() }
        b.reset()
        assertEquals(1_000L, b.next())
        assertEquals(2_000L, b.next())
    }
}
