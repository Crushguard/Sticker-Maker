package com.piptechnologies.stickermaker.feature.create.decor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.LruCache
import com.piptechnologies.stickermaker.feature.create.StickerRenderer
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * One layer drawn at [renderScale] (its scale bucket): [bitmap] holds the content centred on the
 * layer centre, [silhouette] its outline shape (white, centred on the same point) when the
 * outline is on. Both are shared from the cache: never recycle them.
 */
class RenderedLayer(val bitmap: Bitmap, val silhouette: Bitmap?, val renderScale: Float)

/**
 * Layer bitmaps and silhouettes at the layer's current size (spec §5, §6), so a live frame is a
 * handful of bitmap draws. [baseSize] is the size at scale 1 the editor works with (spec §3).
 */
class LayerRenderCache(
    private val assets: DecorAssets,
    private val painter: TextLayerPainter,
    private val data: DecorData
) {
    private val marker = MarkerInk(data.styles)
    private val rendered = LruCache<Key, RenderedLayer>(MAX_ENTRIES)

    /** The content's size in canvas px at scale 1. */
    fun baseSize(content: LayerContent): Size2 = when (content) {
        is LayerContent.Text -> painter.baseSize(content)
        is LayerContent.Emoji -> imageSize(DecorSpec.EMOJI_WIDTH, "emoji/${content.file}")
        is LayerContent.Decor ->
            imageSize(data.decor.byFile(content.file)?.defaultWidth ?: DecorSpec.EMOJI_WIDTH, "decor/${content.file}")
        is LayerContent.Drawing -> drawingSize(content)
    }

    /**
     * [layer]'s content at its scale quantised to 1/64, with its silhouette dilated by
     * [outlineRadius] (canvas px; null when the outline is off). At most [MAX_ENTRIES] stay cached.
     */
    fun render(layer: Layer, outlineRadius: Float?): RenderedLayer {
        val bucket = (layer.scale * SCALE_STEPS).roundToInt().coerceAtLeast(1)
        val content = layer.content
        val key = Key(content, bucket, outlineRadius, (content as? LayerContent.Text)?.let(painter::isRtl) ?: false)
        rendered.get(key)?.let { return it }
        val scale = bucket / SCALE_STEPS
        val bitmap = when (content) {
            is LayerContent.Text -> painter.render(content, scale)
            is LayerContent.Emoji -> image(assets.emoji(content.file, content.tone), baseSize(content), scale)
            is LayerContent.Decor -> image(assets.decor(content.file), baseSize(content), scale)
            is LayerContent.Drawing -> drawing(content, scale)
        }
        val silhouette = outlineRadius?.let { radius ->
            // Text is outlined around its letters, stroke and hard drop, not its blurred shadow (spec §5).
            val text = content as? LayerContent.Text
            val shape = text?.let { painter.render(it, scale, softShadows = false) } ?: bitmap
            silhouetteOf(shape, radius).also { if (shape !== bitmap) shape.recycle() }
        }
        return RenderedLayer(bitmap, silhouette, scale).also { rendered.put(key, it) }
    }

    /** Drops every cached render (the data or the language changed). */
    fun clear() = rendered.evictAll()

    private fun imageSize(widthFraction: Float, path: String): Size2 {
        val w = widthFraction * DecorSpec.CANVAS
        return Size2(w, w * assets.aspect(path))
    }

    /** The strokes' extent around the layer centre plus the widest ink (flushed strokes are centred: their box). */
    private fun drawingSize(d: LayerContent.Drawing): Size2 {
        val points = d.strokes.flatMap { it.points }
        val ink = d.strokes.maxOfOrNull { marker.inkWidth(it.size) } ?: 0f
        val halfW = points.maxOfOrNull { abs(it.x) } ?: 0f
        val halfH = points.maxOfOrNull { abs(it.y) } ?: 0f
        return Size2(2f * halfW + ink, 2f * halfH + ink)
    }

    /** [source] scaled into a fresh bitmap of the layer's size at [scale]; an unreadable source stays transparent. */
    private fun image(source: Bitmap?, base: Size2, scale: Float): Bitmap {
        val out = Bitmap.createBitmap(pixels(base.w * scale), pixels(base.h * scale), Bitmap.Config.ARGB_8888)
        if (source != null) {
            val dst = RectF(0f, 0f, out.width.toFloat(), out.height.toFloat())
            Canvas(out).drawBitmap(source, null, dst, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        }
        return out
    }

    /** The strokes (relative to the layer centre) around the bitmap centre, at [scale]. */
    private fun drawing(d: LayerContent.Drawing, scale: Float): Bitmap {
        val base = drawingSize(d)
        val out = Bitmap.createBitmap(pixels(base.w * scale), pixels(base.h * scale), Bitmap.Config.ARGB_8888)
        Canvas(out).apply {
            translate(out.width / 2f, out.height / 2f)
            scale(scale, scale)
            marker.draw(this, d.strokes)
        }
        return out
    }

    /** The outline shape: [bitmap] padded by the radius + 2 on every side, dilated by [radius], white. */
    private fun silhouetteOf(bitmap: Bitmap, radius: Float): Bitmap {
        val pad = ceil(radius).toInt() + 2
        val padded = Bitmap.createBitmap(bitmap.width + 2 * pad, bitmap.height + 2 * pad, Bitmap.Config.ARGB_8888)
        Canvas(padded).drawBitmap(bitmap, pad.toFloat(), pad.toFloat(), null)
        return StickerRenderer.outlineOf(padded, radius).also { padded.recycle() }
    }

    private fun pixels(v: Float): Int = v.roundToInt().coerceAtLeast(1)

    private data class Key(val content: LayerContent, val bucket: Int, val radius: Float?, val rtl: Boolean)

    private companion object {
        const val SCALE_STEPS = 64f
        const val MAX_ENTRIES = 48
    }
}

/**
 * The Draw marker (spec §5): each stroke is an ink edge (`markerEdgeExtra` px wider on each side,
 * in `markerEdgeColour`), then its colour; round caps and joins; quadratic segments through the
 * midpoints of its points. Shared by the Drawing layers and the live strokes.
 */
internal class MarkerInk(private val book: TextStyleBook) {

    /** The full drawn width of a stroke: the colour plus the ink edge on both sides. */
    fun inkWidth(size: MarkerSize): Float = book.markerPx.getValue(size) + 2f * book.markerEdgeExtra

    /** Each stroke in order: its ink edge, then its colour, so a later stroke crosses over an earlier one. */
    fun draw(canvas: Canvas, strokes: List<MarkerStroke>) {
        val paint = strokePaint()
        strokes.forEach { s ->
            val path = pathOf(s.points)
            paint.color = book.markerEdgeColour
            paint.strokeWidth = inkWidth(s.size)
            canvas.drawPath(path, paint)
            paint.color = s.colour
            paint.strokeWidth = book.markerPx.getValue(s.size)
            canvas.drawPath(path, paint)
        }
    }

    /** The strokes' share of the outline: stroked [radius] wider on each side than their ink, in [colour]. */
    fun drawOutline(canvas: Canvas, strokes: List<MarkerStroke>, radius: Float, colour: Int) {
        val paint = strokePaint().apply { color = colour }
        strokes.forEach { s ->
            paint.strokeWidth = inkWidth(s.size) + 2f * radius
            canvas.drawPath(pathOf(s.points), paint)
        }
    }

    private fun strokePaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private fun pathOf(points: List<Pt>): Path = Path().apply {
        if (points.isEmpty()) return@apply
        moveTo(points[0].x, points[0].y)
        if (points.size == 1) {
            // A tap: a zero-length segment, which the round cap draws as a dot.
            lineTo(points[0].x, points[0].y)
            return@apply
        }
        for (i in 1 until points.size - 1) {
            val p = points[i]
            val next = points[i + 1]
            quadTo(p.x, p.y, (p.x + next.x) / 2f, (p.y + next.y) / 2f)
        }
        lineTo(points.last().x, points.last().y)
    }
}
