package com.piptechnologies.stickermaker.feature.create.editor

import androidx.compose.ui.geometry.Offset
import com.piptechnologies.stickermaker.feature.create.CreateSpec
import com.piptechnologies.stickermaker.feature.create.LayerUi
import com.piptechnologies.stickermaker.feature.create.decor.Affine
import com.piptechnologies.stickermaker.feature.create.decor.MotionMath
import com.piptechnologies.stickermaker.feature.create.decor.MotionPreset
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

// The Cut out canvas's geometry, free of Compose state so it can be unit-tested: the map between
// the 512 canvas and the canvas box, the selection box and its handles, and the small rules of the
// tool bar, the action pill, and the taps, drags and pinches on the canvas.

/** The second tap of a double tap lands within this many ms of the first one lifting. */
internal const val DOUBLE_TAP_MS = 300L

// ----------------------------------------------------------------- mapping

/**
 * Canvas px → canvas-box px (spec §6): the 512 canvas fitted into an [edge]-px box, zoomed by
 * [zoom] about the box centre and panned by [panX], [panY] box px.
 */
internal fun viewTransform(edge: Float, zoom: Float, panX: Float, panY: Float): Affine {
    val k = edge / CreateSpec.CANVAS_SIZE * zoom
    val shift = edge / 2f * (1f - zoom)
    return Affine(k, 0f, 0f, k, panX + shift, panY + shift)
}

/**
 * [preset]'s pose at [timeMs] into its loop (spec §7), in canvas px: the rest pose at 0, which
 * already shrinks the sticker by the preset's base scale. The identity without a preset.
 */
internal fun motionPose(preset: MotionPreset?, timeMs: Float): Affine {
    if (preset == null || preset.isNone) return Affine.IDENTITY
    val t = if (preset.durationMs > 0) timeMs / preset.durationMs else 0f
    return MotionMath.transform(preset, t)
}

/** The inverse map; the identity when this one can't be inverted (a box not laid out yet). */
internal fun Affine.inverse(): Affine {
    val det = a * d - b * c
    if (det == 0f || !det.isFinite()) return Affine.IDENTITY
    return Affine(d / det, -b / det, -c / det, a / det, (c * f - d * e) / det, (b * e - a * f) / det)
}

/** [p] through this map. */
internal fun Affine.mapPoint(p: Offset): Offset = Offset(a * p.x + c * p.y + e, b * p.x + d * p.y + f)

/** The move [v] through this map's linear part: a displacement, which translation leaves alone. */
internal fun Affine.mapVector(v: Offset): Offset = Offset(a * v.x + c * v.y, b * v.x + d * v.y)

/** A pan that keeps a view zoomed by [zoom] covering its [edge]-px box: at most (zoom − 1) × edge / 2 either way. */
internal fun clampPan(pan: Float, zoom: Float, edge: Float): Float {
    val max = (zoom - 1f).coerceAtLeast(0f) * edge / 2f
    return pan.coerceIn(-max, max)
}

// ----------------------------------------------------------- selection box

/**
 * A layer as the canvas box shows it: its [centre], the unit axes of its own frame ([axisX] along its
 * width, [axisY] along its height, turned with it) and its half size along them, all in box px.
 */
internal class LayerBox(
    val centre: Offset,
    val axisX: Offset,
    val axisY: Offset,
    val halfWidth: Float,
    val halfHeight: Float
) {
    /** The layer's rotation on screen, in degrees clockwise. */
    val angle: Float get() = Math.toDegrees(atan2(axisX.y, axisX.x).toDouble()).toFloat()

    /**
     * A corner of the box grown by [outset] px on every side: [x] is −1 on the layer's left edge and 1
     * on its right, [y] −1 on its top and 1 on its bottom, in the layer's own frame.
     */
    fun corner(x: Float, y: Float, outset: Float): Offset =
        centre + axisX * (x * (halfWidth + outset)) + axisY * (y * (halfHeight + outset))
}

/** [layer] (canvas px) as [frame] shows it: canvas px → box px, with the zoom, the pan and the motion pose. */
internal fun layerBox(layer: LayerUi, frame: Affine): LayerBox {
    val radians = Math.toRadians(layer.rotation.toDouble())
    val cos = cos(radians).toFloat()
    val sin = sin(radians).toFloat()
    val alongWidth = frame.mapVector(Offset(cos, sin))
    val alongHeight = frame.mapVector(Offset(-sin, cos))
    val kx = alongWidth.getDistance().takeIf { it > 0f } ?: 1f
    val ky = alongHeight.getDistance().takeIf { it > 0f } ?: 1f
    return LayerBox(
        centre = frame.mapPoint(Offset(layer.cx, layer.cy)),
        axisX = alongWidth / kx,
        axisY = alongHeight / ky,
        halfWidth = layer.width / 2f * kx,
        halfHeight = layer.height / 2f * ky
    )
}

/** Where a selected layer's three handles sit (spec §8): corners of its box grown by the outset, in box px. */
internal class HandleCorners(val delete: Offset, val edit: Offset, val transform: Offset)

/**
 * Delete at the box's top-start corner, edit at its top-end and resize-and-rotate at its bottom-end, in
 * the layer's own frame (they turn with it); start and end swap in a right-to-left layout ([rtl]).
 */
internal fun handleCorners(box: LayerBox, outset: Float, rtl: Boolean): HandleCorners {
    val start = if (rtl) 1f else -1f
    return HandleCorners(
        delete = box.corner(start, -1f, outset),
        edit = box.corner(-start, -1f, outset),
        transform = box.corner(-start, 1f, outset)
    )
}

/** A layer's scale and rotation (degrees clockwise). */
internal data class ScaleRotation(val scale: Float, val rotation: Float)

/**
 * The resize-and-rotate handle (spec §8): the finger went from [from] to [to] around the layer's
 * [centre] (box px). The scale follows its distance to the centre and the rotation its angle, from the
 * [scale] and [rotation] the layer had when the handle was grabbed.
 */
internal fun handleTransform(centre: Offset, from: Offset, to: Offset, scale: Float, rotation: Float): ScaleRotation {
    val grabbed = from - centre
    val now = to - centre
    val reach = grabbed.getDistance()
    if (reach <= 0f) return ScaleRotation(scale, rotation)
    val turn = Math.toDegrees((atan2(now.y, now.x) - atan2(grabbed.y, grabbed.x)).toDouble()).toFloat()
    return ScaleRotation(scale * now.getDistance() / reach, rotation + turn)
}

// ------------------------------------------------------------------ rules

/**
 * The tool bar's label size (spec §8 fallback): 10 sp while [widest] (the widest label's px at a size)
 * fits [slotPx], else 9.5 sp, else 0: the labels hide and each button keeps its label for TalkBack.
 */
internal fun toolLabelSp(slotPx: Float, widest: (sp: Float) -> Int): Float = when {
    widest(10f) <= slotPx -> 10f
    widest(9.5f) <= slotPx -> 9.5f
    else -> 0f
}

/**
 * The action pill's width with its labels showing (spec §8), in px: per button 12 dp padding on both
 * sides, the 15 dp icon, 6 dp and its label ([labelWidths], px); 2 dp between buttons and 2 dp inset.
 * [dp] is px per dp.
 */
internal fun actionPillWidth(labelWidths: List<Int>, dp: Float): Float {
    val buttons = labelWidths.sum() + labelWidths.size * (12f + 15f + 6f + 12f) * dp
    return buttons + (labelWidths.size - 1).coerceAtLeast(0) * 2f * dp + 2f * 2f * dp
}

/** What a touch on the canvas turned into once it moved or a second finger landed. */
internal enum class TouchKind { Undecided, Layer, View, Ignored }

/**
 * What a decided touch moves (spec §6, §8): the layer it started on ([startsOnLayer], asked once),
 * else the view while [zoomed], else nothing. A touch on a layer never also pans the view, and a
 * pinch that starts on empty canvas does nothing unless zoomed.
 */
internal fun touchKind(startsOnLayer: () -> Boolean, zoomed: Boolean): TouchKind = when {
    startsOnLayer() -> TouchKind.Layer
    zoomed -> TouchKind.View
    else -> TouchKind.Ignored
}

/**
 * Tells a tap from a drag or a pinch: a touch stays [TouchKind.Undecided] until its pan passes
 * [slop] px or a second finger lands, then [step]'s `decide` says, once, what it moves.
 */
internal class TouchTracker(private val slop: Float) {
    /** What the touch moves; [TouchKind.Undecided] while it may still be a tap. */
    var kind: TouchKind = TouchKind.Undecided
        private set

    private var fingers = 1
    private var drift = Offset.Zero

    /** Whether the touch, lifting now, was a tap: it never passed the slop, and it had one finger. */
    val isTap: Boolean get() = kind == TouchKind.Undecided && fingers == 1

    /**
     * One pointer event with [pressed] fingers down and their [pan] (box px). Returns the pan to apply
     * now: nothing while undecided, everything that moved so far on the event that decides, so the
     * layer or view catches up with the finger, and the event's own pan after that.
     */
    fun step(pressed: Int, pan: Offset, decide: () -> TouchKind): Offset {
        fingers = maxOf(fingers, pressed)
        if (kind != TouchKind.Undecided) return pan
        drift += pan
        if (pressed <= 1 && drift.getDistance() <= slop) return Offset.Zero
        kind = decide()
        return drift
    }
}

/** A single tap on the canvas: where it landed (box px) and when it lifted (uptime ms). */
internal class CanvasTap(val at: Offset, val upAt: Long)

/**
 * Whether a touch landing at [downAt] (uptime ms) on [at] (box px) is the second tap of a double tap:
 * within [DOUBLE_TAP_MS] of [last] lifting and within [slop] px of where it landed.
 */
internal fun isSecondTap(last: CanvasTap?, downAt: Long, at: Offset, slop: Float): Boolean =
    last != null && downAt - last.upAt in 0..DOUBLE_TAP_MS && (at - last.at).getDistance() <= slop
