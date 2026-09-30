package com.piptechnologies.stickermaker.feature.create.editor

import android.graphics.Matrix
import android.graphics.Paint
import androidx.annotation.StringRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piptechnologies.stickermaker.R
import com.piptechnologies.stickermaker.core.design.Border
import com.piptechnologies.stickermaker.core.design.Hanken
import com.piptechnologies.stickermaker.core.design.Ink
import com.piptechnologies.stickermaker.core.design.Ink2
import com.piptechnologies.stickermaker.core.design.LoveIcons
import com.piptechnologies.stickermaker.core.design.LoveShapes
import com.piptechnologies.stickermaker.core.design.Mono
import com.piptechnologies.stickermaker.core.design.Rose
import com.piptechnologies.stickermaker.core.design.Subtle
import com.piptechnologies.stickermaker.core.design.Surface
import com.piptechnologies.stickermaker.core.ui.rememberReduceMotion
import com.piptechnologies.stickermaker.feature.create.CreatePackViewModel
import com.piptechnologies.stickermaker.feature.create.CreateSpec
import com.piptechnologies.stickermaker.feature.create.CreateUiState
import com.piptechnologies.stickermaker.feature.create.CutStatus
import com.piptechnologies.stickermaker.feature.create.EditorTool
import com.piptechnologies.stickermaker.feature.create.LayerUi
import com.piptechnologies.stickermaker.feature.create.decor.Affine
import com.piptechnologies.stickermaker.feature.create.decor.MotionMath
import com.piptechnologies.stickermaker.feature.create.decor.MotionPreset
import com.piptechnologies.stickermaker.feature.create.decor.OutlineStyle
import com.piptechnologies.stickermaker.feature.create.decor.SceneRenderer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Test tag of the cut-out canvas, used by the on-device screen tour. */
const val EDITOR_CANVAS_TAG = "editorCanvas"

/** The canvas sits this far inside its card (spec §8); the layer overlay works in the card's space. */
internal val CanvasInset = 27.dp

/** The canvas box's corner radius (spec §8). */
internal val CanvasRadius = 14.dp

/** The Zoom button's view scale (the prototype's 1.6×); a pinch refines it between 1× and [MAX_ZOOM]. */
private const val ZOOM_SCALE = 1.6f
private const val MAX_ZOOM = 4f
/** Layers fade to and from 40% over this long when Brush or Erase starts or ends (spec §6). */
private const val DIM_FADE_MS = 120
/** A change of the outline's switch or colour crossfades over this long (spec §5). */
private const val OUTLINE_FADE_MS = 160
/** A second tap this close to the first makes a double tap. */
private val DoubleTapSlop = 24.dp
/** The action pill keeps this far from the card's sides; where it can't, its labels hide. */
private val PillMargin = 8.dp
private val CheckerGrey = 0xFFE6E9EE.toInt()
private val VeilColour = Color(0xB8FAFBFC)
/** `0 2 6 rgba(20,22,28,.12)` */
private val ZoomShadow = Color(0x1F14161C)
/** `0 6 16 -8 rgba(0,0,0,.5)` */
private val PillShadow = Color(0x80000000)
private val HintText = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W500, fontSize = 11.sp)
private val PillLabel = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W600, fontSize = 11.5.sp)

/**
 * How the canvas box shows the 512 canvas, as snapshot state: the box's side, the zoom and pan, and
 * where the sticker's motion preset is in its loop. A draw, a placement or a gesture that maps
 * through it follows every change. [motion] is the preset the sticker is drawn in (null: still).
 */
@Stable
internal class CanvasViewport(private val motion: State<MotionPreset?>) {
    /** The canvas box's side in px; 0 until it is laid out. */
    var edge by mutableFloatStateOf(0f)

    /** The view's zoom: 1 fits the canvas; the Zoom button starts at 1.6 and a pinch goes up to 4. */
    var zoom by mutableFloatStateOf(1f)
        private set

    /** The view's pan in box px, kept so the zoomed canvas covers the box. */
    var panX by mutableFloatStateOf(0f)
        private set

    /** See [panX]. */
    var panY by mutableFloatStateOf(0f)
        private set

    /** Where the preset's loop is, in ms: 0 at rest. */
    var timeMs by mutableFloatStateOf(0f)

    /** One touch owner at a time: set while the canvas or a handle holds a touch, which the other leaves alone. */
    var touchHeld = false

    // Made again when the box, the zoom or the pan change, not on every frame of a preset.
    private val viewMap = derivedStateOf { viewTransform(edge, zoom, panX, panY) }

    /** Canvas px → box px for what doesn't move with the sticker: the checker and the particles. */
    fun view(): Affine = viewMap.value

    /** Whether the sticker is drawn in a motion pose: it has a preset, playing or at rest. */
    val posed: Boolean get() = motion.value != null

    /** The sticker's motion pose right now: the identity without a preset. */
    fun pose(): Affine = motionPose(motion.value, timeMs)

    /** Canvas px → box px for the sticker as it is drawn right now: the view after the motion pose. */
    fun frame(): Affine = view() * pose()

    /** The canvas point the sticker shows under box point [p]. */
    fun toCanvas(p: Offset): Offset = if (edge <= 0f) Offset.Zero else frame().inverse().mapPoint(p)

    /** A finger's move of [v] box px, in the sticker's canvas px. */
    fun toCanvasMove(v: Offset): Offset = if (edge <= 0f) Offset.Zero else frame().inverse().mapVector(v)

    /** Back to [zoom], centred: the Zoom button, or another sticker. */
    fun reset(zoom: Float) {
        this.zoom = zoom
        panX = 0f
        panY = 0f
    }

    /** A pinch and pan of the zoomed view: [factor] about the box centre, then [pan] box px. */
    fun moveView(factor: Float, pan: Offset) {
        zoom = (zoom * factor).coerceIn(1f, MAX_ZOOM)
        panX = clampPan(panX + pan.x, zoom, edge)
        panY = clampPan(panY + pan.y, zoom, edge)
    }
}

/**
 * The die-cut outline's crossfade on the live canvas (spec §5): the outline the canvas fades [from] and
 * how far the sticker's own is in ([blend]). [show] hears what each sticker shows as the canvas
 * composes; a change that fades ([outlineFadeFrom]) starts at 0, for [play] to run.
 */
@Stable
internal class OutlineFade {
    private var sticker: Int? = null
    private var shown: OutlineStyle? = null
    private var run = 0

    /** The outline fading out; null while none is. */
    var from: OutlineStyle? = null
        private set

    /** How far the sticker's outline is in: 0 as a fade starts, 1 once it is over. Snapshot state. */
    var blend by mutableFloatStateOf(1f)
        private set

    /**
     * Sticker number [sticker] shows [outline] now. Returns true when that starts a fade: its switch or
     * colour changed, and nothing asks to [snap] (reduced motion, no cut-out to draw yet). Another
     * sticker, another thickness or a snap shows the outline at once and ends a fade in flight.
     */
    fun show(sticker: Int, outline: OutlineStyle, snap: Boolean): Boolean {
        val previous = shown.takeIf { this.sticker == sticker }
        this.sticker = sticker
        shown = outline
        if (previous == outline) return false
        run++
        from = if (snap) null else outlineFadeFrom(previous, outline)
        blend = if (from == null) 1f else 0f
        return from != null
    }

    /** Plays the fade [show] started last, over 160 ms; a change shown meanwhile takes over. */
    suspend fun play() {
        val mine = run
        animate(0f, 1f, animationSpec = tween(OUTLINE_FADE_MS, easing = LinearEasing)) { value, _ ->
            if (mine == run) blend = value
        }
        if (mine == run) from = null
    }
}

// ------------------------------------------------------------------- card

/**
 * The Cut out canvas card (spec §6, §8): the live canvas with the checker, the sticker's scene (its
 * motion preset played by a frame clock) and every canvas gesture; the Zoom button; the hint line,
 * which becomes the action pill while a layer is selected; and over them, the selected layer's box
 * and handles. While the cut-out runs, the canvas shows the raw picture under a veil. The Zoom
 * button and the pill leave a finger alone while the canvas or a handle holds a touch.
 */
@Composable
internal fun EditorCanvasCard(state: CreateUiState, viewModel: CreatePackViewModel, modifier: Modifier = Modifier) {
    val reduceMotion = rememberReduceMotion()
    val cutDone = state.activeCut == CutStatus.Done
    val painting = state.tool == EditorTool.Brush || state.tool == EditorTool.Erase
    val strokeTool = painting || state.tool == EditorTool.Draw
    val data = viewModel.data
    val motion = remember(data, state.preset, cutDone) {
        data?.motion?.byId(state.preset)?.takeIf { cutDone && !it.isNone }
    }
    val motionState = rememberUpdatedState(motion)
    val viewport = remember { CanvasViewport(motionState) }
    // Brush, Erase and Draw hold the sticker still in its rest pose, so a stroke lands under the finger.
    val animating = motion != null && !strokeTool && (!reduceMotion || state.playing)
    val holding = rememberUpdatedState(state.liveLayerId != null)

    LaunchedEffect(state.zoomed, state.activeIndex) {
        viewport.reset(if (state.zoomed) ZOOM_SCALE else 1f)
    }
    LaunchedEffect(motion, state.activeIndex, state.playing, animating) {
        viewport.timeMs = 0f
        if (motion != null && animating) playMotion(motion, oneLoop = reduceMotion, holding, viewport)
        // One loop played (reduced motion), or a tap-to-play with nothing to play here.
        if (state.playing) viewModel.stopPlaying()
    }

    val layerOpacity = animateFloatAsState(
        targetValue = if (painting) SceneRenderer.DIM_OPACITY else 1f,
        animationSpec = tween(DIM_FADE_MS),
        label = "layerDim"
    )
    val selected = state.layers.firstOrNull { it.id == state.selectedLayerId }?.takeIf { cutDone && !strokeTool }

    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(20.dp))
            .background(Surface)
            .border(1.dp, Border, RoundedCornerShape(20.dp))
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(CanvasInset)
        ) {
            LiveCanvas(
                state = state,
                viewModel = viewModel,
                viewport = viewport,
                particles = motion?.takeIf { animating },
                layerOpacity = { layerOpacity.value },
                reduceMotion = reduceMotion
            )
        }
        ZoomButton(
            zoomed = state.zoomed,
            onClick = viewModel::toggleZoom,
            locked = { viewport.touchHeld },
            // The 44 dp target puts the 36 dp circle 10 dp from the top and the inline end.
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 6.dp, end = 6.dp)
        )
        if (selected == null) {
            Text(
                stringResource(hintOf(state)),
                style = HintText,
                color = Ink2,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
            )
        } else {
            ActionPill(
                layer = selected,
                onDuplicate = viewModel::duplicateSelected,
                onFlip = viewModel::flipSelected,
                onBehind = viewModel::toggleBehindSelected,
                onDelete = { viewModel.deleteLayer(selected.id) },
                locked = { viewport.touchHeld },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
        // Last, so a handle that shows over the pill or the Zoom button is the one a finger gets there.
        LayerOverlay(
            layer = selected,
            guideX = state.guideX && cutDone,
            guideY = state.guideY && cutDone,
            viewport = viewport,
            onDelete = viewModel::deleteLayer,
            onEdit = viewModel::editTextLayer,
            onTransformStart = viewModel::beginHandleGesture,
            onTransform = viewModel::handleGesture,
            onTransformEnd = viewModel::endLayerGesture
        )
    }
}

/** The hint under the canvas (spec §8): the zoomed text while zoomed, else the tool's own. */
@StringRes
private fun hintOf(state: CreateUiState): Int = when {
    state.zoomed -> R.string.create_hint_zoomed
    else -> when (state.tool) {
        EditorTool.Auto ->
            if (state.activeCut == CutStatus.Done) R.string.create_hint_auto_done else R.string.create_hint_auto
        EditorTool.Brush -> R.string.create_hint_brush
        EditorTool.Erase -> R.string.create_hint_erase
        EditorTool.Add -> R.string.create_hint_add
        EditorTool.Draw -> R.string.create_hint_draw
        EditorTool.Animate ->
            if (state.activeIsVideo) R.string.create_hint_animate_clip else R.string.create_hint_animate
    }
}

/**
 * Plays [preset] on the frame clock into [viewport]'s time: looping, or once when [oneLoop] (reduced
 * motion), then back at rest. While [holding] (a finger moves a layer) the loop holds its frame, so
 * the layer stays under the finger, and goes on from there.
 */
private suspend fun playMotion(
    preset: MotionPreset,
    oneLoop: Boolean,
    holding: State<Boolean>,
    viewport: CanvasViewport
) {
    val loop = preset.durationMs.toFloat()
    if (loop <= 0f) return
    var elapsed = 0f
    var last = withFrameMillis { it }
    while (true) {
        if (holding.value) {
            snapshotFlow { holding.value }.first { !it }
            last = withFrameMillis { it }
        }
        val now = withFrameMillis { it }
        elapsed += (now - last).toFloat()
        last = now
        if (oneLoop && elapsed >= loop) {
            viewport.timeMs = 0f
            return
        }
        if (!oneLoop) elapsed %= loop
        viewport.timeMs = elapsed
    }
}

// ------------------------------------------------------------------ canvas

/**
 * The canvas box: the checker and the sticker's scene in [viewport]'s view, in its motion pose, with
 * the layers at [layerOpacity] and, while a preset plays, its [particles] over it; the raw picture
 * while the cut-out runs, under the veil. The clip length sits in its corner. A change of the
 * outline's switch or colour crossfades over 160 ms, and snaps with [reduceMotion].
 */
@Composable
private fun LiveCanvas(
    state: CreateUiState,
    viewModel: CreatePackViewModel,
    viewport: CanvasViewport,
    particles: MotionPreset?,
    layerOpacity: () -> Float,
    reduceMotion: Boolean
) {
    val bitmapPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG) }
    val checkerPaint = remember { Paint() }
    val viewMatrix = remember { Matrix() }
    val poseMatrix = remember { Matrix() }
    val matrixValues = remember { FloatArray(9) }
    val focusManager = LocalFocusManager.current
    val cutDone = state.activeCut == CutStatus.Done
    val editorTick = state.editorTick
    val liveLayerId = state.liveLayerId
    val outlineFade = remember { OutlineFade() }
    val fadeScope = rememberCoroutineScope()
    val sticker = state.activeIndex
    val outline = state.outline
    val snapOutline = reduceMotion || !cutDone
    // After the composition, before its draw: the first frame of a change already shows the old outline.
    SideEffect {
        if (outlineFade.show(sticker, outline, snapOutline)) fadeScope.launch { outlineFade.play() }
    }
    Box(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(CanvasRadius))
            .background(Subtle)
            .testTag(EDITOR_CANVAS_TAG)
            .onSizeChanged { viewport.edge = it.width.toFloat() }
            .canvasGestures(state.tool, state.zoomed, state.activeIndex, viewModel, viewport, focusManager)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // editorTick invalidates this draw whenever something the scene shows changed.
            @Suppress("UNUSED_EXPRESSION") editorTick
            val scene = if (cutDone) viewModel.activeScene() else null
            val renderer = viewModel.renderer
            drawIntoCanvas { canvas ->
                val nc = canvas.nativeCanvas
                nc.save()
                // The same maps the gestures and the overlay use, so what is drawn is what is touched.
                viewMatrix.setValues(viewport.view().writeMatrix(matrixValues))
                nc.concat(viewMatrix)
                if (scene != null && renderer != null) {
                    drawChecker(nc, checkerPaint)
                    nc.save()
                    if (viewport.posed) {
                        poseMatrix.setValues(viewport.pose().writeMatrix(matrixValues))
                        nc.concat(poseMatrix)
                    }
                    renderer.drawSticker(
                        nc, scene,
                        liveLayerId = liveLayerId,
                        layerOpacity = layerOpacity(),
                        outlineFrom = outlineFade.from,
                        outlineBlend = outlineFade.blend
                    )
                    nc.restore()
                    particles?.let { renderer.drawParticles(nc, MotionMath.particles(it, viewport.timeMs)) }
                } else {
                    viewModel.activeSource()?.let { nc.drawBitmap(it, 0f, 0f, bitmapPaint) }
                }
                nc.restore()
            }
        }
        state.activeDurationLabel?.let { label ->
            Text(
                label,
                style = TextStyle(fontFamily = Mono, fontWeight = FontWeight.W500, fontSize = 9.5.sp),
                color = Ink2,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 10.dp, top = 8.dp)
            )
        }
        if (state.activeCut == CutStatus.Pending) {
            Column(
                Modifier
                    .fillMaxSize()
                    .background(VeilColour),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Rose, strokeWidth = 2.dp)
                Text(
                    stringResource(R.string.create_cutting_out_on_phone),
                    style = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W600, fontSize = 12.5.sp),
                    color = Ink2,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
        }
    }
}

/** 512-space transparency checker behind the cut subject. */
private fun drawChecker(canvas: android.graphics.Canvas, paint: Paint) {
    val cell = 32f
    val cells = CreateSpec.CANVAS_SIZE / cell.toInt()
    paint.color = android.graphics.Color.WHITE
    canvas.drawRect(0f, 0f, CreateSpec.CANVAS_SIZE.toFloat(), CreateSpec.CANVAS_SIZE.toFloat(), paint)
    paint.color = CheckerGrey
    for (y in 0 until cells) {
        for (x in 0 until cells) {
            if ((x + y) % 2 == 0) {
                canvas.drawRect(x * cell, y * cell, (x + 1) * cell, (y + 1) * cell, paint)
            }
        }
    }
}

// ---------------------------------------------------------------- gestures

/**
 * Every touch on the canvas (spec §6, §8). Brush and Erase paint the mask with one finger or, while
 * zoomed, pan and pinch the view and dab on a tap. Draw draws with one finger; while zoomed, a second
 * finger ends the stroke (or drops it, if it hadn't moved yet) and the fingers pan and pinch the view.
 * In Auto, Add and Animate, a drag that starts on a layer, or a pinch and twist with a finger on one,
 * moves that layer (one undo step); otherwise the touch pans and pinches the view while zoomed and does
 * nothing else; a tap selects, deselects or plays (and closes the keyboard), and a double tap on a text
 * layer edits it.
 */
private fun Modifier.canvasGestures(
    tool: EditorTool,
    zoomed: Boolean,
    activeIndex: Int,
    viewModel: CreatePackViewModel,
    viewport: CanvasViewport,
    focusManager: FocusManager
): Modifier = pointerInput(tool, zoomed, activeIndex) {
    val tapSlop = DoubleTapSlop.toPx()
    var lastTap: CanvasTap? = null
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        // A handle is being dragged: this finger is left alone.
        if (viewport.touchHeld) return@awaitEachGesture
        try {
            viewport.touchHeld = true
            when (tool) {
                EditorTool.Brush, EditorTool.Erase -> if (zoomed) {
                    val at = viewport.toCanvas(down.position)
                    if (moveOrTap(down, at, layers = false, zoomed = true, viewport, viewModel) != null) {
                        viewModel.tapStroke(at.x, at.y)
                    }
                } else {
                    stroke(down, viewport, viewModel::beginStroke, viewModel::extendStroke, viewModel::endStroke)
                }
                EditorTool.Draw -> stroke(
                    down, viewport, viewModel::beginMarker, viewModel::extendMarker, viewModel::endMarker,
                    yieldToView = zoomed, discard = viewModel::cancelMarker
                )
                EditorTool.Auto, EditorTool.Add, EditorTool.Animate -> {
                    val at = viewport.toCanvas(down.position)
                    val lift = moveOrTap(down, at, layers = true, zoomed, viewport, viewModel)
                    if (lift != null) {
                        focusManager.clearFocus()
                        if (isSecondTap(lastTap, down.uptimeMillis, down.position, tapSlop)) {
                            lastTap = null
                            viewModel.canvasDoubleTap(at.x, at.y)
                        } else {
                            lastTap = CanvasTap(down.position, lift.uptimeMillis)
                            viewModel.canvasTap(at.x, at.y)
                        }
                    }
                }
            }
        } finally {
            viewport.touchHeld = false
        }
    }
}

/**
 * A touch that stays a tap until it moves past the touch slop or a second finger lands. Then it moves
 * the layer under the first finger's landing point [at] (canvas px) or, for a pinch, under another
 * finger, when [layers] allows; else the view when [zoomed]; else nothing (the page may scroll:
 * nothing here consumes it). A layer never also pans the view. Returns the finger's lift when the
 * touch stayed a one-finger tap, else null; a cancelled touch, whose lift comes consumed, is no tap.
 */
private suspend fun AwaitPointerEventScope.moveOrTap(
    down: PointerInputChange,
    at: Offset,
    layers: Boolean,
    zoomed: Boolean,
    viewport: CanvasViewport,
    viewModel: CreatePackViewModel
): PointerInputChange? {
    val touch = TouchTracker(viewConfiguration.touchSlop)
    try {
        while (true) {
            val event = awaitPointerEvent()
            val pressed = event.changes.count { it.pressed }
            if (pressed == 0) {
                return event.changes.firstOrNull { it.id == down.id }?.takeIf { touch.isTap && !it.isConsumed }
            }
            val pan = touch.step(pressed, event.calculatePan()) {
                val fingers = listOf(at) + otherFingers(event, down).map(viewport::toCanvas)
                touchKind(fingers, startsLayerAt = { p -> layers && viewModel.beginLayerGesture(p.x, p.y) }, zoomed)
            }
            if (touch.kind == TouchKind.Undecided) continue
            // On the deciding event a new finger isn't in the zoom or the twist yet: they start at 1 and 0.
            move(touch.kind, pan, event.calculateZoom(), event.calculateRotation(), viewport, viewModel)
            if (touch.kind != TouchKind.Ignored) event.changes.forEach { it.consume() }
        }
    } finally {
        if (touch.kind == TouchKind.Layer) viewModel.endLayerGesture()
    }
}

/** Where the fingers other than [down]'s are (box px), the ones that just landed first. */
private fun otherFingers(event: PointerEvent, down: PointerInputChange): List<Offset> =
    event.changes.filter { it.pressed && it.id != down.id }.sortedBy { it.previousPressed }.map { it.position }

/**
 * One step of a decided touch, one call each: the layer drags by [pan] and pinches and twists; or the
 * view pans and zooms.
 */
private fun move(
    kind: TouchKind,
    pan: Offset,
    zoom: Float,
    rotation: Float,
    viewport: CanvasViewport,
    viewModel: CreatePackViewModel
) {
    when (kind) {
        TouchKind.Layer -> {
            val step = viewport.toCanvasMove(pan)
            if (step != Offset.Zero || zoom != 1f || rotation != 0f) {
                viewModel.layerTransform(step.x, step.y, zoom, rotation)
            }
        }
        TouchKind.View -> if (pan != Offset.Zero || zoom != 1f) viewport.moveView(zoom, pan)
        TouchKind.Undecided, TouchKind.Ignored -> Unit
    }
}

/**
 * One finger drawing on the canvas: [begin] where it lands (a tap is a dab or a dot), [extend] as it
 * moves, [end] when it lifts or the gesture is dropped. Other fingers are ignored, unless
 * [yieldToView] (Draw while zoomed, spec §6): then a second finger ends the stroke where it is, and
 * the fingers pan and pinch the view until they all lift. A stroke that never left the touch slop
 * around its first point by then was the first finger of that pinch, not a dot: [discard] drops it.
 */
private suspend fun AwaitPointerEventScope.stroke(
    down: PointerInputChange,
    viewport: CanvasViewport,
    begin: (Float, Float) -> Unit,
    extend: (Float, Float) -> Unit,
    end: () -> Unit,
    yieldToView: Boolean = false,
    discard: () -> Unit = end
) {
    val start = viewport.toCanvas(down.position)
    begin(start.x, start.y)
    val slop = viewConfiguration.touchSlop
    var drawing = true
    var travelled = false
    try {
        while (true) {
            val event = awaitPointerEvent()
            event.changes.forEach { it.consume() }
            if (!drawing) {
                if (event.changes.none { it.pressed }) break
                viewport.moveView(event.calculateZoom(), event.calculatePan())
                continue
            }
            if (yieldToView && event.changes.count { it.pressed } > 1) {
                drawing = false
                if (travelled) end() else discard()
                continue
            }
            val finger = event.changes.firstOrNull { it.id == down.id }
            if (finger == null || !finger.pressed) break
            if (finger.positionChangeIgnoreConsumed() != Offset.Zero) {
                if (!travelled && (finger.position - down.position).getDistance() > slop) travelled = true
                val p = viewport.toCanvas(finger.position)
                extend(p.x, p.y)
            }
        }
    } finally {
        if (drawing) end()
    }
}

// ---------------------------------------------------------------- controls

/**
 * The Zoom button (spec §8): a 36 dp circle in a 44 dp target; white with an Ink zoom-in, Rose with
 * a white zoom-out while zoomed. A tap while [locked] (another touch is moving something) is ignored.
 */
@Composable
private fun ZoomButton(zoomed: Boolean, onClick: () -> Unit, locked: () -> Boolean, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.create_tool_zoom)
    Box(
        modifier
            .size(44.dp)
            .clickable(
                interactionSource = null,
                indication = ripple(bounded = false, radius = 22.dp),
                role = Role.Button,
                onClick = { if (!locked()) onClick() }
            )
            .semantics {
                contentDescription = label
                selected = zoomed
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(36.dp)
                .shadow(3.dp, CircleShape, spotColor = ZoomShadow)
                .background(if (zoomed) Rose else Surface, CircleShape)
                .border(1.dp, if (zoomed) Rose else Border, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (zoomed) LoveIcons.ZoomOut else LoveIcons.ZoomIn,
                contentDescription = null,
                modifier = Modifier.size(17.dp),
                tint = if (zoomed) Color.White else Ink
            )
        }
    }
}

/** One action of the pill. */
private class PillAction(val icon: ImageVector, val label: String, val onClick: () -> Unit)

/**
 * The action pill that replaces the hint while a layer is selected (spec §8): Duplicate · Flip ·
 * Behind (In front once behind) · Delete. Where the labels would push it past the card's sides, the
 * buttons first narrow their padding to 8 dp, then show their icons only and keep the labels for
 * TalkBack. A tap while [locked] (another touch is moving something) is ignored.
 */
@Composable
private fun ActionPill(
    layer: LayerUi,
    onDuplicate: () -> Unit,
    onFlip: () -> Unit,
    onBehind: () -> Unit,
    onDelete: () -> Unit,
    locked: () -> Boolean,
    modifier: Modifier = Modifier
) {
    val actions = listOf(
        PillAction(LoveIcons.Copy, stringResource(R.string.create_action_duplicate), onDuplicate),
        PillAction(LoveIcons.FlipHorizontal, stringResource(R.string.create_action_flip), onFlip),
        if (layer.behind) {
            PillAction(LoveIcons.BringToFront, stringResource(R.string.create_action_in_front), onBehind)
        } else {
            PillAction(LoveIcons.SendToBack, stringResource(R.string.create_action_behind), onBehind)
        },
        PillAction(LoveIcons.Trash2, stringResource(R.string.create_action_delete), onDelete)
    )
    val labels = actions.map { it.label }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
        val room = with(density) { (maxWidth - PillMargin * 2).toPx() }
        val fit = remember(labels, room, measurer) {
            val widths = labels.map { measurer.measure(it, PillLabel, maxLines = 1, softWrap = false).size.width }
            actionPillFit(widths, density.density, room)
        }
        Row(
            Modifier
                .padding(bottom = 6.dp)
                .shadow(8.dp, LoveShapes.Pill, ambientColor = Color.Transparent, spotColor = PillShadow)
                .background(Ink, LoveShapes.Pill)
                .padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            actions.forEach { PillButton(it, fit, locked) }
        }
    }
}

/** 36 dp high, [fit]'s side padding: a 15 dp icon and, when [fit] has labels, the 11.5 sp label 6 dp after it. */
@Composable
private fun PillButton(action: PillAction, fit: PillFit, locked: () -> Boolean) {
    Row(
        Modifier
            .height(36.dp)
            .clip(LoveShapes.Pill)
            .clickable(role = Role.Button) { if (!locked()) action.onClick() }
            .padding(horizontal = fit.sidePadding.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            action.icon,
            contentDescription = if (fit.labels) null else action.label,
            modifier = Modifier.size(15.dp),
            tint = Color.White
        )
        if (fit.labels) {
            Spacer(Modifier.width(6.dp))
            Text(action.label, style = PillLabel, color = Color.White, maxLines = 1, softWrap = false)
        }
    }
}
