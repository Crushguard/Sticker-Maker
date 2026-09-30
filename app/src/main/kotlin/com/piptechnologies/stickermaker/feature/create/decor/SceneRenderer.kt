package com.piptechnologies.stickermaker.feature.create.decor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import androidx.core.graphics.PathParser
import com.piptechnologies.stickermaker.feature.create.CreateSpec
import com.piptechnologies.stickermaker.feature.namepack.engine.HeartPath
import kotlin.math.roundToInt

/**
 * Draws one decorated sticker in 512 canvas space (spec §5), for the live canvas and the
 * exporter alike, so what the user sees is what exports: the die-cut outline as one shape,
 * behind layers, the subject, front layers and live Draw strokes. Also renders animation
 * frames from a rest composite, with their particles (spec §7).
 */
class SceneRenderer(private val cache: LayerRenderCache, private val data: DecorData) {

    /**
     * What one sticker frame shows: [subject] is the source already masked by the cut-out,
     * [subjectSilhouette] its outline at the current thickness (both 512 px); [liveStrokes] are
     * the Draw strokes not yet turned into a layer, in canvas px.
     */
    class Scene(
        val subject: Bitmap?,
        val subjectSilhouette: Bitmap?,
        val decor: DecorState,
        val liveStrokes: List<MarkerStroke> = emptyList()
    )

    private val marker = MarkerInk(data.styles)

    /** A four-point star in a 24 box, centred on the origin with a unit edge. */
    private val star: Path by lazy {
        PathParser.createPathFromPathData(STAR_PATH).apply {
            transform(Matrix().apply {
                setTranslate(-STAR_BOX / 2f, -STAR_BOX / 2f)
                postScale(1f / STAR_BOX, 1f / STAR_BOX)
            })
        }
    }

    /** The working outline radius: the thickness seen in the export, which fits 512 into 460. */
    fun outlineRadius(t: OutlineThickness): Float =
        data.styles.outlinePx.getValue(t) * CreateSpec.CANVAS_SIZE / CreateSpec.EXPORT_CONTENT

    /** Draws [scene] onto [canvas] (512 space); [dimLayers] shows the layers at 40% (Brush and Erase). */
    fun drawSticker(canvas: Canvas, scene: Scene, dimLayers: Boolean = false) {
        val outline = scene.decor.outline
        val radius = if (outline.on) outlineRadius(outline.thickness) else null
        val layers = scene.decor.layers.map { it to cache.render(it, radius) }
        val layerAlpha = if (dimLayers) DIM_ALPHA else OPAQUE
        if (radius != null) {
            val tint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter = PorterDuffColorFilter(outline.colour, PorterDuff.Mode.SRC_IN)
            }
            scene.subjectSilhouette?.let { canvas.drawBitmap(it, 0f, 0f, tint) }
            tint.alpha = layerAlpha
            layers.forEach { (layer, r) ->
                r.silhouette?.let { canvas.drawBitmap(it, matrixOf(layer, r, it), tint) }
            }
            marker.drawOutline(canvas, scene.liveStrokes, radius, outline.colour)
        }
        val subjectPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val layerPaint = Paint(subjectPaint).apply { alpha = layerAlpha }
        fun drawLayers(behind: Boolean) = layers.forEach { (layer, r) ->
            if (layer.behind == behind) canvas.drawBitmap(r.bitmap, matrixOf(layer, r), layerPaint)
        }
        drawLayers(behind = true)
        scene.subject?.let { canvas.drawBitmap(it, 0f, 0f, subjectPaint) }
        drawLayers(behind = false)
        drawStrokes(canvas, scene.liveStrokes)
    }

    /** [scene] in a new transparent 512 bitmap: the rest composite of an animated sticker. */
    fun renderStill(scene: Scene): Bitmap {
        val out = Bitmap.createBitmap(CreateSpec.CANVAS_SIZE, CreateSpec.CANVAS_SIZE, Bitmap.Config.ARGB_8888)
        drawSticker(Canvas(out), scene)
        return out
    }

    /** Hearts and four-point stars, each at its centre, scale, rotation and alpha. */
    fun drawParticles(canvas: Canvas, particles: List<ParticleState>) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        particles.forEach { p ->
            if (p.alpha <= 0f || p.scale <= 0f) return@forEach
            val alpha = (p.alpha.coerceAtMost(1f) * OPAQUE).roundToInt()
            canvas.save()
            canvas.translate(p.x, p.y)
            canvas.scale(p.scale, p.scale)
            canvas.rotate(p.rotation)
            paint.color = p.colour
            paint.alpha = alpha
            when (p.kind) {
                ParticleKind.Hearts -> canvas.drawPath(HeartPath.inBox(-p.size / 2f, -p.size / 2f, p.size), paint)
                ParticleKind.Sparkle -> {
                    canvas.drawPath(starOf(p.size), paint)
                    paint.color = Color.WHITE
                    paint.alpha = alpha
                    canvas.drawPath(starOf(p.size * SPARKLE_CORE), paint)
                }
            }
            canvas.restore()
        }
    }

    /** Live Draw strokes (canvas px): per stroke, the ink edge, then the colour. */
    fun drawStrokes(canvas: Canvas, strokes: List<MarkerStroke>) = marker.draw(canvas, strokes)

    /** Frame [index] of [preset]: [rest] under the transform at `index / frames`, then the particles, unscaled. */
    fun renderFrame(rest: Bitmap, preset: MotionPreset, index: Int): Bitmap {
        val out = Bitmap.createBitmap(CreateSpec.CANVAS_SIZE, CreateSpec.CANVAS_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val frames = preset.frames.coerceAtLeast(1)
        canvas.save()
        val transform = MotionMath.transform(preset, index.toFloat() / frames)
        canvas.concat(Matrix().apply { setValues(transform.matrixValues()) })
        canvas.drawBitmap(rest, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        canvas.restore()
        drawParticles(canvas, MotionMath.particles(preset, index * preset.durationMs.toFloat() / frames))
        return out
    }

    /** `translate(cx, cy) · rotate · scale(±s, s) · translate(−w/2, −h/2)`, s = layer scale / render scale. */
    private fun matrixOf(layer: Layer, r: RenderedLayer, bitmap: Bitmap = r.bitmap): Matrix = Matrix().apply {
        val s = layer.scale / r.renderScale
        setTranslate(-bitmap.width / 2f, -bitmap.height / 2f)
        postScale(if (layer.flipped) -s else s, s)
        postRotate(layer.rotation)
        postTranslate(layer.cx, layer.cy)
    }

    private fun starOf(size: Float): Path = Path().also { star.transform(Matrix().apply { setScale(size, size) }, it) }

    private companion object {
        const val OPAQUE = 255
        /** Layers at 40% while Brush or Erase is active (spec §6). */
        const val DIM_ALPHA = 102
        const val STAR_PATH = "M12 1c.7 6 5 10.3 11 11-6 .7-10.3 5-11 11-.7-6-5-10.3-11-11 6-.7 10.3-5 11-11z"
        const val STAR_BOX = 24f
        /** The sparkle's white core, as a share of the star (spec §7). */
        const val SPARKLE_CORE = 0.4f
    }
}
