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
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * One layer drawn at [renderScale] (its size step): [bitmap] holds the content centred on the layer
 * centre, [silhouette] its outline shape (white, centred on the same point) when the outline is on.
 * Both are shared from the cache: never recycle them.
 */
class RenderedLayer(val bitmap: Bitmap, val silhouette: Bitmap?, val renderScale: Float)

/**
 * Layer bitmaps and silhouettes at the layer's current size (spec §5, §6), so a live frame is a
 * handful of bitmap draws. [baseSize] is the size at scale 1 the editor works with (spec §3).
 *
 * Sizes go in steps of 2^(1/16), about 4.4%, as accurate for a small layer as for a large one.
 * Contents and silhouettes are cached apart, so an outline change only re-dilates. At most
 * [MAX_BYTES] stay cached; evicted bitmaps are left to the garbage collector, never recycled,
 * because a frame being drawn may still hold them.
 */
class LayerRenderCache(
    private val assets: DecorAssets,
    private val painter: TextLayerPainter,
    private val data: DecorData
) {
    private val marker = MarkerInk(data.styles)
    private val bitmaps = object : LruCache<Key, Bitmap>(MAX_BYTES) {
        override fun sizeOf(key: Key, value: Bitmap): Int = value.byteCount
    }

    /** Bytes held by the cached contents and silhouettes, never more than [MAX_BYTES]. */
    internal val cachedBytes: Int get() = bitmaps.size()

    /** The content's size in canvas px at scale 1. */
    fun baseSize(content: LayerContent): Size2 = when (content) {
        is LayerContent.Text -> painter.baseSize(content)
        is LayerContent.Emoji -> imageSize(DecorSpec.EMOJI_WIDTH, "emoji/${content.file}")
        is LayerContent.Decor ->
            imageSize(data.decor.byFile(content.file)?.defaultWidth ?: DecorSpec.EMOJI_WIDTH, "decor/${content.file}")
        is LayerContent.Drawing -> drawingSize(content)
    }

    /**
     * [layer]'s content at its scale (to the nearest step) and, when [outlineRadius] is given (canvas
     * px), its silhouette. While [live] (a gesture is resizing the layer), the nearest step already
     * cached is returned instead of rendering a new one on every frame; the caller scales it by
     * `layer.scale / renderScale`, and the exact step renders once the gesture ends.
     */
    fun render(layer: Layer, outlineRadius: Float?, live: Boolean = false): RenderedLayer {
        val content = layer.content
        val rtl = (content as? LayerContent.Text)?.let(painter::isRtl) ?: false
        val exact = ContentKey(content, rtl, stepOf(layer.scale))
        val key = if (live) nearestCached(exact) ?: exact else exact
        val scale = scaleOf(key.step)
        val bitmap = cached(key) { draw(content, scale) }
        val silhouette = outlineRadius?.let { radius ->
            cached(SilhouetteKey(key, radius)) { silhouetteOf(content, bitmap, scale, radius) }
        }
        return RenderedLayer(bitmap, silhouette, scale)
    }

    /** Drops every cached bitmap (the editor closed, or memory is short). */
    fun clear() = bitmaps.evictAll()

    private fun cached(key: Key, make: () -> Bitmap): Bitmap = bitmaps.get(key) ?: make().also { bitmaps.put(key, it) }

    /** The cached step of [exact]'s content (and text direction) nearest to it, if any. */
    private fun nearestCached(exact: ContentKey): ContentKey? = bitmaps.snapshot().keys.asSequence()
        .filterIsInstance<ContentKey>()
        .filter { it.content == exact.content && it.rtl == exact.rtl }
        .minByOrNull { abs(it.step - exact.step) }

    private fun draw(content: LayerContent, scale: Float): Bitmap = when (content) {
        is LayerContent.Text -> painter.render(content, scale)
        is LayerContent.Emoji -> image(assets.emoji(content.file, content.tone), baseSize(content), scale)
        is LayerContent.Decor -> image(assets.decor(content.file), baseSize(content), scale)
        is LayerContent.Drawing -> drawing(content, scale)
    }

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

    /** The outline shape; text is outlined around its letters, stroke and hard drop, not its soft shadow (spec §5). */
    private fun silhouetteOf(content: LayerContent, bitmap: Bitmap, scale: Float, radius: Float): Bitmap {
        val shape = (content as? LayerContent.Text)?.let { painter.render(it, scale, softShadows = false) } ?: bitmap
        return dilated(shape, radius).also { if (shape !== bitmap) shape.recycle() }
    }

    /** [bitmap] padded by the radius + 2 on every side, dilated by [radius], white. */
    private fun dilated(bitmap: Bitmap, radius: Float): Bitmap {
        val pad = ceil(radius).toInt() + 2
        val padded = Bitmap.createBitmap(bitmap.width + 2 * pad, bitmap.height + 2 * pad, Bitmap.Config.ARGB_8888)
        Canvas(padded).drawBitmap(bitmap, pad.toFloat(), pad.toFloat(), null)
        return StickerRenderer.outlineOf(padded, radius).also { padded.recycle() }
    }

    private fun pixels(v: Float): Int = v.roundToInt().coerceAtLeast(1)

    private sealed interface Key

    /** A content at a size step; [rtl] only matters for text (paragraph order, bubble tail side). */
    private data class ContentKey(val content: LayerContent, val rtl: Boolean, val step: Int) : Key

    private data class SilhouetteKey(val of: ContentKey, val radius: Float) : Key

    internal companion object {
        /** The bound on cached contents and silhouettes together. */
        const val MAX_BYTES = 32 * 1024 * 1024
        private const val STEPS_PER_DOUBLING = 16f
        /** Far beyond what the editor allows (layers stay 10–100% of the canvas wide), so no step is absurd. */
        private const val MIN_SCALE = 1f / 256f
        private const val MAX_SCALE = 32f

        /** The step of [scale], log2(scale) × 16 rounded; a non-finite scale draws at full size. */
        private fun stepOf(scale: Float): Int {
            val s = if (scale.isFinite()) scale.coerceIn(MIN_SCALE, MAX_SCALE) else 1f
            return (log2(s) * STEPS_PER_DOUBLING).roundToInt()
        }

        private fun scaleOf(step: Int): Float = 2f.pow(step / STEPS_PER_DOUBLING)
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
