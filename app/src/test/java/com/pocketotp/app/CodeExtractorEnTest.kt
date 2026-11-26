package com.pocketotp.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** English SMS samples. All codes, brands and numbers are made up. */
class CodeExtractorEnTest {
    private data class Case(val body: String, val code: String?, val brand: String?)

    private val cases = listOf(
        // Plain numeric
        Case("Your verification code is 482913. It expires in 10 minutes.", "482913", null),
        Case("Use 7351 as your login code for Examplely.", "7351", null),
        Case("Your one-time password is 66120384. Do not share it.", "66120384", null),
        Case("OTP: 905172 (valid 5 min)", "905172", null),
        Case("Security code 31415. Never share this code with anyone.", "31415", null),
        // Separated
        Case("Your WhatsUpp code: 123-456. Don't share this code with others.", "123456", "WhatsUpp"),
        Case("Your Examplegram code is 123 456", "123456", "Examplegram"),
        Case("Your sign-in code is 4821-7730. It expires in 15 minutes.", "48217730", null),
        Case("Verification code: 908 112", "908112", null),
        Case("Acme: 551-203 is your verification code.", "551203", "Acme"),
        Case("G-772041 is your Gloogle verification code.", "772041", "Gloogle"),
        // Alphanumeric
        Case("Your verification code is K7Q2M9.", "K7Q2M9", null),
        Case("[Steamy] Your login code: AB12CD", "AB12CD", "Steamy"),
        Case("Your Fooapp code is 4F9X2. Valid for 10 mins.", "4F9X2", "Fooapp"),
        Case("Passcode: ZX81QP - do not share", "ZX81QP", null),
        // Brand forms
        Case("[Discordia] Your security code is 246810.", "246810", "Discordia"),
        Case("Ubar: Your code is 1357. Never share this code.", "1357", "Ubar"),
        Case("Your Twitch Clone verification code is 864209", "864209", "Twitch Clone"),
        Case("<#> Your Example App code is 302918 FA+9qCX9VSu", "302918", "Example App"),
        Case("Lyfted: 7720 is your Lyfted code. Reply STOP to opt out.", "7720", "Lyfted"),
        Case("Your Shopsy login code is 903311", "903311", "Shopsy"),
        Case("Microsift account: Security code 5512", "5512", "Microsift account"),
        Case("Your code is 663300. -Team Example", "663300", null),
        Case("Code: 774411", "774411", null),
        Case("[Notion-ish] 009876 is your login code", "009876", "Notion-ish"),
        // Full-width digits are normalized
        Case("Your code is １２３４５６", "123456", null),
        // Should not extract a code
        Case("Your order #12345 has shipped and will arrive Friday.", null, null),
        Case("Use promo code SAVE20 for 20% off your next ride!", null, null),
        Case("Hi Sam, are we still on for 7pm? Call me at 555-0142.", null, null),
        Case("Reminder: your appointment is on 2026-12-03 at 09:30.", null, null),
        Case("Your verification code expires in 5min. Request a new one in the app.", null, null),
        Case("Dear customer: your package is out for delivery.", null, null),
        Case("Weather alert: high winds expected after 6pm.", null, null),
        Case("Thanks for joining! Reply HELP for help or STOP to cancel.", null, null),
    )

    @Test
    fun atLeastThirtySamples() {
        assertTrue(cases.size >= 30)
    }

    @Test
    fun extractsCodeAndBrand() {
        val failures = cases.mapNotNull { c ->
            val got = CodeExtractor.extract(c.body)
            if (got.code == c.code && got.brand == c.brand) null
            else "${c.body}\n  expected code=${c.code} brand=${c.brand}\n  got      code=${got.code} brand=${got.brand}"
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun alphanumericNeedsKeywordRightBeforeIt() {
        assertNull(CodeExtractor.extract("Meet at gate B12 near exit A4 code").code)
        assertNull(CodeExtractor.extract("Your Wi-Fi password is HOME2024, enjoy").code)
    }

    @Test
    fun nonVerificationTextIsNotUploaded() {
        val body = "Weather alert: high winds expected after 6pm."
        assertEquals(false, CodeExtractor.looksLikeVerification(body, CodeExtractor.extract(body)))
    }
}
