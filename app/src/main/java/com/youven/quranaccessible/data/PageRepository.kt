package com.youven.quranaccessible.data

import android.graphics.Typeface
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.coroutineContext

data class LoadedPage(val page: TextPage, val font: Typeface, val basmalaFont: Typeface, val titleFont: Typeface, val chapterNames: Map<Int, String>)

class PageLoadException(val part: Part, cause: Throwable) : IOException(cause) {
    enum class Part { TEXT, PAGE_FONT, PAGE_GLYPHS, COMMON_FONT, CHAPTERS }
}

private data class CheckedFont(val typeface: Typeface, val coverage: FontCoverage)
private class MissingFontSymbol : IOException("Font is missing a required page symbol")

class PageRepository(private val temporaryDirectory: File) {
    private val mutex = Mutex()
    private val pages = LinkedHashMap<Int, LoadedPage>()
    private var names: Map<Int, String>? = null
    private var openingFont: CheckedFont? = null
    private var titleFont: CheckedFont? = null

    suspend fun load(number: Int): LoadedPage = withContext(Dispatchers.IO) {
        require(number in 1..604)
        mutex.withLock {
            pages[number]?.let { return@withLock it }
            val page = loadTextPage(number)
            val font = try {
                if (number == 1 && openingFont?.let { f -> page.words.all { f.coverage.supports(it.glyph) } } == true) openingFont!!
                else loadFont(number, page.words.map { it.glyph })
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                val part = if (error is MissingFontSymbol) PageLoadException.Part.PAGE_GLYPHS
                    else PageLoadException.Part.PAGE_FONT
                throw PageLoadException(part, error)
            }
            val basmala = try {
                openingFont ?: (if (number == 1) font else loadFont(1)).also { openingFont = it }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                throw PageLoadException(PageLoadException.Part.COMMON_FONT, error)
            }
            val titles = try {
                titleFont ?: loadTypeface(
                    "surah-names-v1.ttf",
                    "https://verses.quran.foundation/fonts/quran/surah-names/v1/sura_names.ttf",
                    listOf("\uE000") + (1..114).map { (0xE000 + it.toString().toInt(16)).toChar().toString() }
                ).also { titleFont = it }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                throw PageLoadException(PageLoadException.Part.COMMON_FONT, error)
            }
            val chapters = try {
                names ?: run {
                    cachedValue("chapters-v1.json", "https://api.quran.com/api/v4/chapters", 200_000) { bytes ->
                        val list = JSONObject(bytes.toString(Charsets.UTF_8)).getJSONArray("chapters")
                        require(list.length() == 114)
                        (0 until list.length()).associate { i -> list.getJSONObject(i).let { it.getInt("id") to it.getString("name_arabic") } }
                    }.also { names = it }
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                throw PageLoadException(PageLoadException.Part.CHAPTERS, error)
            }
            coroutineContext.ensureActive()
            LoadedPage(page, font.typeface, basmala.typeface, titles.typeface, chapters).also {
                pages[number] = it
                while (pages.size > 8) pages.remove(pages.keys.first())
            }
        }
    }

    /** Checks whether both text and glyph font for a page are downloaded and ready offline. */
    fun isPageCached(number: Int): Boolean {
        val textFile = File(temporaryDirectory, "page-$number-complete-v3.json")
        val fontFile = File(temporaryDirectory, "qcf-v2-page-$number.ttf")
        return textFile.isFile && textFile.length() > 0L && fontFile.isFile && fontFile.length() > 0L
    }

    /** Returns the total count of fully cached pages out of 604. */
    fun getCachedPagesCount(): Int {
        val files = temporaryDirectory.list() ?: return 0
        val set = files.toHashSet()
        var count = 0
        for (i in 1..604) {
            if ("page-$i-complete-v3.json" in set && "qcf-v2-page-$i.ttf" in set) {
                count++
            }
        }
        return count
    }

    /**
     * Downloads the complete Quran (all 604 pages, text, fonts, and chapters) for offline reading.
     * Downloads in parallel with concurrency of 3, skipping any pages already cached.
     * Invokes [onProgress] with (downloadedPagesCount, 604, currentWorkingPage).
     */
    suspend fun downloadAllPages(
        onProgress: (downloaded: Int, total: Int, currentWorkingPage: Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        val total = 604
        val semaphore = Semaphore(3)
        val initialCount = getCachedPagesCount()
        val downloadedCounter = AtomicInteger(initialCount)
        onProgress(initialCount, total, 1)

        // Ensure common fonts, basmala, and chapter names are loaded first
        try {
            load(1)
        } catch (c: CancellationException) {
            throw c
        } catch (_: Exception) {}

        coroutineScope {
            val jobs = (1..604).map { pageNumber ->
                async {
                    if (isPageCached(pageNumber)) {
                        return@async
                    }
                    semaphore.withPermit {
                        ensureActive()
                        try {
                            load(pageNumber)
                            val currentCount = downloadedCounter.incrementAndGet()
                            onProgress(currentCount, total, pageNumber)
                        } catch (c: CancellationException) {
                            throw c
                        } catch (e: Exception) {
                            Log.w("QuranPages", "Offline download error on page $pageNumber", e)
                        }
                    }
                }
            }
            jobs.awaitAll()
        }
        onProgress(getCachedPagesCount(), total, 604)
    }

    private suspend fun loadTextPage(number: Int): TextPage {
        return try {
            val cacheName = "page-$number-complete-v3.json"
            val cached = File(temporaryDirectory, cacheName)
            if (cached.isFile) {
                try {
                    require(cached.length() in 1..4_000_000L)
                    val array = JSONArray(cached.readText())
                    return PageParser.parse(number, (0 until array.length()).map(array::getString))
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { cached.delete() }
            }
            val address = "https://api.quran.com/api/v4/verses/by_page/$number?words=true&word_fields=code_v2,text_uthmani&per_page=50&mushaf=1"
            var responses = fetchTextBatches(address)
            val result = try { PageParser.parse(number, responses) }
            catch (invalid: IllegalArgumentException) {
                val chapters = PageRecovery.chapters(number)
                val restored = chapters.associateWith { chapter ->
                    fetchTextBatches("https://api.quran.com/api/v4/verses/by_chapter/$chapter?words=true&word_fields=code_v2,text_uthmani&per_page=50&mushaf=1")
                }
                responses = listOf(PageRecovery.rebuild(number, responses, restored))
                PageParser.parse(number, responses)
            }
            storeValidated(cacheName, JSONArray(responses).toString().toByteArray(Charsets.UTF_8))
            result
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Log.w("QuranPages", "Page $number text could not be validated", error)
            throw PageLoadException(PageLoadException.Part.TEXT, error)
        }
    }

    private suspend fun fetchTextBatches(address: String): List<String> {
        val responses = mutableListOf<String>()
        var next = 1
        do {
            coroutineContext.ensureActive()
            val body = download("$address&page=$next", 4_000_000).toString(Charsets.UTF_8)
            responses += body
            val pagination = JSONObject(body).getJSONObject("pagination")
            require(pagination.getInt("current_page") == next)
            val following = pagination.optInt("next_page", 0)
            require(following == 0 || following == next + 1)
            next = following
            require(responses.size <= 20)
        } while (next != 0)
        return responses
    }

    private suspend fun loadFont(page: Int, tokens: List<String> = listOf("\uFC41", "\uFC42", "\uFC43", "\uFC44")): CheckedFont =
        loadTypeface("qcf-v2-page-$page.ttf", MadaniPage.fontUrl(page), tokens)

    private suspend fun loadTypeface(cacheName: String, address: String, tokens: List<String>): CheckedFont =
        cachedValue(cacheName, address, 3_000_000) { bytes ->
            val coverage = FontCoverage.read(bytes)
            if (!tokens.all(coverage::supports)) throw MissingFontSymbol()
            val file = File.createTempFile("qcf-font-", ".ttf", temporaryDirectory)
            try {
                file.writeBytes(bytes)
                val typeface = Typeface.Builder(file).build() ?: throw IOException("Android could not open the font")
                CheckedFont(typeface, coverage)
            } finally { file.delete() }
        }

    private suspend fun <T> cachedValue(cacheName: String, address: String, limit: Int, decode: (ByteArray) -> T): T {
        val cached = File(temporaryDirectory, cacheName)
        if (cached.isFile) {
            try {
                require(cached.length() in 1..limit.toLong())
                val rawBytes = cached.readBytes()
                val sanitized = FontCoverage.sanitize(rawBytes)
                if (sanitized !== rawBytes) {
                    storeValidated(cacheName, sanitized)
                }
                return decode(sanitized)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Only the invalid app-cache entry is removed; retry from the source immediately.
                cached.delete()
            }
        }
        val bytes = FontCoverage.sanitize(download(address, limit))
        val result = decode(bytes) // Validate before committing cache, including glyph coverage.
        storeValidated(cacheName, bytes)
        return result
    }

    private suspend fun storeValidated(cacheName: String, bytes: ByteArray) {
        coroutineContext.ensureActive()
        val cached = File(temporaryDirectory, cacheName)
        try {
            val pending = File.createTempFile("$cacheName-", ".part", temporaryDirectory)
            try {
                pending.writeBytes(bytes)
                if (!pending.renameTo(cached)) Log.w("QuranPages", "Could not store validated cache entry")
            } finally { pending.delete() }
        } catch (_: IOException) {
            // A full cache must not prevent displaying content already downloaded and validated.
            Log.w("QuranPages", "Cache is unavailable; keeping content in memory")
        }
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
