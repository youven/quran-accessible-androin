package com.youven.quranaccessible.data

import org.json.JSONObject

data class QuranWord(val glyph: String, val text: String, val line: Int, val verse: String, val position: Int)
data class ChapterStart(val chapter: Int, val firstLine: Int) {
    val headerLine get() = firstLine - if (chapter == 1 || chapter == 9) 1 else 2
    val basmalaLine get() = if (chapter == 1 || chapter == 9) null else firstLine - 1
}
data class TextPage(val number: Int, val words: List<QuranWord>, val starts: List<ChapterStart>) {
    val lines get() = words.groupBy { it.line }.toSortedMap()
}

/** Retains provider ordering, ornaments, page boundaries and line assignments. */
object PageParser {
    fun parse(page: Int, responses: List<String>): TextPage {
        require(page in 1..604 && responses.isNotEmpty())
        val words = mutableListOf<QuranWord>()
        val starts = mutableListOf<ChapterStart>()
        val seen = mutableSetOf<String>()
        var total = -1
        responses.forEachIndexed { index, body ->
            val root = JSONObject(body)
            val pagination = root.getJSONObject("pagination")
            require(pagination.getInt("current_page") == index + 1)
            require(pagination.getInt("total_pages") == responses.size)
            val count = pagination.getInt("total_records")
            if (total == -1) total = count else require(total == count)
            val verses = root.getJSONArray("verses")
            for (i in 0 until verses.length()) {
                val verse = verses.getJSONObject(i)
                val key = verse.getString("verse_key")
                require(seen.add(key)) { "Duplicate verse" }
                val source = verse.getJSONArray("words")
                for (j in 0 until source.length()) {
                    val word = source.getJSONObject(j)
                    if (word.getInt("page_number") != page) continue
                    val glyph = word.getString("code_v2")
                    val text = word.getString("text_uthmani")
                    val line = word.getInt("line_number")
                    val position = word.getInt("position")
                    require(glyph.isNotBlank() && text.isNotBlank() && line in 1..15)
                    require(words.lastOrNull()?.line?.let { it <= line } != false)
                    words += QuranWord(glyph, text, line, key, position)
                    if (verse.getInt("verse_number") == 1 && position == 1) {
                        val start = ChapterStart(key.substringBefore(':').toInt(), line)
                        require(start.headerLine >= 1)
                        starts += start
                    }
                }
            }
        }
        require(seen.size == total && words.isNotEmpty()) { "Incomplete page" }
        val occupied = words.map { it.line }.toSet()
        starts.forEach { require(it.headerLine !in occupied && it.basmalaLine !in occupied) }
        return TextPage(page, words, starts)
    }
}
