package com.piptechnologies.stickermaker.feature.create.decor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.io.File
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * The emoji and decoration bitmaps (spec §2): bundled assets read through [openAsset], and
 * skin tones downloaded into [toneDir] (a missing tone falls back to the bundled default).
 * Decoded bitmaps are shared from a 24 MB cache, so callers must not recycle them.
 */
class DecorAssets(private val openAsset: (String) -> InputStream, private val toneDir: File) {

    private val bitmaps = object : LruCache<String, Bitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val aspects = ConcurrentHashMap<String, Float>()

    /** The emoji art in [tone]; null when even the bundled file can't be read. */
    fun emoji(file: String, tone: SkinTone = SkinTone.Default): Bitmap? {
        if (tone != SkinTone.Default) {
            val downloaded = File(toneDir, toneFileName(file, tone))
            if (downloaded.isFile) {
                cached("$TONES${downloaded.name}") { BitmapFactory.decodeFile(downloaded.path) }?.let { return it }
            }
        }
        return cached("$EMOJI$file") { decodeAsset("$EMOJI$file") }
    }

    /** A decoration piece; null when it can't be read. */
    fun decor(file: String): Bitmap? = cached("$DECOR$file") { decodeAsset("$DECOR$file") }

    /** Height / width of the asset at [path] (e.g. `decor/props-7.webp`), from its header only; 1 when unreadable. */
    fun aspect(path: String): Float = aspects.getOrPut(path) {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { openAsset(path).use { BitmapFactory.decodeStream(it, null, options) } }
        if (options.outWidth > 0 && options.outHeight > 0) options.outHeight.toFloat() / options.outWidth else 1f
    }

    private fun cached(key: String, decode: () -> Bitmap?): Bitmap? {
        bitmaps.get(key)?.let { return it }
        return decode()?.also { bitmaps.put(key, it) }
    }

    private fun decodeAsset(path: String): Bitmap? =
        runCatching { openAsset(path).use { BitmapFactory.decodeStream(it) } }.getOrNull()

    companion object {
        private const val CACHE_BYTES = 24 * 1024 * 1024
        private const val EMOJI = "emoji/"
        private const val DECOR = "decor/"
        private const val TONES = "tones/"

        /** The downloaded file of a non-default [tone]: `waving_hand.webp` → `waving_hand_medium-light.png`. */
        fun toneFileName(file: String, tone: SkinTone): String =
            file.removeSuffix(".webp") + "_" + tone.fileSuffix + ".png"

        /** Production assets: the APK's assets, tones in `filesDir/emoji-tones/` (spec §2). */
        fun android(context: Context): DecorAssets {
            val app = context.applicationContext
            return DecorAssets({ app.assets.open(it) }, File(app.filesDir, "emoji-tones"))
        }
    }
}
