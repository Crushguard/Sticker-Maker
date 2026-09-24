package com.piptechnologies.whatsappstub

import android.content.ContentResolver
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import java.nio.ByteBuffer
import java.util.Locale

/** What the stub learned about one pack by reading the sticker app's provider. */
class ContractReport(
    val name: String,
    val publisher: String,
    val animated: Boolean,
    val stickerCount: Int,
    val tray: Bitmap?,
    val previews: List<Bitmap>,
    val passed: List<String>,
    val problems: List<String>
) {
    val ok: Boolean get() = problems.isEmpty()
}

/**
 * Reads a pack through the sticker app's ContentProvider the way WhatsApp
 * does (metadata row, sticker rows, then every image through
 * openAssetFileDescriptor) and checks WhatsApp's published pack rules:
 * 3 to 30 stickers with 1 to 3 emojis each, a square PNG tray of at most
 * 50 KB, 512×512 WebP stickers of at most 100 KB (500 KB when animated),
 * and an animated flag that matches the files.
 */
class ContractCheck(
    private val resolver: ContentResolver,
    private val authority: String,
    private val identifier: String
) {
    private val passed = mutableListOf<String>()
    private val problems = mutableListOf<String>()

    fun run(): ContractReport {
        if (authority.isBlank() || identifier.isBlank()) {
            problems += "intent: sticker_pack_authority and sticker_pack_id are required"
            return report("", "", false, 0, null, emptyList())
        }

        var name = ""
        var publisher = ""
        var trayFile = ""
        var animated = false
        try {
            val cursor = resolver.query(uri("metadata", identifier), null, null, null, null)
            if (cursor == null) {
                problems += "metadata: the provider returned no cursor"
            } else {
                cursor.use { c ->
                    if (c.count != 1) {
                        problems += "metadata: expected 1 row, got ${c.count}"
                    } else {
                        c.moveToFirst()
                        val id = c.text("sticker_pack_identifier")
                        name = c.text("sticker_pack_name")
                        publisher = c.text("sticker_pack_publisher")
                        trayFile = c.text("sticker_pack_icon")
                        val version = c.text("image_data_version")
                        animated = c.number("animated_sticker_pack") == 1
                        val avoidCache = c.number("whatsapp_will_not_cache_stickers")
                        listOf(
                            "android_play_store_link", "ios_app_download_link",
                            "sticker_pack_publisher_email", "sticker_pack_publisher_website",
                            "sticker_pack_privacy_policy_website", "sticker_pack_license_agreement_website"
                        ).forEach { c.getColumnIndexOrThrow(it) }
                        if (id != identifier) problems += "metadata: identifier is '$id', expected '$identifier'"
                        if (!identifier.matches(IDENTIFIER)) problems += "metadata: identifier '$identifier' has illegal characters"
                        if (name.isBlank() || name.length > 128) problems += "metadata: name must be 1 to 128 characters"
                        if (publisher.isBlank() || publisher.length > 128) problems += "metadata: publisher must be 1 to 128 characters"
                        if (trayFile.isBlank()) problems += "metadata: sticker_pack_icon is empty"
                        if (version.isBlank()) problems += "metadata: image_data_version is empty"
                        if (avoidCache !in 0..1) problems += "metadata: whatsapp_will_not_cache_stickers must be 0 or 1"
                        if (problems.isEmpty()) passed += "metadata · “$name” by $publisher"
                    }
                }
            }
        } catch (e: Exception) {
            problems += "metadata: ${e.javaClass.simpleName}: ${e.message}"
        }

        val stickers = mutableListOf<Pair<String, List<String>>>()
        try {
            val cursor = resolver.query(uri("stickers", identifier), null, null, null, null)
            if (cursor == null) {
                problems += "stickers: the provider returned no cursor"
            } else {
                cursor.use { c ->
                    c.getColumnIndexOrThrow("sticker_accessibility_text")
                    while (c.moveToNext()) {
                        val emojis = c.text("sticker_emoji")
                            .split(',')
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                        stickers += c.text("sticker_file_name") to emojis
                    }
                }
            }
        } catch (e: Exception) {
            problems += "stickers: ${e.javaClass.simpleName}: ${e.message}"
        }
        val countOk = stickers.size in 3..30
        if (!countOk) problems += "stickers: ${stickers.size} rows, WhatsApp needs 3 to 30"
        val emojiProblems = stickers.filter { it.second.size !in 1..3 }
        emojiProblems.forEach { problems += "${it.first}: ${it.second.size} emojis, needs 1 to 3" }
        if (countOk && emojiProblems.isEmpty()) passed += "${stickers.size} stickers · 1 to 3 emojis each"

        var tray: Bitmap? = null
        if (trayFile.isNotBlank()) {
            val bytes = readAsset(trayFile)
            if (bytes == null) {
                problems += "tray $trayFile: could not be opened"
            } else {
                val (w, h) = bounds(bytes)
                val before = problems.size
                if (!isPng(bytes)) problems += "tray $trayFile: not a PNG"
                if (bytes.size > TRAY_LIMIT) problems += "tray $trayFile: ${kb(bytes.size)}, limit 50 KB"
                if (w != h || w !in 24..512) problems += "tray $trayFile: $w×$h, must be square, 24 to 512 px"
                if (problems.size == before) passed += "tray · $trayFile $w×$h PNG, ${kb(bytes.size)}"
                tray = decode(bytes)
            }
        }

        val previews = mutableListOf<Bitmap>()
        val limit = if (animated) ANIMATED_LIMIT else STATIC_LIMIT
        var largest = 0
        val before = problems.size
        stickers.forEachIndexed { index, (file, _) ->
            val bytes = readAsset(file)
            if (bytes == null) {
                problems += "$file: could not be opened"
                return@forEachIndexed
            }
            largest = maxOf(largest, bytes.size)
            val webp = WebpHeader.parse(bytes)
            if (webp == null) {
                problems += "$file: not a WebP file"
                return@forEachIndexed
            }
            if (webp.width != 512 || webp.height != 512) problems += "$file: ${webp.width}×${webp.height}, must be 512×512"
            if (bytes.size > limit) problems += "$file: ${kb(bytes.size)}, limit ${limit / 1024} KB"
            if (webp.animated != animated) {
                problems += "$file: animated=${webp.animated} in a pack marked animated=$animated"
            }
            if (index < PREVIEW_COUNT) decode(bytes)?.let { previews += it }
        }
        if (stickers.isNotEmpty() && problems.size == before) {
            passed += "stickers · 512×512 WebP, largest ${kb(largest)} of ${limit / 1024} KB"
            passed += if (animated) "animated pack · every sticker animates" else "static pack · no animated files"
        }

        return report(name, publisher, animated, stickers.size, tray, previews)
    }

    private fun report(
        name: String,
        publisher: String,
        animated: Boolean,
        count: Int,
        tray: Bitmap?,
        previews: List<Bitmap>
    ) = ContractReport(name, publisher, animated, count, tray, previews, passed.toList(), problems.toList())

    private fun uri(vararg segments: String): Uri =
        Uri.Builder()
            .scheme(ContentResolver.SCHEME_CONTENT)
            .authority(authority)
            .apply { segments.forEach { appendPath(it) } }
            .build()

    private fun readAsset(file: String): ByteArray? = try {
        resolver.openAssetFileDescriptor(uri("stickers_asset", identifier, file), "r")?.use { afd ->
            afd.createInputStream().use { it.readBytes() }
        }
    } catch (e: Exception) {
        null
    }

    private fun Cursor.text(column: String): String = getString(getColumnIndexOrThrow(column)).orEmpty()

    private fun Cursor.number(column: String): Int = getInt(getColumnIndexOrThrow(column))

    private fun bounds(bytes: ByteArray): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        return options.outWidth to options.outHeight
    }

    private fun decode(bytes: ByteArray): Bitmap? =
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes)))
                } catch (e: Exception) {
                    null
                }
            } else {
                null
            }

    private fun isPng(bytes: ByteArray): Boolean =
        bytes.size > 8 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() &&
            bytes[2] == 'N'.code.toByte() && bytes[3] == 'G'.code.toByte()

    private fun kb(size: Int): String = String.format(Locale.ROOT, "%.1f KB", size / 1024.0)

    private companion object {
        const val TRAY_LIMIT = 50 * 1024
        const val STATIC_LIMIT = 100 * 1024
        const val ANIMATED_LIMIT = 500 * 1024
        const val PREVIEW_COUNT = 5
        val IDENTIFIER = Regex("[a-zA-Z0-9_.\\- ]{1,128}")
    }
}

/** Canvas size and animation flag from a WebP container header. */
class WebpHeader(val width: Int, val height: Int, val animated: Boolean) {
    companion object {
        fun parse(bytes: ByteArray): WebpHeader? {
            if (bytes.size < 30) return null
            if (ascii(bytes, 0) != "RIFF" || ascii(bytes, 8) != "WEBP") return null
            return when (ascii(bytes, 12)) {
                "VP8X" -> WebpHeader(
                    width = 1 + u24(bytes, 24),
                    height = 1 + u24(bytes, 27),
                    animated = (bytes[20].toInt() and 0x02) != 0
                )
                // Lossy: 14-bit dimensions after the 3-byte start code.
                "VP8 " -> WebpHeader(
                    width = u16(bytes, 26) and 0x3FFF,
                    height = u16(bytes, 28) and 0x3FFF,
                    animated = false
                )
                // Lossless: 14-bit width-1 and height-1 packed after the 0x2F signature.
                "VP8L" -> {
                    val bits = u32(bytes, 21)
                    WebpHeader(
                        width = (bits and 0x3FFF) + 1,
                        height = ((bits shr 14) and 0x3FFF) + 1,
                        animated = false
                    )
                }
                else -> null
            }
        }

        private fun ascii(bytes: ByteArray, at: Int) = String(bytes, at, 4, Charsets.US_ASCII)
        private fun u16(b: ByteArray, at: Int) = (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8)
        private fun u24(b: ByteArray, at: Int) = u16(b, at) or ((b[at + 2].toInt() and 0xFF) shl 16)
        private fun u32(b: ByteArray, at: Int) = u24(b, at) or ((b[at + 3].toInt() and 0xFF) shl 24)
    }
}
