package com.pocketotp.app

data class ExtractedSms(
    val code: String?,
    val brand: String?,
)

object CodeExtractor {
    private val brandRegex = Regex("【([^】]{1,40})】")
    private val keyword = Regex(
        "验证码|校验码|动态码|動態碼|确认码|確認碼|檢驗碼|" +
            "verification code|verify code|security code|login code|" +
            "one[ -]?time (?:code|password)|\\botp\\b|\\bcode\\b",
        RegexOption.IGNORE_CASE,
    )
    private val codeAfterKeyword = Regex(
        "(?:验证码|校验码|动态码|動態碼|确认码|確認碼|檢驗碼|verification code|verify code|security code|login code|otp|code)" +
            "[^\\n\\d]{0,20}?(?<![\\d])(\\d{4,8})(?!\\d)",
        RegexOption.IGNORE_CASE,
    )
    private val codeBeforeKeyword = Regex(
        "(?<![\\d])(\\d{4,8})(?!\\d)[^\\n]{0,20}?(?:验证码|校验码|动态码|動態碼|确认码|確認碼|verification code|verify code|otp|code)",
        RegexOption.IGNORE_CASE,
    )
    private val anyStandaloneCode = Regex("(?<![\\d])(\\d{4,8})(?!\\d)")

    fun extract(body: String): ExtractedSms {
        val brand = brandRegex.find(body)?.groupValues?.get(1)?.trim()?.ifEmpty { null }
        val direct = codeAfterKeyword.find(body)?.groupValues?.get(1)
            ?: codeBeforeKeyword.find(body)?.groupValues?.get(1)
        if (direct != null) return ExtractedSms(direct, brand)
        if (!keyword.containsMatchIn(body)) return ExtractedSms(null, brand)
        val candidates = anyStandaloneCode.findAll(body).map { it.groupValues[1] }.toList()
        return ExtractedSms(preferTypicalCode(candidates), brand)
    }

    fun looksLikeVerification(body: String, extracted: ExtractedSms): Boolean =
        extracted.code != null || keyword.containsMatchIn(body)

    private fun preferTypicalCode(candidates: List<String>): String? {
        if (candidates.isEmpty()) return null
        return candidates.firstOrNull { it.length == 6 }
            ?: candidates.firstOrNull { it.length == 4 }
            ?: candidates.firstOrNull { it.length == 8 }
            ?: candidates.first()
    }
}
