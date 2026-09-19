package com.youven.quranaccessible.data

import org.json.JSONArray
import org.json.JSONObject

/** Recover incomplete by_page responses from the same provider's by_chapter data. */
object PageRecovery {
    private fun verses(responses: List<String>): List<JSONObject> {
        require(responses.isNotEmpty())
        val roots = responses.map(::JSONObject)
        roots.forEachIndexed { index, root ->
            val pagination = root.getJSONObject("pagination")
            require(pagination.getInt("current_page") == index + 1)
            require(pagination.getInt("total_pages") == roots.size)
            require(pagination.getInt("total_records") == expected(responses))
        }
        return roots.flatMap { root ->
            val array = root.getJSONArray("verses")
            (0 until array.length()).map(array::getJSONObject)
        }.also { list -> require(list.map { it.getString("verse_key") }.distinct().size == list.size) }
    }

    private fun expected(responses: List<String>) =
        JSONObject(responses.first()).getJSONObject("pagination").getInt("total_records")

    fun chapters(page: Int): List<Int> = MadaniPage.spec(page).chapters

    fun chapters(responses: List<String>): List<Int> {
        val entries = verses(responses)
        val firstVerse = entries.firstOrNull()
        val firstWords = firstVerse?.getJSONArray("words")
        val threshold = if (firstVerse?.optInt("verse_number") == 1) 2 else 1
        val missingTop = firstWords != null && firstWords.length() > 0 && firstWords.getJSONObject(0).getInt("line_number") > threshold
        require(entries.isNotEmpty() && (entries.size != expected(responses) || missingTop)) { "Not an incomplete page response" }
        val chapters = entries.map { it.getString("verse_key").substringBefore(':').toInt() }
        require(chapters.all { it in 1..114 })
        var min = chapters.min()
        val max = chapters.max()
        if (firstVerse?.getInt("verse_number") == 1 && firstWords != null && firstWords.length() > 0 &&
            firstWords.getJSONObject(0).getInt("line_number") > 2 && min > 1) {
            min -= 1
        }
        return (min..max).toList()
    }

    fun rebuild(page: Int, original: List<String>, chapterResponses: Map<Int, List<String>>): String {
        val requiredChapters = chapters(page).toSet()
        require(chapterResponses.keys.containsAll(requiredChapters))
        val restored = chapterResponses.flatMap { (chapter, responses) ->
            val entries = verses(responses)
            require(entries.size == expected(responses)) { "Incomplete chapter response" }
            require(entries.all { it.getString("verse_key").substringBefore(':').toInt() == chapter })
            entries.filter { verse ->
                val words = verse.getJSONArray("words")
                (0 until words.length()).any { words.getJSONObject(it).getInt("page_number") == page }
            }
        }.sortedWith(compareBy({ it.getString("verse_key").substringBefore(':').toInt() },
            { it.getString("verse_key").substringAfter(':').toInt() }))
        val keys = restored.map { it.getString("verse_key") }.toSet()
        val originalForPage = verses(original).filter { verse ->
            val words = verse.getJSONArray("words")
            (0 until words.length()).any { words.getJSONObject(it).getInt("page_number") == page }
        }
        require(originalForPage.all { it.getString("verse_key") in keys })
        val body = JSONObject().put("verses", JSONArray(restored)).put("pagination", JSONObject()
            .put("current_page", 1).put("total_pages", 1).put("total_records", restored.size)
            .put("next_page", JSONObject.NULL)).toString()
        PageParser.parse(page, listOf(body)) // Never relax layout, duplicate or completeness checks.
        return body
    }
}
