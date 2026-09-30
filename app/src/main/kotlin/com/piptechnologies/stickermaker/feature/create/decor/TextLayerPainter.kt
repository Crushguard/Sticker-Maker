package com.piptechnologies.stickermaker.feature.create.decor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import com.piptechnologies.stickermaker.feature.namepack.engine.LetteringFonts
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

/**
 * Letters a text layer in its style (spec §5) into a bitmap with the text block at its centre.
 * The size is 52 px × scale and every style measure scales with it, so a render at scale 2 is
 * twice the size of one at scale 1. Lines are chosen once at scale 1, so they never re-wrap
 * while the layer is resized. [rtlLanguage] is read at render time (the bubble tail's side
 * and the paragraph direction follow it).
 *
 * The style box is the text block (or bubble) padded by the stroke half-width, the largest
 * shadow reach and the bubble border plus tail, plus 2 px. Letters can still reach past it
 * (tall marks above a 1.05 em line, overhanging script glyphs), so [render] grows the bitmap
 * evenly on both sides of an axis to keep them whole and the block centred.
 */
class TextLayerPainter(
    private val fonts: DecorFonts,
    private val book: TextStyleBook,
    private val rtlLanguage: () -> Boolean
) {

    /** The style box at scale 1, without drawing: what the editor scales, hit-tests and boxes. */
    fun baseSize(t: LayerContent.Text): Size2 = plan(t, 1f).let { Size2(it.width.toFloat(), it.height.toFloat()) }

    /** Whether [t] reads right to left: this decides the paragraph direction and the bubble tail's side. */
    fun isRtl(t: LayerContent.Text): Boolean = LetteringFonts.isRtlParagraph(t.text, rtlLanguage())

    /**
     * [t] drawn at [scale] into a new transparent bitmap: the style box, or larger where letters
     * reach past it. Without [softShadows] it is the shape the die-cut outline wraps: the
     * letters, their stroke and the hard drop, but not the blurred shadow or glow (spec §5).
     */
    fun render(t: LayerContent.Text, scale: Float, softShadows: Boolean = true): Bitmap {
        val p = plan(t, scale)
        val slack = ceil(SLACK_EM * p.textSize).toInt()
        val scratch = Bitmap.createBitmap(p.width + 2 * slack, p.height + 2 * slack, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(scratch)
        canvas.translate(slack.toFloat(), slack.toFloat())
        draw(canvas, t, p, softShadows)
        return trim(scratch, slack)
    }

    /** The bubble, then the under pass and the fill of every line, in the style box's coordinates. */
    private fun draw(canvas: Canvas, t: LayerContent.Text, p: Plan, softShadows: Boolean) {
        val rtl = isRtl(t)
        val paint = p.paint
        val direction = if (rtl) TextDirectionHeuristics.RTL else TextDirectionHeuristics.LTR
        val layouts = p.lines.mapIndexed { i, line ->
            StaticLayout.Builder.obtain(line, 0, line.length, paint, ceil(p.widths[i]).toInt() + 2)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setTextDirection(direction)
                .setIncludePad(false)
                .setMaxLines(1)
                .build()
        }
        val metrics = paint.fontMetrics
        val blockTop = (p.height - p.blockHeight) / 2f

        /** One pass over every line, each centred; the font's ascent + descent sit centred in the line box. */
        fun pass(dx: Float = 0f, dy: Float = 0f) = layouts.forEachIndexed { i, layout ->
            val baseline = blockTop + (i + 0.5f) * p.lineHeight - (metrics.ascent + metrics.descent) / 2f
            canvas.save()
            canvas.translate(p.width / 2f - layout.width / 2f + dx, baseline - layout.getLineBaseline(0) + dy)
            layout.draw(canvas)
            canvas.restore()
        }

        p.spec.bubble?.let { drawBubble(canvas, it, p, rtl) }
        // Under pass, carrying the soft shadow: the stroke (Sticker, Bold stroke) or the hard drop (Classic).
        val solid = p.spec.shadows.firstOrNull { it.blur == 0f }
        val soft = p.spec.shadows.firstOrNull { it.blur > 0f }
        if (softShadows && soft != null) {
            paint.setShadowLayer(soft.blur * p.k * BLUR_TO_RADIUS, soft.dx * p.k, soft.dy * p.k, soft.colour)
        }
        if (p.spec.strokeEm > 0f) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = p.spec.strokeEm * p.textSize
            paint.strokeJoin = Paint.Join.ROUND
            paint.color = p.spec.strokeColour ?: t.colour
            pass()
        } else if (solid != null) {
            paint.color = solid.colour
            pass(solid.dx * p.k, solid.dy * p.k)
        }
        paint.clearShadowLayer()
        paint.style = Paint.Style.FILL
        paint.color = p.spec.fill ?: t.colour
        pass()
    }

    /** [scratch] cut back to the style box ([slack] in from every side), widened evenly where ink reaches past it. */
    private fun trim(scratch: Bitmap, slack: Int): Bitmap {
        val w = scratch.width
        val h = scratch.height
        val pixels = IntArray(w * h)
        scratch.getPixels(pixels, 0, w, 0, 0, w, h)
        var left = w
        var right = -1
        var top = h
        var bottom = -1
        for (y in 0 until h) for (x in 0 until w) {
            if (pixels[y * w + x] ushr 24 > INK_ALPHA) {
                if (x < left) left = x
                if (x > right) right = x
                if (y < top) top = y
                bottom = y
            }
        }
        val overX = if (right < 0) 0 else maxOf(0, slack - left, right - (w - 1 - slack))
        val overY = if (bottom < 0) 0 else maxOf(0, slack - top, bottom - (h - 1 - slack))
        val x = slack - overX
        val y = slack - overY
        return Bitmap.createBitmap(scratch, x, y, w - 2 * x, h - 2 * y).also { if (it !== scratch) scratch.recycle() }
    }

    /** The white speech bubble with its tail at the bottom-start, as one outline (spec §5). */
    private fun drawBubble(canvas: Canvas, b: BubbleSpec, p: Plan, rtl: Boolean) {
        val k = p.k
        val body = RectF(
            (p.width - p.bodyWidth) / 2f, (p.height - p.bodyHeight) / 2f,
            (p.width + p.bodyWidth) / 2f, (p.height + p.bodyHeight) / 2f
        )
        val shape = Path().apply { addRoundRect(body, b.radius * k, b.radius * k, Path.Direction.CW) }
        val tailW = b.tailW * k
        val inset = TAIL_START_EM * p.size
        val start = if (rtl) body.right - inset - tailW else body.left + inset
        // The base reaches a little into the body, so the union leaves no seam on the bottom edge.
        val overlap = b.border * k
        val tail = Path().apply {
            moveTo(start, body.bottom - overlap)
            lineTo(start, body.bottom)
            lineTo(start + tailW / 2f, body.bottom + b.tailH * k)
            lineTo(start + tailW, body.bottom)
            lineTo(start + tailW, body.bottom - overlap)
            close()
        }
        shape.op(tail, Path.Op.UNION)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = b.fill }
        canvas.drawPath(shape, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = b.border * k
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = b.borderColour
        canvas.drawPath(shape, paint)
    }

    /** The paint, lines and geometry of [t] at [scale]; `k` = size / 52 scales every px value of the style. */
    private fun plan(t: LayerContent.Text, scale: Float): Plan {
        val spec = book.styles.getValue(t.style)
        val text = t.text.replace('\n', ' ').replace('\r', ' ')
        val textRatio = if (spec.bubble != null) BUBBLE_TEXT else 1f
        val atOne = paintFor(t, spec, TextStyleBook.DEFAULT_TEXT_PX * textRatio)
        val lines = linesOf(text) { Layout.getDesiredWidth(it, atOne) }
        val size = TextStyleBook.DEFAULT_TEXT_PX * scale
        val paint = if (scale == 1f) atOne else paintFor(t, spec, size * textRatio)
        val k = size / TextStyleBook.DEFAULT_TEXT_PX
        val widths = FloatArray(lines.size) { Layout.getDesiredWidth(lines[it], paint) }
        val lineHeight = paint.textSize * if (spec.bubble != null) BUBBLE_LINE_HEIGHT else LINE_HEIGHT
        val blockWidth = widths.maxOrNull() ?: 0f
        val blockHeight = lines.size * lineHeight
        val bodyWidth = blockWidth + 2f * (spec.bubble?.padH ?: 0f) * k
        val bodyHeight = blockHeight + 2f * (spec.bubble?.padV ?: 0f) * k
        val strokeHalf = spec.strokeEm * paint.textSize / 2f
        val shadowReach = spec.shadows.maxOfOrNull { max(abs(it.dx), abs(it.dy)) + it.blur } ?: 0f
        val bubbleReach = spec.bubble?.let { it.border + it.tailH } ?: 0f
        val pad = strokeHalf + (shadowReach + bubbleReach) * k + MARGIN_PX
        return Plan(
            spec = spec, paint = paint, lines = lines, widths = widths, size = size, k = k,
            lineHeight = lineHeight, blockHeight = blockHeight, bodyWidth = bodyWidth, bodyHeight = bodyHeight,
            width = ceil(bodyWidth + 2f * pad).toInt().coerceAtLeast(1),
            height = ceil(bodyHeight + 2f * pad).toInt().coerceAtLeast(1)
        )
    }

    private fun paintFor(t: LayerContent.Text, spec: TextStyleSpec, textSize: Float): TextPaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            val face = fonts.forText(t.text, spec.uiFont, t.font)
            typeface = face.typeface
            isFakeBoldText = face.fakeBold
            this.textSize = textSize
            letterSpacing = if (spec.id == TextStyleId.Classic || spec.id == TextStyleId.Sticker) TRACKING_EM else 0f
        }

    private class Plan(
        val spec: TextStyleSpec,
        val paint: TextPaint,
        val lines: List<String>,
        val widths: FloatArray,
        val size: Float,
        val k: Float,
        val lineHeight: Float,
        val blockHeight: Float,
        val bodyWidth: Float,
        val bodyHeight: Float,
        val width: Int,
        val height: Int
    ) {
        val textSize: Float get() = paint.textSize
    }

    private companion object {
        /** A line wider than this at scale 1 splits in two (spec §5 fit). */
        const val MAX_LINE_PX = 480f
        const val LINE_HEIGHT = 1.05f
        const val BUBBLE_LINE_HEIGHT = 1.1f
        const val BUBBLE_TEXT = 0.78f
        const val TRACKING_EM = -0.01f
        /** The tail's base starts this many em (of the layer size) in from the bubble's start edge. */
        const val TAIL_START_EM = 0.5f
        /** CSS blur → `setShadowLayer` radius. */
        const val BLUR_TO_RADIUS = 0.87f
        const val MARGIN_PX = 2f
        /** Room drawn around the style box for letters that reach past it, in em. */
        const val SLACK_EM = 0.5f
        /** Fainter pixels (about 3%) don't widen the bitmap. */
        const val INK_ALPHA = 8

        /** One line if it fits [MAX_LINE_PX], else the split at the space whose longer half is narrowest. */
        fun linesOf(text: String, measure: (String) -> Float): List<String> {
            if (measure(text) <= MAX_LINE_PX) return listOf(text)
            var best = listOf(text)
            var bestWidth = Float.MAX_VALUE
            for (i in text.indices) {
                if (text[i] != ' ') continue
                val first = text.substring(0, i).trimEnd()
                val second = text.substring(i + 1).trimStart()
                if (first.isEmpty() || second.isEmpty()) continue
                val width = max(measure(first), measure(second))
                if (width < bestWidth) {
                    bestWidth = width
                    best = listOf(first, second)
                }
            }
            return best
        }
    }
}
