package com.piptechnologies.stickermaker.feature.create.decor

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * The five downloadable skin tones of the Hands emoji (spec §2). Each tone's PNG is fetched once
 * from Fluent Emoji ([EmojiCatalog.toneUrl]) and kept in [dir] under the name [DecorAssets] reads
 * ([DecorAssets.toneFileName]). [fetch] returns a URL's body, or null on any failure. Part files a
 * killed download left behind are deleted when the instance is made.
 */
class EmojiTones(private val dir: File, private val fetch: (String) -> ByteArray? = ::httpGet) {

    init {
        dir.listFiles { file -> file.name.endsWith(PART_SUFFIX) }?.forEach { it.delete() }
    }

    /** The downloaded [tone] of [item], or null while it isn't on disk. */
    fun cached(item: EmojiItem, tone: SkinTone): File? = fileOf(item, tone).takeIf { it.isFile && it.length() > 0 }

    /**
     * The [tone] of [item] on disk, downloaded first when it isn't there yet; null when the download
     * fails, and then nothing is kept. A body that isn't a PNG counts as a failure (a captive portal
     * answers 200 with its own page). The body goes to a `.part` file that is renamed into place, so a
     * half-written file never counts as cached. Blocking I/O on the caller's dispatcher: call it on IO.
     * [tone] is never [SkinTone.Default], which is bundled.
     */
    suspend fun ensure(item: EmojiItem, tone: SkinTone): File? {
        require(tone != SkinTone.Default) { "the default tone is bundled" }
        cached(item, tone)?.let { return it }
        val bytes = fetch(EmojiCatalog.toneUrl(item, tone))?.takeIf(::isPng) ?: return null
        val target = fileOf(item, tone)
        return try {
            dir.mkdirs()
            // A name of its own, so two downloads of the same tone never write the same part file.
            val part = File.createTempFile(target.name, PART_SUFFIX, dir)
            try {
                part.writeBytes(bytes)
                if (part.renameTo(target)) target else null
            } finally {
                part.delete()
            }
        } catch (e: IOException) {
            null
        }
    }

    private fun fileOf(item: EmojiItem, tone: SkinTone) = File(dir, DecorAssets.toneFileName(item.file, tone))

    companion object {
        /** The folder under `filesDir` that holds the downloads (spec §2); [DecorAssets.android] reads it. */
        const val DIR = "emoji-tones"

        private const val PART_SUFFIX = ".part"

        /** The 8 bytes every PNG file starts with. */
        private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

        private fun isPng(bytes: ByteArray): Boolean =
            bytes.size >= PNG_SIGNATURE.size && PNG_SIGNATURE.indices.all { bytes[it] == PNG_SIGNATURE[it] }

        /** Production downloads, kept in `filesDir/emoji-tones/`. */
        fun android(context: Context): EmojiTones = EmojiTones(File(context.applicationContext.filesDir, DIR))
    }
}

private const val TIMEOUT_MS = 8_000
private const val MAX_BYTES = 512 * 1024

/**
 * The body of [url] when the server answers HTTP 200 within the 8 s connect and read timeouts and
 * sends at most 512 KB; null otherwise, and on any exception.
 */
internal fun httpGet(url: String): ByteArray? {
    var connection: HttpURLConnection? = null
    return try {
        connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
        }
        if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
        if (connection.contentLengthLong > MAX_BYTES) return null
        connection.inputStream.use { readAtMost(it, MAX_BYTES) }
    } catch (e: Exception) {
        null
    } finally {
        connection?.disconnect()
    }
}

/** Everything [input] holds, or null once it passes [limit] bytes. */
private fun readAtMost(input: InputStream, limit: Int): ByteArray? {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    while (true) {
        val n = input.read(buffer)
        if (n < 0) return out.toByteArray()
        if (out.size() + n > limit) return null
        out.write(buffer, 0, n)
    }
}
