package com.youven.quranaccessible.data

/** Madani 1421H, QCF V2: never mix page fonts or editions. */
object MadaniPage {
    const val COUNT = 604
    const val EDITION = "qcf-v2-mushaf-1"

    fun fontUrl(page: Int): String {
        require(page in 1..COUNT)
        return "https://verses.quran.foundation/fonts/quran/hafs/v2/ttf/p$page.ttf"
    }

    fun restoredPage(value: Int): Int = value.coerceIn(1, COUNT)

    /** Accept Arabic, Persian and ASCII decimal digits without accepting signs or decimals. */
    fun parse(input: String): Int? {
        val trimmed = input.trim()
        if (trimmed.isEmpty() || trimmed.length > 3) return null
        val digits = trimmed.map { it.digitToIntOrNull() ?: return null }.joinToString("")
        return digits.toIntOrNull()?.takeIf { it in 1..COUNT }
    }
}
