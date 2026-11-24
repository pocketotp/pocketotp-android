package com.pocketotp.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RelayStateTest {
    @Test
    fun revokedOnlyOnExplicitError() {
        assertTrue(RelayState.isRevoked(401, "device_revoked"))
        assertFalse(RelayState.isRevoked(401, "unauthorized")) // unknown token / old relay: keep token
        assertFalse(RelayState.isRevoked(401, null))
        assertFalse(RelayState.isRevoked(403, "device_revoked"))
        assertTrue(RelayState.isRevokedClose(4001, "device_revoked"))
        assertTrue(RelayState.isRevokedClose(4001, ""))
        assertFalse(RelayState.isRevokedClose(1000, "stop"))
        assertEquals("device_revoked", RelayState.errorOf("""{"ok":false,"error":"device_revoked"}"""))
        assertNull(RelayState.errorOf("<html>"))
        assertNull(RelayState.errorOf(""))
    }

    @Test
    fun smsPlanErrors() {
        assertEquals(RelayState.Issue.QUOTA_EXCEEDED, RelayState.issueOf(402, "quota_exceeded"))
        assertEquals(RelayState.Issue.NUMBER_NOT_ON_PLAN, RelayState.issueOf(403, "number_not_on_plan"))
        assertEquals(RelayState.Issue.DEVICE_SUSPENDED, RelayState.issueOf(402, "device_suspended"))
        assertNull(RelayState.issueOf(402, null))
        assertNull(RelayState.issueOf(403, "forbidden"))
        assertEquals(2, RelayState.slotOf("""{"ok":false,"error":"number_not_on_plan","slot":2}"""))
        assertNull(RelayState.slotOf("""{"error":"number_not_on_plan"}"""))
    }

    @Test
    fun presenceFieldsOptional() {
        // Old single-user relay: {ok, t} only -> nothing to apply.
        assertNull(RelayState.parsePresence("""{"ok":true,"t":1}"""))
        assertNull(RelayState.parsePresence(""))
        val p = RelayState.parsePresence("""{"ok":true,"t":1,"suspended":false,"plan":"free","allowedSlots":[1]}""")!!
        assertEquals(false, p.suspended)
        assertEquals("free", p.plan)
        assertEquals(listOf(1), p.allowedSlots)
        val s = RelayState.parsePresence("""{"ok":true,"suspended":true,"plan":"free","allowedSlots":[]}""")!!
        assertEquals(true, s.suspended)
        assertEquals(emptyList<Int>(), s.allowedSlots)
        val partial = RelayState.parsePresence("""{"ok":true,"allowedSlots":[2,1,2]}""")!!
        assertNull(partial.suspended)
        assertNull(partial.plan)
        assertEquals(listOf(1, 2), partial.allowedSlots)
    }

    @Test
    fun slotStorageRoundTrip() {
        assertNull(RelayState.decodeSlots(RelayState.storeSlots(null)))
        assertEquals(emptyList<Int>(), RelayState.decodeSlots(RelayState.storeSlots(emptyList())))
        assertEquals(listOf(1, 2), RelayState.decodeSlots(RelayState.storeSlots(listOf(1, 2))))
    }

    @Test
    fun claimErrorMessages() {
        assertEquals(RelayState.ClaimError.CODE, RelayState.claimErrorOf("code_invalid"))
        assertEquals(RelayState.ClaimError.CODE, RelayState.claimErrorOf("bad_request"))
        assertEquals(RelayState.ClaimError.CODE, RelayState.claimErrorOf("code_expired"))
        assertEquals(RelayState.ClaimError.DEVICE_LIMIT, RelayState.claimErrorOf("device_limit"))
        assertEquals(RelayState.ClaimError.RATE, RelayState.claimErrorOf("rate_limited"))
        assertEquals(RelayState.ClaimError.NETWORK, RelayState.claimErrorOf("network"))
        assertEquals(RelayState.ClaimError.OTHER, RelayState.claimErrorOf("http_500"))
    }
}
