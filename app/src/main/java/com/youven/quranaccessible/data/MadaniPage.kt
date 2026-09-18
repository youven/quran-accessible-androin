package com.youven.quranaccessible.data

/** One edition only: the KSU/Ayat Hafs page images, numbered 1 through 604. */
object MadaniPage {
    const val COUNT = 604
    const val EDITION = "ksu-hafs-png-big-v1"

    fun imageUrl(page: Int): String {
        require(page in 1..COUNT)
        return "https://quran.ksu.edu.sa/png_big/$page.png"
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
