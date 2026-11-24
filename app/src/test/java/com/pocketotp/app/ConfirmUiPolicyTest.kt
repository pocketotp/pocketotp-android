package com.pocketotp.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfirmUiPolicyTest {
    @Test
    fun countdownRoundsUpAndFormats() {
        assertEquals(300L, ConfirmUiPolicy.remainingSeconds(300_000L, 0L))
        assertEquals(1L, ConfirmUiPolicy.remainingSeconds(1_000L, 1L))
        assertEquals(0L, ConfirmUiPolicy.remainingSeconds(1_000L, 5_000L))
        assertEquals("4:32", ConfirmUiPolicy.clock(272L))
        assertEquals("0:05", ConfirmUiPolicy.clock(5L))
        assertEquals("—", ConfirmUiPolicy.clock(null))
    }

    @Test
    fun allowNeedsOpenGuardLiveDeadlineAndIdle() {
        assertFalse(ConfirmUiPolicy.canAllow(10_000L, 0L, 999L, busy = false))
        assertTrue(ConfirmUiPolicy.canAllow(10_000L, 0L, 1_000L, busy = false))
        assertFalse(ConfirmUiPolicy.canAllow(10_000L, 0L, 5_000L, busy = true))
        assertFalse(ConfirmUiPolicy.canAllow(10_000L, 10_000L, 5_000L, busy = false))
        assertFalse(ConfirmUiPolicy.canAllow(null, 0L, 5_000L, busy = false))
    }
}
