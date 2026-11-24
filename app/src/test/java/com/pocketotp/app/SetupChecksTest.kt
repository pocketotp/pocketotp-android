package com.pocketotp.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupChecksTest {
    private val all = SetupChecks.Inputs(true, true, true, true, true, true, isSamsung = true)

    @Test
    fun samsungAddsManualStep() {
        val checks = SetupChecks.build(all)
        assertEquals(7, checks.size)
        assertEquals(SetupChecks.Status.MANUAL, checks.last().status)
        assertEquals(6, SetupChecks.autoCount(checks))
        assertEquals(6, SetupChecks.doneCount(checks))
        assertEquals(6, SetupChecks.build(all.copy(isSamsung = false)).size)
    }

    @Test
    fun onlyRequiredItemsBlock() {
        assertTrue(SetupChecks.requiredOk(SetupChecks.build(all.copy(batteryUnrestricted = false, canDrawOverlays = false, fullScreenAllowed = false))))
        assertFalse(SetupChecks.requiredOk(SetupChecks.build(all.copy(smsGranted = false))))
        assertFalse(SetupChecks.requiredOk(SetupChecks.build(all.copy(notificationsGranted = false))))
    }
}
