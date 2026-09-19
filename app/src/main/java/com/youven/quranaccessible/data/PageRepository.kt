package com.youven.quranaccessible.data

import android.graphics.Paint
import android.graphics.Typeface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.io.IOException
import kotlin.coroutines.coroutineContext

data class LoadedPage(val page: TextPage, val font: Typeface, val basmalaFont: Typeface, val titleFont: Typeface, val chapterNames: Map<Int, String>)

class PageLoadException(val part: Part, cause: Throwable) : IOException(cause) {
    enum class Part { TEXT, PAGE_FONT, PAGE_GLYPHS, COMMON_FONT, CHAPTERS }
}

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
            val page = loadTextPage(number)
            val font = try {
                if (number == 1 && openingFont != null) openingFont!! else loadFont(number)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                throw PageLoadException(PageLoadException.Part.PAGE_FONT, error)
            }
            val paint = Paint().apply { typeface = font }
            if (!page.words.all { QcfGlyphCoverage.supports(it.glyph, paint::hasGlyph) }) {
                throw PageLoadException(PageLoadException.Part.PAGE_GLYPHS,
                    IOException("Font is missing a QCF code point for page $number"))
            }
            val basmala = try {
                openingFont ?: (if (number == 1) font else loadFont(1)).also { openingFont = it }
            } catch (error: Exception) {
                throw PageLoadException(PageLoadException.Part.COMMON_FONT, error)
            }
            val titles = try {
                titleFont ?: loadTypeface(
                    "surah-names-v1.ttf",
                    "https://verses.quran.foundation/fonts/quran/surah-names/v1/sura_names.ttf"
                ).also { titleFont = it }
            } catch (error: Exception) {
                throw PageLoadException(PageLoadException.Part.COMMON_FONT, error)
            }
            val titlePaint = Paint().apply { typeface = titles }
            require(titlePaint.hasGlyph("\uE000") && page.starts.all {
                titlePaint.hasGlyph((0xE000 + it.chapter.toString().toInt(16)).toChar().toString())
            }) { "Missing chapter title glyph" }
            val chapters = try {
                names ?: run {
                    val bytes = cachedDownload("chapters-v1.json", "https://api.quran.com/api/v4/chapters", 200_000)
                    val list = JSONObject(bytes.toString(Charsets.UTF_8)).getJSONArray("chapters")
                    require(list.length() == 114)
                    (0 until list.length()).associate { i -> list.getJSONObject(i).let { it.getInt("id") to it.getString("name_arabic") } }.also { names = it }
                }
            } catch (error: Exception) {
                throw PageLoadException(PageLoadException.Part.CHAPTERS, error)
            }
            coroutineContext.ensureActive()
            LoadedPage(page, font, basmala, titles, chapters).also {
                pages[number] = it
                while (pages.size > 3) pages.remove(pages.keys.first())
            }
        }
    }

    private suspend fun loadTextPage(number: Int): TextPage = try {
        val responses = mutableListOf<String>()
        var next = 1
        do {
            coroutineContext.ensureActive()
            val address = "https://api.quran.com/api/v4/verses/by_page/$number?words=true&word_fields=code_v2,text_uthmani&per_page=50&mushaf=1&page=$next"
            val body = cachedDownload("page-$number-$next-v1.json", address, 4_000_000).toString(Charsets.UTF_8)
            responses += body
            val pagination = JSONObject(body).getJSONObject("pagination")
            val following = pagination.optInt("next_page", 0)
            require(following == 0 || following == next + 1)
            next = following
            require(responses.size <= 20)
        } while (next != 0)
        PageParser.parse(number, responses)
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        temporaryDirectory.listFiles { file -> file.name.startsWith("page-$number-") }
            ?.forEach(File::delete)
        throw PageLoadException(PageLoadException.Part.TEXT, error)
    }

    private suspend fun loadFont(page: Int): Typeface =
        loadTypeface("qcf-v2-page-$page.ttf", MadaniPage.fontUrl(page))

    private suspend fun loadTypeface(cacheName: String, address: String): Typeface {
        val bytes = cachedDownload(cacheName, address, 3_000_000)
        if (bytes.size <= 1024 || bytes.take(4) != listOf<Byte>(0, 1, 0, 0)) {
            File(temporaryDirectory, cacheName).delete()
            throw IOException("Invalid TrueType font")
        }
        val file = File.createTempFile("qcf-font-", ".ttf", temporaryDirectory)
        return try {
            file.writeBytes(bytes)
            Typeface.createFromFile(file)
        } finally { file.delete() }
    }

    private suspend fun cachedDownload(cacheName: String, address: String, limit: Int): ByteArray {
        val cached = File(temporaryDirectory, cacheName)
        if (cached.isFile && cached.length() in 1..limit.toLong()) return cached.readBytes()

        val bytes = download(address, limit)
        val pending = File(temporaryDirectory, "$cacheName.part")
        pending.writeBytes(bytes)
        if (!pending.renameTo(cached)) {
            cached.writeBytes(bytes)
            pending.delete()
        }
        return bytes
    }

    private suspend fun download(address: String, limit: Int): ByteArray {
        var lastError: Exception? = null
        repeat(3) { attempt ->
            coroutineContext.ensureActive()
            val connection = URL(address).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 25_000
                connection.readTimeout = 45_000
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("Accept", "*/*")
                connection.setRequestProperty("User-Agent", "QuranAccessibleAndroid/0.3")
                val status = connection.responseCode
                if (status !in 200..299) throw IOException("HTTP $status")
                if (connection.contentLengthLong > limit) throw IOException("Response too large")
                return connection.inputStream.use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (output.size() + count > limit) throw IOException("Response too large")
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                lastError = error
                if (attempt < 2) delay(700L * (attempt + 1))
            } finally {
                connection.disconnect()
            }
        }
        throw lastError ?: IOException("Download failed")
    }
}
