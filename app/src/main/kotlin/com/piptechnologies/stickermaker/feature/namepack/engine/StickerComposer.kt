package com.piptechnologies.stickermaker.feature.namepack.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import com.piptechnologies.stickermaker.feature.create.StickerRenderer

/** Template art + lettering, then the 8 px white die-cut outline around the union, drawn beneath. */
class StickerComposer(private val lettering: Lettering) {

    fun compose(art: Bitmap, sticker: TemplateSticker, text: String, rtlLanguage: Boolean): Bitmap {
        val size = TemplateSet.CANVAS
        val body = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        Canvas(body).apply {
            drawBitmap(art, null, Rect(0, 0, size, size), Paint(Paint.FILTER_BITMAP_FLAG))
            lettering.draw(this, text, sticker, rtlLanguage)
        }
        return withOutline(body, OUTLINE_PX).also { body.recycle() }
    }

    companion object {
        const val OUTLINE_PX = 8f

        private val QUALITIES = intArrayOf(84, 76, 68, 60, 50, 40, 30)

        /** [body] over a white outline of [radiusPx] around its opaque pixels. [body] is not recycled. */
        fun withOutline(body: Bitmap, radiusPx: Float): Bitmap {
            val outline = StickerRenderer.outlineOf(body, radiusPx)
            val out = Bitmap.createBitmap(body.width, body.height, Bitmap.Config.ARGB_8888)
            Canvas(out).apply {
                drawBitmap(outline, 0f, 0f, null)
                drawBitmap(body, 0f, 0f, null)
            }
            outline.recycle()
            return out
        }

        /** WebP from quality 84 down until WhatsApp's 100 KB static limit is met (Create's encoder). */
        fun encode(bitmap: Bitmap): ByteArray = StickerRenderer.encodeStaticSticker(bitmap, qualities = QUALITIES)
    }
}
