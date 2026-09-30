package com.piptechnologies.stickermaker.feature.create

import android.content.Context
import android.util.LruCache
import com.piptechnologies.stickermaker.feature.create.decor.DecorAssets
import com.piptechnologies.stickermaker.feature.create.decor.DecorData
import com.piptechnologies.stickermaker.feature.create.decor.DecorFonts
import com.piptechnologies.stickermaker.feature.create.decor.EmojiTones
import com.piptechnologies.stickermaker.feature.create.decor.FontFile
import com.piptechnologies.stickermaker.feature.create.decor.LayerContent
import com.piptechnologies.stickermaker.feature.create.decor.LayerRenderCache
import com.piptechnologies.stickermaker.feature.create.decor.SceneRenderer
import com.piptechnologies.stickermaker.feature.create.decor.Size2
import com.piptechnologies.stickermaker.feature.create.decor.TextLayerPainter

/**
 * The Create session's decor engine (spec §2, §5): the catalogs, the skin-tone downloads, the fonts
 * and text painter, and the layer cache and scene renderer that the live canvas and the exporter
 * share. It also remembers the layers' base sizes, which the editor asks for on every gesture frame
 * (a text layer measures its lines each time).
 */
internal class CreateDecor(
    val data: DecorData,
    val tones: EmojiTones,
    val fonts: DecorFonts,
    val painter: TextLayerPainter,
    private val cache: LayerRenderCache,
    val renderer: SceneRenderer
) {
    private val baseSizes = LruCache<LayerContent, Size2>(BASE_SIZES)

    /** [content]'s size at scale 1, in canvas px. */
    fun baseSize(content: LayerContent): Size2 =
        baseSizes.get(content) ?: cache.baseSize(content).also { baseSizes.put(content, it) }

    /** Frees the cached layer bitmaps (a finished pack's layers won't be drawn again). */
    fun clearCache() = cache.clear()

    companion object {
        private const val BASE_SIZES = 64

        /**
         * Reads the data from the app's assets and builds the renderer. [rtlLanguage] is read while
         * text renders (paragraph direction, bubble tail), on any thread. Blocking I/O: call it on IO.
         */
        fun load(context: Context, rtlLanguage: () -> Boolean): CreateDecor {
            val app = context.applicationContext
            val data = DecorData.load(app.assets)
            val fonts = DecorFonts.android(app, data.styles)
            // The faces of a first caption (Sticker style, Rounded, any script): typing it loads no font file.
            listOf(FontFile.BALOO, FontFile.BALOO_BHAIJAAN, FontFile.RUBIK).forEach { fonts.face(it) }
            val painter = TextLayerPainter(fonts, data.styles, rtlLanguage)
            val cache = LayerRenderCache(DecorAssets.android(app), painter, data)
            return CreateDecor(data, EmojiTones.android(app), fonts, painter, cache, SceneRenderer(cache, data))
        }
    }
}
