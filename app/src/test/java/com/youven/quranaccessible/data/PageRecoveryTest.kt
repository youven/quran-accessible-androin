package com.youven.quranaccessible.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PageRecoveryTest {
    private fun fixture(name: String) = javaClass.getResource("/$name.json")!!.readText()
    private fun original() = listOf(fixture("page600-incomplete"))
    private fun chapters() = (100..102).associateWith { listOf(fixture("chapter$it")) }

    @Test fun restoresAllTwentyFiveVersesOfPage600FromProviderChapterData() {
        assertEquals(listOf(100, 101, 102), PageRecovery.chapters(original()))
        val restored = PageRecovery.rebuild(600, original(), chapters())
        val page = PageParser.parse(600, listOf(restored))
        assertEquals(25, page.words.map { it.verse }.distinct().size)
        assertEquals("100:6", page.words.first().verse)
        assertEquals(1, page.words.first().line)
        assertEquals("102:8", page.words.last().verse)
        assertEquals(15, page.words.last().line)
        assertEquals(listOf(4, 11), page.starts.map { it.headerLine })
        assertEquals(listOf(5, 12), page.starts.map { it.basmalaLine })
    }

    @Test(expected = IllegalArgumentException::class)
    fun incompleteOriginalStillFailsWithoutRecovery() { PageParser.parse(600, original()) }

    @Test(expected = IllegalArgumentException::class)
    fun incompleteChapterCannotBecomeACompletePage() {
        val chapters = chapters().toMutableMap()
        val root = JSONObject(chapters.getValue(100).single())
        root.getJSONArray("verses").remove(5)
        chapters[100] = listOf(root.toString())
        PageRecovery.rebuild(600, original(), chapters)
    }

    @Test(expected = IllegalArgumentException::class)
    fun missingChapterIsRejected() { PageRecovery.rebuild(600, original(), chapters() - 101) }

    @Test(expected = IllegalArgumentException::class)
    fun wrongPageCannotBeSilentlyAccepted() { PageRecovery.rebuild(599, original(), chapters()) }

    @Test(expected = IllegalArgumentException::class)
    fun completeResponsesDoNotTriggerRecovery() { PageRecovery.chapters(listOf(fixture("page1"))) }
}
