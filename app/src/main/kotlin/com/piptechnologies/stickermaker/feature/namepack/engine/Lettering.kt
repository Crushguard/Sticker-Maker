package com.piptechnologies.stickermaker.feature.namepack.engine

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.style.ReplacementSpan
import kotlin.math.ceil
import kotlin.math.max

/**
 * Letters a phrase into its template zone (spec, Rendering): font from the string, the fit,
 * line height 1.08 × size, tracking −0.01 em, the block centred on the zone and rotated with it,
 * the white stroke painted before the ink fill, "❤" drawn as the app-mark heart.
 */
class Lettering(private val typefaceFor: (LetteringFont) -> Typeface) {

    /** The size and lines [draw] would use, without drawing. */
    fun fit(text: String, sticker: TemplateSticker): Fit {
        val paint = paintFor(text, sticker)
        return LetteringFit.fit(text, sticker.zone.w, sticker.zone.h) { line, size ->
            paint.textSize = size
            Layout.getDesiredWidth(spanned(line), paint)
        }
    }

    /** Draws [text] into [sticker]'s zone on a 512 canvas; returns the fit it used. */
    fun draw(canvas: Canvas, text: String, sticker: TemplateSticker, rtlLanguage: Boolean): Fit {
        val zone = sticker.zone
        val paint = paintFor(text, sticker)
        val fit = LetteringFit.fit(text, zone.w, zone.h) { line, size ->
            paint.textSize = size
            Layout.getDesiredWidth(spanned(line), paint)
        }
        paint.textSize = fit.size
        val direction = if (LetteringFonts.isRtlParagraph(text, rtlLanguage)) {
            TextDirectionHeuristics.RTL
        } else {
            TextDirectionHeuristics.LTR
        }
        val metrics = paint.fontMetrics
        val lineHeight = fit.lineHeight
        val blockTop = zone.cy - lineHeight * fit.lines.size / 2f

        canvas.save()
        canvas.rotate(zone.rotate, zone.cx, zone.cy)
        fit.lines.forEachIndexed { index, line ->
            val content = spanned(line)
            val width = ceil(max(zone.w, Layout.getDesiredWidth(content, paint))).toInt() + 2
            val layout = StaticLayout.Builder.obtain(content, 0, content.length, paint, width)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setTextDirection(direction)
                .setIncludePad(false)
                .setMaxLines(1)
                .build()
            // CSS line box: the font's ascent + descent centred in a 1.08 × size line.
            val baseline = blockTop + index * lineHeight + lineHeight / 2f - (metrics.ascent + metrics.descent) / 2f
            canvas.save()
            canvas.translate(zone.cx - width / 2f, baseline - layout.getLineBaseline(0))
            if (sticker.stroke > 0f) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = sticker.stroke
                paint.strokeJoin = Paint.Join.ROUND
                paint.color = sticker.strokeColor
                layout.draw(canvas)
            }
            paint.style = Paint.Style.FILL
            paint.color = sticker.ink
            layout.draw(canvas)
            canvas.restore()
        }
        canvas.restore()
        return fit
    }

    private fun paintFor(text: String, sticker: TemplateSticker) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = typefaceFor(LetteringFonts.choose(text))
        letterSpacing = TRACKING_EM
        color = sticker.ink
    }

    /** [line] with every "❤" (and an emoji selector after it) drawn as the app-mark heart. */
    private fun spanned(line: String): CharSequence {
        var index = line.indexOf(HEART)
        if (index < 0) return line
        val out = SpannableString(line)
        while (index >= 0) {
            val end = if (index + 1 < line.length && line[index + 1] == '️') index + 2 else index + 1
            out.setSpan(HeartSpan(), index, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            index = line.indexOf(HEART, end)
        }
        return out
    }

    /** A 0.78 em heart, bottom 0.12 em below the baseline, 0.08 em margins, ink colour, never stroked. */
    private class HeartSpan : ReplacementSpan() {

        override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
            if (fm != null) paint.getFontMetricsInt(fm)
            return ceil(paint.textSize * (SIZE_EM + 2 * MARGIN_EM)).toInt()
        }

        override fun draw(
            canvas: Canvas, text: CharSequence?, start: Int, end: Int,
            x: Float, top: Int, y: Int, bottom: Int, paint: Paint
        ) {
            if (paint.style != Paint.Style.FILL) return
            val size = paint.textSize * SIZE_EM
            val boxBottom = y + paint.textSize * DROP_EM
            canvas.drawPath(HeartPath.inBox(x + paint.textSize * MARGIN_EM, boxBottom - size, size), paint)
        }

        private companion object {
            const val SIZE_EM = 0.78f
            const val MARGIN_EM = 0.08f
            const val DROP_EM = 0.12f
        }
    }

    companion object {
        const val TRACKING_EM = -0.01f
        const val HEART = '❤'
    }
}
