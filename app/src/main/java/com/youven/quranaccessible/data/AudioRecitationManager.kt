package com.youven.quranaccessible.data

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class Reciter(
    val id: Int,
    val nameArabic: String,
    val englishName: String,
    val style: String? = null
) {
    val displayName: String
        get() = if (style.isNullOrBlank() || style == "None") nameArabic else "$nameArabic ($style)"
}

data class VerseAudio(
    val verseKey: String,
    val audioUrl: String
)

class AudioRecitationManager(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var mediaPlayer: MediaPlayer? = null

    var isPlaying = false; private set
    var currentVerseKey: String? = null; private set
    var currentReciterId: Int = 7; private set // Default: Mishary Alafasy
    var currentPage: Int = 1; private set

    private var pageAudioFiles: List<VerseAudio> = emptyList()
    private var currentAudioIndex: Int = -1

    var onVerseChangeListener: ((String) -> Unit)? = null
    var onPageChangeListener: ((Int) -> Unit)? = null
    var onPlaybackStateChangeListener: ((Boolean) -> Unit)? = null
    var onErrorListener: ((String) -> Unit)? = null

    companion object {
        val DEFAULT_RECITERS = listOf(
            Reciter(7, "مشاري راشد العفاسي", "Mishari Rashid al-`Afasy"),
            Reciter(2, "عبد الباسط عبد الصمد", "AbdulBaset AbdulSamad", "مرتل"),
            Reciter(1, "عبد الباسط عبد الصمد", "AbdulBaset AbdulSamad", "مجود"),
            Reciter(8, "محمد صديق المنشاوي", "Mohamed Siddiq al-Minshawi", "مجود"),
            Reciter(9, "محمد صديق المنشاوي", "Mohamed Siddiq al-Minshawi", "مرتل"),
            Reciter(6, "محمود خليل الحصري", "Mahmoud Khalil Al-Husary", "مرتل"),
            Reciter(12, "محمود خليل الحصري", "Mahmoud Khalil Al-Husary", "معلم"),
            Reciter(4, "أبو بكر الشاطري", "Abu Bakr al-Shatri"),
            Reciter(10, "سعود الشريم", "Sa`ud ash-Shuraym"),
            Reciter(3, "عبد الرحمن السديس", "Abdur-Rahman as-Sudais"),
            Reciter(5, "هاني الرفاعي", "Hani ar-Rifai"),
            Reciter(11, "محمد الطبلاوي", "Mohamed al-Tablawi")
        )
    }

    /**
     * Loads the available reciters from Quran.com API with local disk caching and fallback.
     */
    suspend fun loadReciters(): List<Reciter> = withContext(Dispatchers.IO) {
        val cacheFile = File(context.cacheDir, "reciters_api_v1.json")
        if (cacheFile.isFile && cacheFile.length() > 0) {
            try {
                val cachedList = parseRecitersJson(cacheFile.readText())
                if (cachedList.isNotEmpty()) return@withContext cachedList
            } catch (_: Exception) {}
        }

        try {
            val url = URL("https://api.quran.com/api/v4/resources/recitations?language=ar")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 15_000
                setRequestProperty("Accept", "application/json")
            }
            if (conn.responseCode in 200..299) {
                val json = conn.inputStream.bufferedReader().use { it.readText() }
                cacheFile.writeText(json)
                val list = parseRecitersJson(json)
                if (list.isNotEmpty()) return@withContext list
            }
        } catch (e: Exception) {
            Log.w("AudioRecitation", "Failed to fetch reciters API", e)
        }

        DEFAULT_RECITERS
    }

    private fun parseRecitersJson(json: String): List<Reciter> {
        val root = JSONObject(json)
        val array = root.getJSONArray("recitations")
        val result = mutableListOf<Reciter>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val id = obj.getInt("id")
            val englishName = obj.optString("reciter_name", "")
            val style = if (obj.has("style") && !obj.isNull("style")) obj.getString("style") else null
            val arabicName = obj.optJSONObject("translated_name")?.optString("name", englishName) ?: englishName
            result.add(Reciter(id, arabicName, englishName, style))
        }
        return result
    }

    /**
     * Loads audio URLs for all verses of a given page from Quran.com API.
     */
    suspend fun loadPageAudio(reciterId: Int, page: Int): List<VerseAudio> = withContext(Dispatchers.IO) {
        val cacheFile = File(context.cacheDir, "audio_p${page}_r${reciterId}.json")
        if (cacheFile.isFile && cacheFile.length() > 0) {
            try {
                val list = parsePageAudioJson(cacheFile.readText())
                if (list.isNotEmpty()) return@withContext list
            } catch (_: Exception) {}
        }

        try {
            val url = URL("https://api.quran.com/api/v4/recitations/$reciterId/by_page/$page")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 15_000
                setRequestProperty("Accept", "application/json")
            }
            if (conn.responseCode in 200..299) {
                val json = conn.inputStream.bufferedReader().use { it.readText() }
                cacheFile.writeText(json)
                return@withContext parsePageAudioJson(json)
            }
        } catch (e: Exception) {
            Log.w("AudioRecitation", "Failed to fetch page audio for page $page", e)
        }
        emptyList()
    }

    private fun parsePageAudioJson(json: String): List<VerseAudio> {
        val root = JSONObject(json)
        val array = root.getJSONArray("audio_files")
        val result = mutableListOf<VerseAudio>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val verseKey = obj.getString("verse_key")
            var rawUrl = obj.getString("url")
            val fullUrl = if (rawUrl.startsWith("http")) rawUrl else "https://verses.quran.com/$rawUrl"
            result.add(VerseAudio(verseKey, fullUrl))
        }
        return result
    }

    /**
     * Starts playing recitation for a page, beginning either from [startVerseKey] or the first verse.
     */
    fun startPageRecitation(
        reciterId: Int,
        page: Int,
        startVerseKey: String? = null
    ) {
        currentReciterId = reciterId
        currentPage = page

        scope.launch {
            val audios = loadPageAudio(reciterId, page)
            if (audios.isEmpty()) {
                onErrorListener?.invoke("تعذّر جلب الصوت، تأكد من الاتصال بالإنترنت")
                updatePlaybackState(false)
                return@launch
            }

            pageAudioFiles = audios
            currentAudioIndex = if (!startVerseKey.isNullOrBlank()) {
                audios.indexOfFirst { it.verseKey == startVerseKey }.coerceAtLeast(0)
            } else {
                0
            }

            playCurrentIndex()
        }
    }

    private fun playCurrentIndex() {
        if (currentAudioIndex !in pageAudioFiles.indices) {
            // Reached end of current page: advance to next page if possible
            if (currentPage < 604) {
                val nextPage = currentPage + 1
                onPageChangeListener?.invoke(nextPage)
                startPageRecitation(currentReciterId, nextPage, null)
            } else {
                stop()
            }
            return
        }

        val verseAudio = pageAudioFiles[currentAudioIndex]
        currentVerseKey = verseAudio.verseKey
        onVerseChangeListener?.invoke(verseAudio.verseKey)

        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(verseAudio.audioUrl)
                setOnPreparedListener {
                    start()
                    updatePlaybackState(true)
                }
                setOnCompletionListener {
                    currentAudioIndex++
                    playCurrentIndex()
                }
                setOnErrorListener { _, what, extra ->
                    Log.w("AudioRecitation", "MediaPlayer error: $what, $extra")
                    onErrorListener?.invoke("تعذّر تشغيل المقطع الصوتي")
                    updatePlaybackState(false)
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.w("AudioRecitation", "Exception starting player", e)
            onErrorListener?.invoke("حدث خطأ أثناء تشغيل التلاوة")
            updatePlaybackState(false)
        }
    }

    fun pause() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                updatePlaybackState(false)
            }
        }
    }

    fun resume() {
        mediaPlayer?.let {
            it.start()
            updatePlaybackState(true)
        } ?: run {
            startPageRecitation(currentReciterId, currentPage, currentVerseKey)
        }
    }

    fun stop() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
        currentVerseKey = null
        updatePlaybackState(false)
    }

    private fun updatePlaybackState(playing: Boolean) {
        isPlaying = playing
        onPlaybackStateChangeListener?.invoke(playing)
    }

    fun release() {
        stop()
        scope.cancel()
    }
}
