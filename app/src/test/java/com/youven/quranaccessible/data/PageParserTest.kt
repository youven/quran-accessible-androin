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
    @Test fun chapterStartingAtLineTwoHasHeaderOnLineOneAndNoExtraBasmala() {
        val start = ChapterStart(80, 2)
        assertEquals(1, start.headerLine)
        assertNull(start.basmalaLine)
    }
    @Test fun mismatchedTotalRecordsDoesNotPreventValidPageFromParsing() {
        val root = JSONObject(fixture(1))
        root.getJSONObject("pagination").put("total_records", 5)
        val page = PageParser.parse(1, listOf(root.toString()))
        assertEquals(36, page.words.size)
    }
    @Test fun outOfOrderWordsWithinVerseDoNotCrashPageParsing() {
        val root = JSONObject(fixture(1))
        val verses = root.getJSONArray("verses")
        val v1Words = verses.getJSONObject(1).getJSONArray("words")
        v1Words.getJSONObject(0).put("line_number", 4)
        v1Words.getJSONObject(1).put("line_number", 3)
        val page = PageParser.parse(1, listOf(root.toString()))
        assertEquals(36, page.words.size)
    }
    @Test fun canonicalPageSpecsReflectAccurateVerseBoundariesAndWordCounts() {
        val p121 = MadaniPage.spec(121)
        assertEquals("5:78", p121.firstVerse)
        assertEquals("5:83", p121.lastVerse)
        assertEquals(114, p121.wordCount)
        assertEquals(listOf(5), p121.chapters)

        val p122 = MadaniPage.spec(122)
        assertEquals("5:84", p122.firstVerse)
        assertEquals("5:90", p122.lastVerse)
        assertEquals(133, p122.wordCount)
        assertEquals(listOf(5), p122.chapters)

        val p604 = MadaniPage.spec(604)
        assertEquals("112:1", p604.firstVerse)
        assertEquals("114:6", p604.lastVerse)
        assertEquals(73, p604.wordCount)
        assertEquals(listOf(112, 113, 114), p604.chapters)
    }
    @Test(expected = IllegalArgumentException::class)
    fun pageMissingWordsIsStrictlyRejected() {
        val root = JSONObject(fixture(1))
        val verses = root.getJSONArray("verses")
        val firstVerseWords = verses.getJSONObject(0).getJSONArray("words")
        firstVerseWords.remove(0) // Remove a word
        PageParser.parse(1, listOf(root.toString()))
    }
}
