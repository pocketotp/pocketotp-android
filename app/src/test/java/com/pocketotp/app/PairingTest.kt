package com.pocketotp.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingTest {
    private val now = 1_764_000_000L

    private fun ok(s: String): Pairing.Offer {
        val p = Pairing.parse(s, now)
        assertTrue("expected Ok for $s but was $p", p is Pairing.Parsed.Ok)
        return (p as Pairing.Parsed.Ok).offer
    }

    private fun err(s: String): Pairing.Reason = (Pairing.parse(s, now) as Pairing.Parsed.Error).reason

    @Test
    fun parsesCanonicalLink() {
        val o = ok("pocketotp://pair?v=1&relay=https%3A%2F%2Frelay.pocketotp.com&code=7K3M-Q9TD-2XHF&exp=${now + 600}")
        assertEquals("https://relay.pocketotp.com", o.relay)
        assertEquals("7K3MQ9TD2XHF", o.code)
        assertEquals("7K3M-Q9TD-2XHF", o.displayCode)
        assertEquals("relay.pocketotp.com", o.relayHost)
        assertEquals(now + 600, o.expiresAtSec)
    }

    @Test
    fun versionDefaultsAndUnknownParamsIgnored() {
        val o = ok("POCKETOTP://pair?relay=https://r.example.com/&code=7k3mq9td2xhf&foo=bar")
        assertEquals("https://r.example.com", o.relay)
        assertEquals("7K3MQ9TD2XHF", o.code)
    }

    @Test
    fun rejectsBadInput() {
        assertEquals(Pairing.Reason.NOT_POCKETOTP, err("https://example.com/pair?code=x"))
        assertEquals(Pairing.Reason.NOT_POCKETOTP, err("pocketotp://config?relay=https://a.b&code=7K3MQ9TD"))
        assertEquals(Pairing.Reason.UNSUPPORTED_VERSION, err("pocketotp://pair?v=2&relay=https://a.b&code=7K3MQ9TD"))
        assertEquals(Pairing.Reason.BAD_RELAY, err("pocketotp://pair?relay=http://a.b&code=7K3MQ9TD"))
        assertEquals(Pairing.Reason.BAD_RELAY, err("pocketotp://pair?relay=https://user:pw@a.b&code=7K3MQ9TD"))
        assertEquals(Pairing.Reason.BAD_RELAY, err("pocketotp://pair?code=7K3MQ9TD"))
        assertEquals(Pairing.Reason.BAD_CODE, err("pocketotp://pair?relay=https://a.b&code=SHORT"))
        assertEquals(Pairing.Reason.BAD_CODE, err("pocketotp://pair?relay=https://a.b&code=7K3MQ9TD!!"))
        assertEquals(Pairing.Reason.EXPIRED, err("pocketotp://pair?relay=https://a.b&code=7K3MQ9TD&exp=${now - 1}"))
    }

    @Test
    fun normalizesLookalikes() {
        assertEquals("01117K3M", Pairing.normalizeCode("oiL1-7k3m"))
        assertNull(Pairing.normalizeCode("ABCDEFGU")) // U is not in the alphabet
    }

    @Test
    fun claimResponseHandling() {
        val offer = ok("pocketotp://pair?relay=https://relay.pocketotp.com&code=7K3MQ9TD2XHF")
        val good = Pairing.parseClaimResponse(offer, 200,
            """{"token":"dev_abc","deviceId":"d1","relay":"https://relay.pocketotp.com/v1","account":{"label":"Jimmy"}}""")
        val c = (good as Pairing.ClaimResult.Ok).claimed
        assertEquals("dev_abc", c.token)
        assertEquals("https://relay.pocketotp.com/v1", c.relay)
        assertEquals("Jimmy", c.accountLabel)

        // A reply pointing at a different host is ignored.
        val moved = Pairing.parseClaimResponse(offer, 200, """{"token":"t","relay":"https://evil.example"}""")
        assertEquals("https://relay.pocketotp.com", (moved as Pairing.ClaimResult.Ok).claimed.relay)

        assertEquals("code_expired", (Pairing.parseClaimResponse(offer, 410, """{"error":"code_expired"}""") as Pairing.ClaimResult.Failed).error)
        assertEquals("code_invalid", (Pairing.parseClaimResponse(offer, 404, "") as Pairing.ClaimResult.Failed).error)
        assertEquals("device_limit", (Pairing.parseClaimResponse(offer, 402, "") as Pairing.ClaimResult.Failed).error)
        assertEquals("device_limit", (Pairing.parseClaimResponse(offer, 402, """{"ok":false,"error":"device_limit"}""") as Pairing.ClaimResult.Failed).error)
        assertEquals("bad_request", (Pairing.parseClaimResponse(offer, 400, "") as Pairing.ClaimResult.Failed).error)
        assertEquals("bad_response", (Pairing.parseClaimResponse(offer, 200, "{}") as Pairing.ClaimResult.Failed).error)
    }

    @Test
    fun claimRequestCarriesNoSecretsBeyondCode() {
        val json = org.json.JSONObject(Pairing.claimRequestJson("7K3MQ9TD2XHF", "SM-F741", "samsung SM-F741", "2.5.0"))
        assertEquals(1, json.getInt("v"))
        assertEquals("7K3MQ9TD2XHF", json.getString("code"))
        assertEquals("android", json.getJSONObject("device").getString("platform"))
        assertEquals("confirm", json.getJSONObject("device").getJSONArray("caps").getString(0))
    }
}
