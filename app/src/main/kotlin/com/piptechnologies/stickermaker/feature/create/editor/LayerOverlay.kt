package com.piptechnologies.stickermaker.feature.create.editor

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import com.piptechnologies.stickermaker.R
import com.piptechnologies.stickermaker.core.design.LoveIcons
import com.piptechnologies.stickermaker.core.design.Rose
import com.piptechnologies.stickermaker.feature.create.LayerKind
import com.piptechnologies.stickermaker.feature.create.LayerUi
import com.piptechnologies.stickermaker.feature.create.decor.DecorSpec

/** The selection box grows this far beyond the layer on every side; the handles sit on its corners (spec §8). */
internal val BoxOutset = 6.dp
private val HandleTarget = 44.dp
internal val HandleSize = 22.dp
private const val SELECT_FADE_IN_MS = 120
private const val SELECT_FADE_OUT_MS = 100
/** The guide eases in as the drag snaps to the centre (handoff: 90 ms ease-out). */
private const val GUIDE_FADE_MS = 90
private val EaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)
private val Hairline = Color.White.copy(alpha = 0.95f)
/** `0 1 3 rgba(0,0,0,.2)` under the white handles, `.25` under the Rose one. */
private val HandleShadow = Color(0x33000000)
private val TransformShadow = Color(0x40000000)
private val CanvasCentre = Offset(DecorSpec.CANVAS / 2f, DecorSpec.CANVAS / 2f)

/**
 * The selected layer's overlay on the canvas card (spec §8), in the card's own space, where the
 * canvas box sits [CanvasInset] in: the dashed Rose box with a white hairline on both sides, turned
 * with the layer; the delete handle (top-start), the edit handle (top-end, text only) and the
 * resize-and-rotate handle (bottom-end), whose start and end swap in RTL; and the centre guides while
 * a drag snaps to them. The box and handles fade in over 120 ms and out over 100 ms, and follow the
 * sticker's motion preset as it plays. At 1× they may reach into the card's margin, so a handle at
 * the canvas edge stays whole; zoomed in, they are clipped to the canvas box, drawing and touch alike.
 * Handles leave a touch alone while the canvas holds one. Nothing else here takes a touch.
 */
@Composable
internal fun LayerOverlay(
    layer: LayerUi?,
    guideX: Boolean,
    guideY: Boolean,
    viewport: CanvasViewport,
    onDelete: (Long) -> Unit,
    onEdit: (Long) -> Unit,
    onTransformStart: (Long) -> Unit,
    onTransform: (scale: Float, rotation: Float) -> Unit,
    onTransformEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val last = remember { LastLayer() }
    if (layer != null) last.layer = layer
    // While the box fades out, it stays where the layer was, and its handles no longer act.
    val shown = layer ?: last.layer
    val selected = rememberUpdatedState(layer != null)
    Box(
        modifier
            .fillMaxSize()
            // Clips hit tests as well as drawing: no live handle is left in the margin, off-view.
            .graphicsLayer {
                clip = viewport.zoom > 1f
                shape = CanvasBoxShape
            }
    ) {
        CentreGuides(guideX, guideY, viewport)
        AnimatedVisibility(
            visible = layer != null,
            enter = fadeIn(tween(SELECT_FADE_IN_MS)),
            exit = fadeOut(tween(SELECT_FADE_OUT_MS))
        ) {
            if (shown != null) {
                Selection(shown, selected, viewport, onDelete, onEdit, onTransformStart, onTransform, onTransformEnd)
            }
        }
    }
}

/** The layer the overlay last showed. */
private class LastLayer {
    var layer: LayerUi? = null
}

/** The canvas box within the card: [CanvasInset] in from every side, rounded like it. */
private object CanvasBoxShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val inset = with(density) { CanvasInset.toPx() }
        val radius = with(density) { CanvasRadius.toPx() }
        return Outline.Rounded(RoundRect(inset, inset, size.width - inset, size.height - inset, CornerRadius(radius)))
    }
}

/**
 * The box and the handles of [layer], placed by [viewport] on every frame. The handles act only while
 * [selected] (not while the box fades out) and while the canvas holds no touch.
 */
@Composable
private fun Selection(
    layer: LayerUi,
    selected: State<Boolean>,
    viewport: CanvasViewport,
    onDelete: (Long) -> Unit,
    onEdit: (Long) -> Unit,
    onTransformStart: (Long) -> Unit,
    onTransform: (scale: Float, rotation: Float) -> Unit,
    onTransformEnd: () -> Unit
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val density = LocalDensity.current
    val origin = with(density) { Offset(CanvasInset.toPx(), CanvasInset.toPx()) }
    val outset = with(density) { BoxOutset.toPx() }
    val style = remember(density) { SelectionStyle(density) }
    val current = rememberUpdatedState(layer)
    val box = remember(viewport) { derivedStateOf { layerBox(current.value, viewport.frame()) } }
    val corners = remember(viewport, outset, rtl) { derivedStateOf { handleCorners(box.value, outset, rtl) } }
    val deleteNow = rememberUpdatedState(onDelete)
    val editNow = rememberUpdatedState(onEdit)
    val startNow = rememberUpdatedState(onTransformStart)
    val moveNow = rememberUpdatedState(onTransform)
    val endNow = rememberUpdatedState(onTransformEnd)

    // Physical positions: children sit at the top-left and move by absolute offsets, in RTL too.
    Box(Modifier.fillMaxSize(), contentAlignment = AbsoluteAlignment.TopLeft) {
        Canvas(Modifier.fillMaxSize()) {
            if (viewport.edge > 0f) drawSelection(box.value, origin, style)
        }
        Handle(
            centre = { origin + corners.value.delete },
            label = stringResource(R.string.create_action_delete),
            icon = LoveIcons.X,
            iconSize = 12.dp,
            filled = false,
            input = Modifier.handleClick {
                if (selected.value && !viewport.touchHeld) deleteNow.value(current.value.id)
            }
        )
        if (layer.kind == LayerKind.Text) {
            Handle(
                centre = { origin + corners.value.edit },
                label = stringResource(R.string.create_handle_edit),
                icon = LoveIcons.Pencil,
                iconSize = 11.dp,
                filled = false,
                input = Modifier.handleClick {
                    if (selected.value && !viewport.touchHeld) editNow.value(current.value.id)
                }
            )
        }
        Handle(
            centre = { origin + corners.value.transform },
            label = stringResource(R.string.create_handle_transform),
            icon = LoveIcons.Scaling,
            iconSize = 11.dp,
            filled = true,
            input = Modifier.transformGesture(viewport, selected, current, box, corners, startNow, moveNow, endNow)
        )
    }
}

/** A delete or edit handle's tap, with a round ripple over its 44 dp target. */
private fun Modifier.handleClick(onClick: () -> Unit): Modifier = clickable(
    interactionSource = null,
    indication = ripple(bounded = false, radius = HandleTarget / 2),
    role = Role.Button,
    onClick = onClick
)

/**
 * The resize-and-rotate handle's drag (spec §8): one finger scales [layer] by its distance to the
 * layer's centre and turns it by its angle, from the scale and rotation it had when grabbed; one
 * undo step. While the canvas holds a touch, a finger here is left alone, and the other way round.
 */
private fun Modifier.transformGesture(
    viewport: CanvasViewport,
    selected: State<Boolean>,
    layer: State<LayerUi>,
    box: State<LayerBox>,
    corners: State<HandleCorners>,
    onStart: State<(Long) -> Unit>,
    onMove: State<(Float, Float) -> Unit>,
    onEnd: State<() -> Unit>
): Modifier = pointerInput(viewport, corners) {
    val half = HandleTarget.toPx() / 2f
    awaitEachGesture {
        val down = awaitFirstDown()
        if (!selected.value || viewport.touchHeld) return@awaitEachGesture
        try {
            viewport.touchHeld = true
            down.consume()
            val grabbed = layer.value
            val centre = box.value.centre
            // The finger in box px: the handle's centre plus where on the handle it landed.
            val from = corners.value.transform + (down.position - Offset(half, half))
            var finger = from
            onStart.value(grabbed.id)
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                val step = change.positionChangeIgnoreConsumed()
                change.consume()
                if (step != Offset.Zero) {
                    finger += step
                    val next = handleTransform(centre, from, finger, grabbed.scale, grabbed.rotation)
                    onMove.value(next.scale, next.rotation)
                }
            }
        } finally {
            onEnd.value()
            viewport.touchHeld = false
        }
    }
}

/**
 * One handle: a 22 dp circle centred on [centre] (card px, read on every placement) in a 44 dp target.
 * White with a 2 dp Rose ring and a Rose icon, or, [filled], Rose with a white ring and icon.
 */
@Composable
private fun Handle(
    centre: () -> Offset,
    label: String,
    icon: ImageVector,
    iconSize: Dp,
    filled: Boolean,
    input: Modifier
) {
    val half = with(LocalDensity.current) { HandleTarget.toPx() / 2f }
    Box(
        Modifier
            .absoluteOffset { (centre() - Offset(half, half)).round() }
            .size(HandleTarget)
            .then(input)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(HandleSize)
                .shadow(2.dp, CircleShape, spotColor = if (filled) TransformShadow else HandleShadow)
                .background(if (filled) Rose else Color.White, CircleShape)
                .border(2.dp, if (filled) Color.White else Rose, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                tint = if (filled) Color.White else Rose
            )
        }
    }
}

/**
 * The box's three rings, as the prototype's CSS draws them: a 1.5 dp white hairline outside, the 2 dp
 * dashed Rose border (6 on, 4 off) whose outer edge is 6 dp out with radius 6, and a 1.5 dp white
 * hairline inside. Each ring's centre line runs [at] px out from the layer, rounded by the same px.
 */
private class SelectionStyle(density: Density) {
    val outerAt = with(density) { 6.75.dp.toPx() }
    val dashAt = with(density) { 5.dp.toPx() }
    val innerAt = with(density) { 3.25.dp.toPx() }
    val hairline = Stroke(width = with(density) { 1.5.dp.toPx() })
    val dashed = with(density) {
        Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
    }
}

/** The selection box of [box] (box px) at [origin] in the card, turned with the layer. */
private fun DrawScope.drawSelection(box: LayerBox, origin: Offset, style: SelectionStyle) {
    val centre = origin + box.centre
    rotate(box.angle, pivot = centre) {
        ring(centre, box, style.outerAt, style.hairline, Hairline)
        ring(centre, box, style.dashAt, style.dashed, Rose)
        ring(centre, box, style.innerAt, style.hairline, Hairline)
    }
}

/** A rounded rectangle around the upright layer, [at] px out, rounded by [at]. */
private fun DrawScope.ring(centre: Offset, box: LayerBox, at: Float, stroke: Stroke, colour: Color) {
    val halfWidth = box.halfWidth + at
    val halfHeight = box.halfHeight + at
    drawRoundRect(
        color = colour,
        topLeft = Offset(centre.x - halfWidth, centre.y - halfHeight),
        size = Size(halfWidth * 2f, halfHeight * 2f),
        cornerRadius = CornerRadius(at),
        style = stroke
    )
}

/**
 * The centre guides (spec §3): a 1.5 dp dashed Rose line through the canvas centre on each axis the
 * dragged layer snaps to, across the canvas box, with a light haptic tick as the snap engages.
 */
@Composable
private fun CentreGuides(guideX: Boolean, guideY: Boolean, viewport: CanvasViewport) {
    val fade = tween<Float>(GUIDE_FADE_MS, easing = EaseOut)
    val vertical by animateFloatAsState(if (guideX) 1f else 0f, fade, label = "guideX")
    val horizontal by animateFloatAsState(if (guideY) 1f else 0f, fade, label = "guideY")
    val haptics = LocalHapticFeedback.current
    val view = LocalView.current
    val engaged = remember { BooleanArray(2) }
    LaunchedEffect(guideX, guideY) {
        if ((guideX && !engaged[0]) || (guideY && !engaged[1])) {
            // The text-handle tick only exists from API 27; the clock tick is the light one before it.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            } else {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
        }
        engaged[0] = guideX
        engaged[1] = guideY
    }
    val density = LocalDensity.current
    val inset = with(density) { CanvasInset.toPx() }
    val stroke = remember(density) {
        with(density) {
            val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
            Stroke(width = 1.5.dp.toPx(), pathEffect = dash)
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        val edge = viewport.edge
        if (edge <= 0f || (vertical <= 0f && horizontal <= 0f)) return@Canvas
        val frame = viewport.frame()
        val through = Offset(inset, inset) + frame.mapPoint(CanvasCentre)
        clipRect(inset, inset, inset + edge, inset + edge) {
            if (vertical > 0f) guide(through, frame.mapVector(Offset(0f, 1f)), edge, stroke, vertical)
            if (horizontal > 0f) guide(through, frame.mapVector(Offset(1f, 0f)), edge, stroke, horizontal)
        }
    }
}

/** A dashed line through [through] along [direction], long enough to cross a [reach]-px box. */
private fun DrawScope.guide(through: Offset, direction: Offset, reach: Float, stroke: Stroke, alpha: Float) {
    val length = direction.getDistance()
    if (length <= 0f) return
    val half = direction * (2f * reach / length)
    drawLine(
        color = Rose,
        start = through - half,
        end = through + half,
        strokeWidth = stroke.width,
        pathEffect = stroke.pathEffect,
        alpha = alpha
    )
}
