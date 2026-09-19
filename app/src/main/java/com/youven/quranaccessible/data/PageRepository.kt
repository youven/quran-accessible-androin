package com.youven.quranaccessible.data

import android.graphics.Paint
import android.graphics.Typeface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

data class LoadedPage(val page: TextPage, val font: Typeface, val basmalaFont: Typeface, val titleFont: Typeface, val chapterNames: Map<Int, String>)

class PageRepository(private val temporaryDirectory: File) {
    private val mutex = Mutex()
    private val pages = LinkedHashMap<Int, LoadedPage>()
    private var names: Map<Int, String>? = null
    private var openingFont: Typeface? = null
    private var titleFont: Typeface? = null

    suspend fun load(number: Int): LoadedPage = withContext(Dispatchers.IO) {
        require(number in 1..604)
        mutex.withLock {
            pages[number]?.let { return@withLock it }
            val responses = mutableListOf<String>()
            var next = 1
            do {
                coroutineContext.ensureActive()
                val body = download("https://api.quran.com/api/v4/verses/by_page/$number?words=true&word_fields=code_v2,text_uthmani&per_page=50&mushaf=1&page=$next", 4_000_000).toString(Charsets.UTF_8)
                responses += body
                val pagination = JSONObject(body).getJSONObject("pagination")
                val following = pagination.optInt("next_page", 0)
                require(following == 0 || following == next + 1)
                next = following
                require(responses.size <= 20)
            } while (next != 0)
            val page = PageParser.parse(number, responses)
            val font = if (number == 1 && openingFont != null) openingFont!! else loadFont(number)
            val paint = Paint().apply { typeface = font }
            require(page.words.all { paint.hasGlyph(it.glyph) }) { "Font does not match page" }
            val basmala = openingFont ?: (if (number == 1) font else loadFont(1)).also { openingFont = it }
            val titles = titleFont ?: loadTypeface("https://verses.quran.foundation/fonts/quran/surah-names/v1/sura_names.ttf").also { titleFont = it }
            val titlePaint = Paint().apply { typeface = titles }
            require(titlePaint.hasGlyph("\uE000") && page.starts.all {
                titlePaint.hasGlyph((0xE000 + it.chapter.toString().toInt(16)).toChar().toString())
            }) { "Missing chapter title glyph" }
            val chapters = names ?: run {
                val list = JSONObject(download("https://api.quran.com/api/v4/chapters", 200_000).toString(Charsets.UTF_8)).getJSONArray("chapters")
                require(list.length() == 114)
                (0 until list.length()).associate { i -> list.getJSONObject(i).let { it.getInt("id") to it.getString("name_arabic") } }.also { names = it }
            }
            coroutineContext.ensureActive()
            LoadedPage(page, font, basmala, titles, chapters).also {
                pages[number] = it
                while (pages.size > 3) pages.remove(pages.keys.first())
            }
        }
    }

    private suspend fun loadFont(page: Int): Typeface = loadTypeface(MadaniPage.fontUrl(page))

    private suspend fun loadTypeface(address: String): Typeface {
        val bytes = download(address, 3_000_000)
        require(bytes.size > 1024 && bytes.take(4) == listOf<Byte>(0, 1, 0, 0)) { "Invalid TrueType font" }
        val file = File.createTempFile("qcf-font-", ".ttf", temporaryDirectory)
        return try {
            file.writeBytes(bytes)
            Typeface.createFromFile(file)
        } finally { file.delete() }
    }

    private suspend fun download(address: String, limit: Int): ByteArray {
        coroutineContext.ensureActive()
        val connection = URL(address).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "*/*")
            connection.setRequestProperty("User-Agent", "QuranAccessibleAndroid/0.3")
            require(connection.responseCode == 200) { "Source unavailable" }
            require(connection.contentLengthLong <= limit)
            connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    coroutineContext.ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= limit)
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
        } finally { connection.disconnect() }
    }
}
