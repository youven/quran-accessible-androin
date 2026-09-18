package com.youven.quranaccessible.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class LoadedPage(val bitmap: Bitmap, val savedOffline: Boolean)

/** Read-through persistent storage: no bulk downloads or fabricated placeholder pages. */
class PageRepository(filesDir: File) {
    private val directory = File(filesDir, "mushaf/${MadaniPage.EDITION}")

    suspend fun load(page: Int): LoadedPage = withContext(Dispatchers.IO) {
        val url = MadaniPage.imageUrl(page)
        val target = File(directory, "$page.png")
        if (target.isFile && target.length() in 1..MAX_BYTES.toLong()) {
            val bitmap = runCatching { decode(target.readBytes()) }.getOrNull()
            if (bitmap != null) return@withContext LoadedPage(bitmap, true)
            target.delete() // A corrupt cached image must not trap the reader in an error loop.
        }
        val connection = URL(url).openConnection() as HttpURLConnection
        val bytes = try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "image/png")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) throw IOException("Page unavailable")
            if (connection.contentLengthLong > MAX_BYTES) throw IOException("Page too large")
            connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > MAX_BYTES) throw IOException("Page too large")
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
        } finally {
            connection.disconnect()
        }
        currentCoroutineContext().ensureActive()
        val bitmap = decode(bytes) ?: throw IOException("Invalid page image")
        // Storage failure must not hide a successfully downloaded page.
        val saved = runCatching {
            if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Storage unavailable")
            val temporary = File.createTempFile("page-$page-", ".part", directory)
            try {
                temporary.outputStream().use { it.write(bytes) }
                if (!temporary.renameTo(target)) throw IOException("Cannot save page")
            } finally {
                temporary.delete()
            }
        }.isSuccess
        LoadedPage(bitmap, saved)
    }

    private fun decode(bytes: ByteArray): Bitmap? {
        if (bytes.size < 8 || !bytes.copyOfRange(0, 8).contentEquals(PNG_SIGNATURE)) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth !in 300..2400 || bounds.outHeight !in 400..3600) return null
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    companion object {
        private const val MAX_BYTES = 4 * 1024 * 1024
        private val PNG_SIGNATURE = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)
    }
}
