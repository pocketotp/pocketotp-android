package com.pocketotp.app

object Blacklist {
    fun match(keywords: List<String>, vararg fields: String?): String? {
        val haystack = fields.filterNotNull().joinToString("\n")
        if (haystack.isBlank()) return null
        for (raw in keywords) {
            val kw = raw.trim()
            if (kw.isEmpty()) continue
            if (haystack.contains(kw, ignoreCase = true)) return kw
        }
        return null
    }
}
