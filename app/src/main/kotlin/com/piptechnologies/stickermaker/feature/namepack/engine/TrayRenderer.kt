package com.piptechnologies.stickermaker.feature.namepack.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import java.io.ByteArrayOutputStream

/**
 * The pack's tray icon (spec, Rendering): the app-mark heart filled rose, with white initials
 * (yours then theirs) at 0.36 × size for two and 0.46 × size for one, −0.03 em tracking, centred
 * horizontally and at 0.44 × size vertically (the design's optical centre, 6 px above at 96).
 */
class TrayRenderer(private val typefaceFor: (LetteringFont) -> Typeface) {

    fun render(initials: String, size: Int = TRAY_SIZE): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawPath(HeartPath.inBox(0f, 0f, size.toFloat()), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ROSE })
        if (initials.isNotEmpty()) {
            val two = NameInput.graphemeCount(initials) >= 2
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                typeface = typefaceFor(LetteringFonts.choose(initials))
                textSize = size * if (two) 0.36f else 0.46f
                letterSpacing = -0.03f
            }
            val layout = StaticLayout.Builder.obtain(initials, 0, initials.length, paint, size)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
                .setIncludePad(false)
                .setMaxLines(1)
                .build()
            val metrics = paint.fontMetrics
            val baseline = size * 0.44f - (metrics.ascent + metrics.descent) / 2f
            canvas.save()
            canvas.translate(0f, baseline - layout.getLineBaseline(0))
            layout.draw(canvas)
            canvas.restore()
        }
        return bitmap
    }

    companion object {
        const val TRAY_SIZE = 96
        val ROSE = 0xFFC23359.toInt()

        fun encode(bitmap: Bitmap): ByteArray =
            ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.toByteArray()
            }
    }
}
