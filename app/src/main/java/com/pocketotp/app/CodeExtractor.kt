package com.pocketotp.app

data class ExtractedSms(
    val code: String?,
    val brand: String?,
)

/**
 * Pulls a one-time code and a sender brand out of an SMS body.
 *
 * Order of attempts (first hit wins):
 *  1. Letter+digit code right after a strong keyword ("code is AB12CD", "验证码：K7Q2M9").
 *  2. 4–8 digits, or 3+3 / 4+4 digits split by "-" or a space, near a keyword.
 *     Split codes are returned without the separator ("123-456" -> "123456").
 *  3. Any standalone numeric code in a message that mentions a keyword.
 *
 * Brand: 【Brand】, then [Brand], then a leading "Brand:", then "Your Brand code is".
 */
object CodeExtractor {
    private const val ZH_KEYWORDS = "验证码|校验码|动态码|動態碼|确认码|確認碼|檢驗碼|驗證碼|安全码|登录码"
    private const val EN_STRONG =
        "verification code|verify code|security code|login code|log-in code|sign-in code|" +
            "authentication code|auth code|access code|one[ -]?time (?:pass)?code|one[ -]?time password|" +
            "\\botp\\b|\\bpasscode\\b"

    private val keyword = Regex(
        "$ZH_KEYWORDS|$EN_STRONG|\\bcode\\b",
        RegexOption.IGNORE_CASE,
    )

    // Numeric code: 3+3 or 4+4 split by one "-" or space, or 4–8 plain digits.
    private const val NUM =
        "(?<!\\d)(?<!\\d[- ])(\\d{3}[- ]\\d{3}|\\d{4}[- ]\\d{4})(?![- ]?\\d)|(?<!\\d)(\\d{4,8})(?!\\d)"

    private val codeAfterKeyword = Regex(
        "(?:$ZH_KEYWORDS|verification code|verify code|security code|login code|sign-in code|" +
            "authentication code|auth code|access code|passcode|otp|code)" +
            "[^\\n\\d]{0,20}?(?:$NUM)",
        RegexOption.IGNORE_CASE,
    )
    private val codeBeforeKeyword = Regex(
        "(?:$NUM)[^\\n]{0,20}?(?:$ZH_KEYWORDS|verification code|verify code|security code|login code|" +
            "sign-in code|passcode|otp|code)",
        RegexOption.IGNORE_CASE,
    )
    private val anyStandaloneCode = Regex(NUM)

    /** Letter+digit codes only right after "keyword [is|为|是] [:]". */
    private val alnumAfterKeyword = Regex(
        "(?:$ZH_KEYWORDS|verification code|verify code|security code|login code|sign-in code|" +
            "authentication code|auth code|access code|passcode|\\botp|\\bcode)" +
            "\\s*(?:is|为|是|為)?\\s*[:：]?\\s*[\"'“「]?([A-Za-z0-9]{4,8})(?![A-Za-z0-9])",
        RegexOption.IGNORE_CASE,
    )
    private val promo = Regex(
        "promo|coupon|discount|voucher|referral|invite|gift|% off|优惠|兑换|邀请|折扣|红包",
        RegexOption.IGNORE_CASE,
    )
    private val unitLike = Regex("^\\d+(?:s|sec|secs|m|min|mins|h|hr|hrs|am|pm)$", RegexOption.IGNORE_CASE)

    private val brandZh = Regex("【([^】]{1,40})】")
    private val brandSquare = Regex("\\[([^\\[\\]\\n]{1,30})]")
    private val brandColon = Regex(
        "^\\s*(?:<#>\\s*)?([A-Za-z][A-Za-z0-9&.'+-]*(?: [A-Za-z0-9&.'+-]+){0,2})\\s*[:：]\\s",
    )
    private val brandYour = Regex(
        "\\b[Yy]our ([A-Z][A-Za-z0-9&.'+-]*(?: [A-Z][A-Za-z0-9&.'+-]*){0,2}) " +
            "(?:(?:[Vv]erification|[Ss]ecurity|[Ll]ogin|[Ss]ign-in|[Aa]ccess|[Oo]ne-time|[Aa]uthentication) )?" +
            "(?:code|Code|OTP|PIN|passcode)\\b",
    )
    private val notBrand = setOf(
        "code", "otp", "pin", "passcode", "password", "verification", "verification code", "security",
        "security code", "login", "login code", "sign-in", "access", "one-time", "authentication", "your",
        "note", "notice", "alert", "reminder", "warning", "info", "msg", "sms", "re", "fw", "fwd", "tip",
        "important", "urgent", "hi", "hello", "dear", "ref", "order", "id", "auth", "the", "a", "new",
    )

    fun extract(body: String): ExtractedSms {
        val text = normalizeDigits(body)
        val brand = extractBrand(text)
        val alnum = alnumCode(text)
        if (alnum != null) return ExtractedSms(alnum, brand)
        val direct = codeAfterKeyword.find(text)?.let(::numeric)
            ?: codeBeforeKeyword.find(text)?.let(::numeric)
        if (direct != null) return ExtractedSms(direct, brand)
        if (!keyword.containsMatchIn(text)) return ExtractedSms(null, brand)
        val candidates = anyStandaloneCode.findAll(text).map(::numeric).toList()
        return ExtractedSms(preferTypicalCode(candidates), brand)
    }

    fun looksLikeVerification(body: String, extracted: ExtractedSms): Boolean =
        extracted.code != null || keyword.containsMatchIn(normalizeDigits(body))

    fun extractBrand(body: String): String? {
        brandZh.find(body)?.groupValues?.get(1)?.trim()?.ifEmpty { null }?.let { return it }
        brandSquare.findAll(body).map { it.groupValues[1].trim() }.firstOrNull(::plausibleBrand)?.let { return it }
        brandColon.find(body)?.groupValues?.get(1)?.trim()?.takeIf(::plausibleBrand)?.let { return it }
        brandYour.find(body)?.groupValues?.get(1)?.trim()?.takeIf(::plausibleBrand)?.let { return it }
        return null
    }

    private fun plausibleBrand(s: String): Boolean {
        if (s.isEmpty() || s.none { it.isLetter() }) return false
        val lower = s.lowercase()
        if (lower in notBrand) return false
        if (lower.substringBefore(' ') in notBrand || lower.substringAfterLast(' ') in notBrand) return false
        return !keyword.containsMatchIn(s)
    }

    private fun alnumCode(text: String): String? {
        if (promo.containsMatchIn(text)) return null
        for (m in alnumAfterKeyword.findAll(text)) {
            val token = m.groupValues[1]
            if (token.none { it.isDigit() } || token.none { it.isLetter() }) continue
            if (unitLike.matches(token)) continue
            return token
        }
        return null
    }

    private fun numeric(m: MatchResult): String {
        // Groups: 1 = split code, 2 = plain code (per NUM alternative).
        val g = m.groupValues
        val split = g.getOrNull(1).orEmpty()
        val plain = g.getOrNull(2).orEmpty()
        return (split.ifEmpty { plain }).replace("-", "").replace(" ", "")
    }

    private fun normalizeDigits(s: String): String =
        buildString(s.length) {
            for (ch in s) append(if (ch in '０'..'９') '0' + (ch - '０') else ch)
        }

    private fun preferTypicalCode(candidates: List<String>): String? {
        if (candidates.isEmpty()) return null
        return candidates.firstOrNull { it.length == 6 }
            ?: candidates.firstOrNull { it.length == 4 }
            ?: candidates.firstOrNull { it.length == 8 }
            ?: candidates.first()
    }
}
