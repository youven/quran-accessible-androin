package com.youven.quranaccessible.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PageParserTest {
    private fun fixture(page: Int) = javaClass.getResource("/page$page.json")!!.readText()
    @Test fun openingPagesKeepTheirOriginalEightLineLayout() {
        val first = PageParser.parse(1, listOf(fixture(1)))
        assertEquals(36, first.words.size)
        assertEquals((2..8).toList(), first.lines.keys.toList())
        assertEquals(1, first.starts.single().headerLine)
        assertNull(first.starts.single().basmalaLine)
        val second = PageParser.parse(2, listOf(fixture(2)))
        assertEquals(2, second.starts.single().basmalaLine)
        assertEquals(3, second.words.first().line)
    }
    @Test fun finalPageRetainsThreeChaptersAndVerseOrnaments() {
        val page = PageParser.parse(604, listOf(fixture(604)))
        assertEquals(listOf(1, 5, 10), page.starts.map { it.headerLine })
        assertEquals(listOf(2, 6, 11), page.starts.map { it.basmalaLine })
        assertEquals("114:6", page.words.last().verse)
        assertEquals(15, page.words.last().line)
        assertTrue(page.words.all { it.text.isNotBlank() && it.glyph.isNotBlank() })
    }
    @Test fun actualChapterTransitionsPreserveHeaderSpace() {
        val imran = PageParser.parse(50, listOf(fixture(50)))
        assertEquals(3, imran.starts.single().chapter)
        assertEquals(1, imran.starts.single().headerLine)
        val tawbah = PageParser.parse(187, listOf(fixture(187)))
        assertEquals(9, tawbah.starts.single().chapter)
        assertEquals(1, tawbah.starts.single().headerLine)
        assertNull(tawbah.starts.single().basmalaLine)
    }
    @Test fun tawbahHasNoExtraBasmala() {
        assertNull(ChapterStart(9, 2).basmalaLine)
        assertEquals(1, ChapterStart(9, 2).headerLine)
    }
    @Test(expected = IllegalArgumentException::class)
    fun truncatedPaginationIsRejected() {
        val root = JSONObject(fixture(1))
        root.getJSONObject("pagination").put("total_pages", 2)
        PageParser.parse(1, listOf(root.toString()))
    }
    @Test(expected = IllegalArgumentException::class)
    fun wrongPageIsRejected() { PageParser.parse(3, listOf(fixture(1))) }
    @Test(expected = IllegalArgumentException::class)
    fun missingVerseIsRejected() {
        val root = JSONObject(fixture(1)); root.getJSONArray("verses").remove(6)
        PageParser.parse(1, listOf(root.toString()))
    }
}
