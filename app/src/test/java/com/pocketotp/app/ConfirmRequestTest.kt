package com.pocketotp.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfirmRequestTest {
    @Test
    fun parsesConfirmRequestAndExpiry() {
        val expires = "2026-11-23T16:05:00.000Z"
        val obj = JSONObject()
            .put("id", "abc-123")
            .put("to", "13800138000")
            .put("toMasked", "****8000")
            .put("sim", JSONObject().put("kind", "slot").put("slot", 1).put("label", "unicom"))
            .put("text", "hello secret")
            .put("textLength", 12)
            .put("dryRun", false)
            .put("createdAt", "2026-11-23T16:00:00.000Z")
            .put("expiresAt", expires)
        val req = ConfirmRequest.fromJson(obj)!!
        assertEquals("abc-123", req.id)
        assertEquals("13800138000", req.to)
        assertEquals("****8000", req.toMasked)
        assertEquals("unicom", req.simLabel)
        assertEquals("hello secret", req.text)
        assertEquals(12, req.textLength)
        assertFalse(req.dryRun)
        assertEquals(ConfirmRequest.parseIsoMillis(expires), req.expiresAtMs)
        assertFalse(req.isExpired(req.expiresAtMs!! - 1))
        assertTrue(req.isExpired(req.expiresAtMs!!))
        assertTrue(req.isExpired(req.expiresAtMs!! + 1_000))
    }

    @Test
    fun missingToMaskedFallsBackAndOtherSim() {
        val obj = JSONObject().put("id", "x").put("to", "+8613800138000").put("text", "hi")
        val req = ConfirmRequest.fromJson(obj)!!
        assertEquals("+****8000", req.toMasked)
        assertEquals("other_sim", ConfirmRequest.maskFallback("other_sim"))
        assertNull(ConfirmRequest.fromJson(JSONObject().put("to", "13800138000")))
        assertNull(ConfirmRequest.fromJson(null))
    }

    @Test
    fun parseListIsIdempotentAndSkipsBlankIds() {
        val arr = JSONArray()
            .put(JSONObject().put("id", "one").put("to", "13800138000").put("text", "a"))
            .put(JSONObject().put("id", "").put("to", "13800138000").put("text", "b"))
            .put(JSONObject().put("id", "one").put("to", "13800138000").put("text", "a"))
        val list = ConfirmRequest.parseList(arr)
        assertEquals(2, list.size)
        assertEquals("one", list[0].id)
        assertEquals("one", list[1].id)
    }

    @Test
    fun dryRunAliasAndSimSlotLabel() {
        val obj = JSONObject()
            .put("id", "d")
            .put("to", "other_sim")
            .put("dry_run", true)
            .put("sim", JSONObject().put("slot", 2))
            .put("text", "preview")
        val req = ConfirmRequest.fromJson(obj)!!
        assertTrue(req.dryRun)
        assertEquals("SIM 2", req.simLabel)
        assertEquals("other_sim", req.toMasked)
    }
}
