package com.youven.quranaccessible.data

import org.junit.Assert.*
import org.junit.Test

class MadaniPageTest {
    @Test fun acceptsSupportedNumeralsAndWhitespace() {
        assertEquals(604, MadaniPage.parse(" ٦٠٤ "))
        assertEquals(123, MadaniPage.parse("۱۲۳"))
        assertEquals(1, MadaniPage.parse("1"))
    }

    @Test fun rejectsOutOfRangeAndMalformedInput() {
        listOf("", "0", "605", "-1", "1.5", "1e2", "+2", "1 2", "abc", "999999999999").forEach {
            assertNull("Unexpectedly accepted $it", MadaniPage.parse(it))
        }
    }

    @Test fun correctsInvalidSavedPositions() {
        assertEquals(1, MadaniPage.restoredPage(-20))
        assertEquals(604, MadaniPage.restoredPage(900))
        assertEquals(50, MadaniPage.restoredPage(50))
    }

    @Test fun preservesEditionPageNumbering() {
        assertEquals("https://quran.ksu.edu.sa/png_big/1.png", MadaniPage.imageUrl(1))
        assertEquals("https://quran.ksu.edu.sa/png_big/604.png", MadaniPage.imageUrl(604))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidDownloadPage() { MadaniPage.imageUrl(605) }
}
