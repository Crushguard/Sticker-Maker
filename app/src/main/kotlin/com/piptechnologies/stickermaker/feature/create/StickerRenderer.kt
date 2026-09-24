package com.piptechnologies.stickermaker.feature.create

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.Typeface
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlin.math.cos
import kotlin.math.sin

/**
 * One manual correction stroke, in canvas (512) coordinates.
 * Brush strokes restore the subject ([keep] = true), Erase strokes remove it.
 */
class MaskStroke(
    val keep: Boolean,
    val radiusPx: Float,
    val points: MutableList<PointF>
)

/**
 * All bitmap math of the cut-out editor and the exporter:
 * segmentation-confidence → alpha mask, Porter-Duff stroke painting, the
 * alpha-dilated white outline, the composite (outline + masked subject +
 * caption) and the WebP/PNG size-capped encoders.
 *
 * Masks are ARGB_8888 bitmaps that are white where the subject is kept, with
 * the confidence in the alpha channel; the subject is applied with DST_IN.
 */
object StickerRenderer {

    private const val SIZE = CreateSpec.CANVAS_SIZE

    // Caption metrics at 512 (the prototype draws 28px text on a 296px canvas).
    private const val TEXT_SIZE = 48f
    private const val TEXT_BOTTOM_PADDING = 28f
    private const val TEXT_MAX_WIDTH = SIZE - 32f
    private const val TEXT_SHADOW_DROP = 4f

    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private val dstInPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }

    /** Draws the mask's alpha as solid white — the outline and dilation paint. */
    private val whiteMaskPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
    }

    private fun keepPaint(radius: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = radius * 2f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private fun erasePaint(radius: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        style = Paint.Style.STROKE
        strokeWidth = radius * 2f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    // ---------------------------------------------------------------- masks

    /**
     * Converts a raw segmentation confidence buffer ([maskWidth] x [maskHeight]
     * floats, 0..1) into a [SIZE] px ARGB mask with a soft alpha ramp around
     * the 0.5 confidence threshold.
     */
    fun maskFromConfidence(buffer: ByteBuffer, maskWidth: Int, maskHeight: Int): Bitmap {
        buffer.rewind()
        val pixels = IntArray(maskWidth * maskHeight)
        for (i in pixels.indices) {
            val confidence = buffer.float
            // Soft edge: fully out below 0.35, fully in above 0.65.
            val alpha = (((confidence - 0.35f) / 0.30f).coerceIn(0f, 1f) * 255f).toInt()
            pixels[i] = (alpha shl 24) or 0x00FFFFFF
        }
        val small = Bitmap.createBitmap(pixels, maskWidth, maskHeight, Bitmap.Config.ARGB_8888)
        if (maskWidth == SIZE && maskHeight == SIZE) return small
        val out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val matrix = Matrix().apply {
            setScale(SIZE.toFloat() / maskWidth, SIZE.toFloat() / maskHeight)
        }
        canvas.drawBitmap(small, matrix, bitmapPaint)
        small.recycle()
        return out
    }

    /** Fully opaque mask — the fallback when segmentation finds nothing. */
    fun fullMask(): Bitmap =
        Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
        }

    /** Paints one stroke segment (or a dot when [from] == [to]) into [mask]. */
    fun drawStrokeSegment(mask: Bitmap, keep: Boolean, radiusPx: Float, from: PointF, to: PointF) {
        val canvas = Canvas(mask)
        val paint = if (keep) keepPaint(radiusPx) else erasePaint(radiusPx)
        if (from.x == to.x && from.y == to.y) {
            canvas.drawPoint(from.x, from.y, paint)
        } else {
            canvas.drawLine(from.x, from.y, to.x, to.y, paint)
        }
    }

    /** Auto mask + every stroke replayed, into a new bitmap (undo rebuilds). */
    fun rebuildMask(autoMask: Bitmap, strokes: List<MaskStroke>): Bitmap {
        val out = autoMask.copy(Bitmap.Config.ARGB_8888, true)
        strokes.forEach { stroke -> applyStroke(out, stroke) }
        return out
    }

    /** Replays one full stroke into [mask]. */
    fun applyStroke(mask: Bitmap, stroke: MaskStroke) {
        var previous: PointF? = null
        for (point in stroke.points) {
            drawStrokeSegment(mask, stroke.keep, stroke.radiusPx, previous ?: point, point)
            previous = point
        }
    }

    // -------------------------------------------------------------- outline

    /**
     * The white die-cut outline: the mask alpha dilated by [radiusPx] and
     * painted solid white (drawn beneath the subject). Dilation is a ring of
     * offset draws — the raster version of the prototype's stacked
     * drop-shadow trick.
     */
    fun outlineOf(mask: Bitmap, radiusPx: Float = CreateSpec.OUTLINE_RADIUS): Bitmap {
        val out = Bitmap.createBitmap(mask.width, mask.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val outer = 16
        for (i in 0 until outer) {
            val angle = Math.PI * 2 * i / outer
            canvas.drawBitmap(
                mask,
                (cos(angle) * radiusPx).toFloat(),
                (sin(angle) * radiusPx).toFloat(),
                whiteMaskPaint
            )
        }
        val inner = 8
        for (i in 0 until inner) {
            val angle = Math.PI * 2 * i / inner
            canvas.drawBitmap(
                mask,
                (cos(angle) * radiusPx * 0.5f).toFloat(),
                (sin(angle) * radiusPx * 0.5f).toFloat(),
                whiteMaskPaint
            )
        }
        canvas.drawBitmap(mask, 0f, 0f, whiteMaskPaint)
        return out
    }

    // ------------------------------------------------------------ composite

    /**
     * Draws the full sticker (optional outline, masked subject, optional
     * caption) onto [canvas] in 512-space. Shared by the live editor canvas
     * and the exporter so what you see is what exports.
     */
    fun drawComposite(
        canvas: Canvas,
        source: Bitmap,
        mask: Bitmap,
        outline: Bitmap?,
        text: String,
        typeface: Typeface?
    ) {
        if (outline != null) {
            canvas.drawBitmap(outline, 0f, 0f, bitmapPaint)
        }
        val checkpoint = canvas.saveLayer(0f, 0f, SIZE.toFloat(), SIZE.toFloat(), null)
        canvas.drawBitmap(source, 0f, 0f, bitmapPaint)
        canvas.drawBitmap(mask, 0f, 0f, dstInPaint)
        canvas.restoreToCount(checkpoint)
        if (text.isNotBlank()) {
            drawCaption(canvas, text.trim(), typeface)
        }
    }

    /** Renders the composite into a fresh transparent 512 bitmap. */
    fun renderComposite(
        source: Bitmap,
        mask: Bitmap,
        outline: Bitmap?,
        text: String,
        typeface: Typeface?
    ): Bitmap {
        val out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        drawComposite(Canvas(out), source, mask, outline, text, typeface)
        return out
    }

    /**
     * The exported 512x512 frame: the composite scaled into the centred
     * [CreateSpec.EXPORT_CONTENT] square, leaving the transparent margin
     * WhatsApp recommends.
     */
    fun renderExportCanvas(composite: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val scale = CreateSpec.EXPORT_CONTENT.toFloat() / SIZE
        val inset = (SIZE - CreateSpec.EXPORT_CONTENT) / 2f
        val matrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate(inset, inset)
        }
        canvas.drawBitmap(composite, matrix, bitmapPaint)
        return out
    }

    /** Bold white caption with a hard dark drop and a soft glow, like the design. */
    private fun drawCaption(canvas: Canvas, text: String, typeface: Typeface?) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface ?: Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            textSize = TEXT_SIZE
        }
        while (paint.measureText(text) > TEXT_MAX_WIDTH && paint.textSize > 20f) {
            paint.textSize = paint.textSize - 2f
        }
        val x = SIZE / 2f
        val y = SIZE - TEXT_BOTTOM_PADDING - paint.fontMetrics.descent
        paint.color = Color.parseColor("#1E2128")
        paint.setShadowLayer(14f, 0f, 0f, 0x59000000)
        canvas.drawText(text, x, y + TEXT_SHADOW_DROP, paint)
        paint.clearShadowLayer()
        paint.color = Color.WHITE
        canvas.drawText(text, x, y, paint)
    }

    /** Small [size] px preview of a composite for the rails and the tray box. */
    fun thumbOf(composite: Bitmap, size: Int = CreateSpec.TRAY_SIZE): Bitmap {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val matrix = Matrix().apply {
            setScale(size.toFloat() / composite.width, size.toFloat() / composite.height)
        }
        canvas.drawBitmap(composite, matrix, bitmapPaint)
        return out
    }

    // ------------------------------------------------------------- encoders

    /** One static WebP encode. WEBP_LOSSY needs API 30; older builds use WEBP. */
    fun encodeWebp(bitmap: Bitmap, quality: Int): ByteArray {
        val stream = ByteArrayOutputStream()
        val format = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }
        bitmap.compress(format, quality.coerceIn(1, 99), stream)
        return stream.toByteArray()
    }

    /**
     * Quality-loops a static sticker under [limitBytes] (WhatsApp's 100 KB).
     * Returns the first fit, or the smallest attempt when nothing fits.
     */
    fun encodeStaticSticker(
        bitmap: Bitmap,
        limitBytes: Int = CreateSpec.STATIC_LIMIT_BYTES
    ): ByteArray {
        var best: ByteArray? = null
        for (quality in intArrayOf(95, 85, 75, 65, 55, 45, 35, 30)) {
            val bytes = encodeWebp(bitmap, quality)
            if (best == null || bytes.size < best.size) best = bytes
            if (bytes.size <= limitBytes) return bytes
        }
        return checkNotNull(best)
    }

    /** PNG tray icon, 96 px (72 px fallback keeps it under 50 KB). */
    fun encodeTrayPng(composite: Bitmap): ByteArray {
        var size = CreateSpec.TRAY_SIZE
        while (true) {
            val thumb = thumbOf(composite, size)
            val stream = ByteArrayOutputStream()
            thumb.compress(Bitmap.CompressFormat.PNG, 100, stream)
            thumb.recycle()
            val bytes = stream.toByteArray()
            if (bytes.size <= CreateSpec.TRAY_LIMIT_BYTES || size <= 48) return bytes
            size -= 24
        }
    }
}
