package com.pocketotp.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SimInfoSlotTest {
    @Test
    fun physicalIndexZeroIsSlotOne() {
        assertEquals(1, SimInfo.uploadSlot(0))
        assertEquals(2, SimInfo.uploadSlot(1))
    }

    @Test
    fun uploadJsonSendsOneBasedSlotAndZeroBasedIndex() {
        val payload = RelayClient.Payload(
            code = "000000",
            sender = "106",
            brand = "Test",
            source = "Test",
            body = "code 000000",
            timestamp = 0L,
            subscriptionId = 11,
            slot = SimInfo.uploadSlot(0),
            simSlotIndex = 0,
            carrier = "China Unicom",
            test = true,
        )
        val o = JSONObject(payload.toJson())
        assertEquals(1, o.getInt("slot"))
        assertEquals(0, o.getInt("simSlotIndex"))
        assertEquals(11, o.getInt("subscriptionId"))
        assertFalse(o.getInt("slot") == o.getInt("simSlotIndex"))
    }
}
