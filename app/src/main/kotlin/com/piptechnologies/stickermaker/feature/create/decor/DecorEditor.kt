package com.piptechnologies.stickermaker.feature.create.decor

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** What an undo did: nothing, a mask stroke the caller must remove, or a decor change. */
enum class UndoResult { Nothing, MaskStroke, Changed }

/**
 * Edits one sticker's decor (spec §3-§4): layers, gestures, text sessions, live
 * Draw strokes, outline, preset and the undo stack. [sizeOf] gives a content's base
 * size at scale 1; [subjectBox] is the cut-out's bounding box (null before the cut).
 */
class DecorEditor(
    private val sizeOf: (LayerContent) -> Size2,
    private val subjectBox: () -> Box?,
    private val newId: () -> Long
) {
    var state: DecorState = DecorState(); private set
    var selectedId: Long? = null; private set
    var liveStrokes: List<MarkerStroke> = emptyList(); private set
    var guides: Guides = Guides.NONE; private set
    var textDefaults: TextDefaults = TextDefaults(); private set
    private val history = EditHistory()
    val canUndo: Boolean get() = !history.isEmpty

    private var gesture: Gesture? = null
    private var textSession: Long? = null
    private var textSessionPushed = false
    private var strokeDown = false

    private class Gesture(val id: Long, var rawX: Float, var rawY: Float, var rawScale: Float, var rawRotation: Float, val startScale: Float)

    // ---------------------------------------------------------------- queries

    fun baseSize(layer: Layer): Size2 = sizeOf(layer.content)
    /** Rendered size at the layer's scale. */
    fun renderedSize(layer: Layer): Size2 = baseSize(layer).let { Size2(it.w * layer.scale, it.h * layer.scale) }

    /** Top-most layer under a point: front layers first, then behind ones. */
    fun hitTest(x: Float, y: Float): Long? = hits(x, y).firstOrNull()

    /** Every layer under a point, top-most first: the front layers, then the ones behind the subject. */
    private fun hits(x: Float, y: Float): List<Long> {
        val front = state.layers.filter { !it.behind }.asReversed()
        val back = state.layers.filter { it.behind }.asReversed()
        return (front + back).filter { contains(it, x, y, DecorSpec.HIT_SLOP_PX) }.map { it.id }
    }

    /**
     * The layer a drag or pinch starting at a point moves: the selected layer when the point is inside
     * its box, even under another layer (a frame-like piece over it), else the top-most layer there.
     */
    fun gestureTarget(x: Float, y: Float): Long? {
        val hits = hits(x, y)
        return selectedId?.takeIf { it in hits } ?: hits.firstOrNull()
    }

    /**
     * The layer a tap at a point selects: with the selected layer under the point among others, the
     * next one down the stack (wrapping to the top-most), so every layer under a frame-like piece can
     * be reached; the selected layer alone there stays; else the top-most layer there, or nothing.
     */
    fun tapTarget(x: Float, y: Float): Long? {
        val hits = hits(x, y)
        val at = hits.indexOf(selectedId)
        return if (at < 0) hits.firstOrNull() else hits[(at + 1) % hits.size]
    }

    /**
     * The text layer a double tap at a point edits: the selected layer when it is text and under the
     * point, else the top-most text layer there, or nothing.
     */
    fun doubleTapTarget(x: Float, y: Float): Long? {
        val hits = hits(x, y)
        val text = hits.filter { state.layer(it)?.content is LayerContent.Text }
        return selectedId?.takeIf { it in text } ?: text.firstOrNull()
    }

    private fun contains(layer: Layer, x: Float, y: Float, slop: Float): Boolean {
        val s = renderedSize(layer)
        val r = Math.toRadians(-layer.rotation.toDouble())
        val dx = x - layer.cx
        val dy = y - layer.cy
        val lx = (dx * cos(r) - dy * sin(r)).toFloat()
        val ly = (dx * sin(r) + dy * cos(r)).toFloat()
        return abs(lx) <= s.w / 2f + slop && abs(ly) <= s.h / 2f + slop
    }

    // ------------------------------------------------------------- selection

    fun select(id: Long?) {
        if (id != textSession) endTextSession()
        selectedId = id?.takeIf { state.layer(it) != null }
    }

    // ---------------------------------------------------------------- adding

    /** Adds a layer at its spec position and selects it; null when 8 layers exist (an emptied caption goes first). */
    fun add(content: LayerContent, headTop: Boolean = false): Long? {
        endTextSession()
        if (state.layers.size >= DecorSpec.MAX_LAYERS) return null
        val before = state
        val id = place(content, headTop)
        history.push(EditStep.Decor(before))
        return id
    }

    private fun place(content: LayerContent, headTop: Boolean, at: Pt? = null): Long {
        val size = sizeOf(content)
        val c = DecorSpec.CANVAS
        val (cx, cy) = when {
            at != null -> at.x to at.y
            content is LayerContent.Text && state.layers.none { it.content is LayerContent.Text } -> c / 2f to DecorSpec.FIRST_TEXT_Y * c
            headTop -> subjectBox()?.let { box -> box.cx to max(box.top - size.h / 4f, size.h / 2f) } ?: (c / 2f to c / 2f)
            else -> c / 2f to c / 2f
        }
        val id = newId()
        val layer = Layer(id, content, cx.coerceIn(0f, c), cy.coerceIn(0f, c))
        val initialScale = minOf(1f, maxScale(layer))
        state = state.copy(layers = state.layers + layer.copy(scale = initialScale))
        selectedId = id
        return id
    }

    // ------------------------------------------------------------ layer edits

    /** A copy of layer [id], offset and selected; null when it is gone or 8 layers exist (an emptied caption went). */
    fun duplicate(id: Long): Long? {
        endTextSession()
        val src = state.layer(id) ?: return null
        if (state.layers.size >= DecorSpec.MAX_LAYERS) return null
        val before = state
        val off = DecorSpec.DUPLICATE_OFFSET * DecorSpec.CANVAS
        val copy = src.copy(id = newId(), cx = (src.cx + off).coerceIn(0f, DecorSpec.CANVAS), cy = (src.cy + off).coerceIn(0f, DecorSpec.CANVAS))
        state = state.copy(layers = state.layers + copy)
        selectedId = copy.id
        history.push(EditStep.Decor(before))
        return copy.id
    }

    fun flip(id: Long) = change(id) { it.copy(flipped = !it.flipped) }
    fun toggleBehind(id: Long) = change(id) { it.copy(behind = !it.behind) }

    /** Removes layer [id]; an emptied caption goes with the text session first, and then there is nothing to record. */
    fun delete(id: Long) {
        endTextSession()
        if (state.layer(id) == null) return
        history.push(EditStep.Decor(state))
        state = state.copy(layers = state.layers.filterNot { it.id == id })
        if (selectedId == id) selectedId = null
        if (gesture?.id == id) gesture = null
    }

    private fun change(id: Long, f: (Layer) -> Layer) {
        endTextSession()
        if (state.layer(id) == null) return
        history.push(EditStep.Decor(state))
        update(id, f)
    }

    private fun update(id: Long, f: (Layer) -> Layer) {
        state = state.copy(layers = state.layers.map { if (it.id == id) f(it) else it })
    }

    // -------------------------------------------------------------- gestures

    /** A drag, pinch or handle gesture starts on [id]: selects it and records one undo step. */
    fun beginGesture(id: Long) {
        val l = state.layer(id) ?: return
        if (id != textSession) endTextSession()
        selectedId = id
        history.push(EditStep.Decor(state))
        gesture = Gesture(id, l.cx, l.cy, l.scale, l.rotation, l.scale)
    }

    /** Moves by a canvas-px delta, with centre snapping and the quarter-inside clamp. Non-finite deltas are ignored. */
    fun drag(id: Long, dx: Float, dy: Float) {
        if (!dx.isFinite() || !dy.isFinite()) return
        val g = gesture?.takeIf { it.id == id } ?: return
        g.rawX += dx
        g.rawY += dy
        val c = DecorSpec.CANVAS / 2f
        val snapX = abs(g.rawX - c) < DecorSpec.SNAP_PX
        val snapY = abs(g.rawY - c) < DecorSpec.SNAP_PX
        guides = Guides(snapX, snapY)
        update(id) { it.copy(cx = (if (snapX) c else g.rawX).coerceIn(0f, DecorSpec.CANVAS), cy = (if (snapY) c else g.rawY).coerceIn(0f, DecorSpec.CANVAS)) }
    }

    /** Multiplies the scale and adds rotation (two-finger pinch and twist). Non-finite input is ignored. */
    fun pinch(id: Long, zoom: Float, rotationDeg: Float) {
        if (!zoom.isFinite() || !rotationDeg.isFinite()) return
        val g = gesture?.takeIf { it.id == id } ?: return
        val layer = state.layer(id) ?: return
        g.rawScale *= zoom
        g.rawRotation += rotationDeg
        val newScale = g.rawScale.coerceIn(minOf(minScale(layer), g.startScale), maxScale(layer))
        update(id) { it.copy(scale = newScale, rotation = snapRotation(g.rawRotation)) }
        g.rawScale = newScale
    }

    /**
     * Absolute scale and rotation from the corner handle; [start] records the undo step. A
     * non-finite scale or rotation changes nothing (the gesture still starts, so the next
     * finite move is recorded as usual).
     */
    fun setScaleRotation(id: Long, scale: Float, rotation: Float, start: Boolean = false) {
        if (start) beginGesture(id)
        if (!scale.isFinite() || !rotation.isFinite()) return
        val layer = state.layer(id) ?: return
        val g = gesture?.takeIf { it.id == id }
        val startScale = g?.startScale ?: layer.scale
        val newScale = scale.coerceIn(minOf(minScale(layer), startScale), maxScale(layer))
        update(id) { it.copy(scale = newScale, rotation = snapRotation(rotation)) }
    }

    fun endGesture() {
        if (gesture != null) {
            history.dropIfUnchanged(state)
        }
        gesture = null
        guides = Guides.NONE
    }

    /**
     * The largest scale: the layer's longer side stays within the canvas (half of it for emoji, whose
     * art is 256 px), so a thin upright piece can't grow into a bitmap many canvases tall. A drawing
     * is never capped below the size it was drawn at: its ink may reach a little past the canvas edge.
     */
    private fun maxScale(layer: Layer): Float {
        val maxSide = if (layer.content is LayerContent.Emoji) DecorSpec.EMOJI_MAX_SIDE else 1f
        val cap = maxSide * DecorSpec.CANVAS / longerSide(layer)
        return if (layer.content is LayerContent.Drawing) max(1f, cap) else cap
    }

    /**
     * The smallest scale a gesture reaches: the layer's longer side stays at 10% of the canvas or more.
     * A layer that arrived smaller than that is not forced up to it.
     */
    private fun minScale(layer: Layer): Float = DecorSpec.MIN_SIDE * DecorSpec.CANVAS / longerSide(layer)

    /** The longer side of the layer's base size, in canvas px (at least 1). */
    private fun longerSide(layer: Layer): Float = baseSize(layer).let { max(it.w, it.h) }.coerceAtLeast(1f)

    private fun snapRotation(deg: Float): Float {
        val n = ((deg % 360f) + 540f) % 360f - 180f
        return if (abs(n) < DecorSpec.ROTATION_SNAP_DEG) 0f else n
    }

    // ------------------------------------------------------------------ text

    /**
     * Edits the selected text layer, or creates one on the first character; an edit past the 30 characters
     * is refused whole, so the text and its caret stay as they were. False when 8 layers exist.
     */
    fun setText(text: String): Boolean {
        if (text.length > DecorSpec.TEXT_MAX_CHARS) return true
        val t = dropHighSurrogateAtEnd(text)
        val sel = selectedText()
        if (sel != null) {
            beginTextSession(sel.id)
            val updatedLayer = sel.copy(content = (sel.content as LayerContent.Text).copy(text = t))
            update(sel.id) { it.copy(content = updatedLayer.content, scale = minOf(it.scale, maxScale(updatedLayer))) }
            return true
        }
        if (t.isEmpty()) return true
        if (state.layers.size >= DecorSpec.MAX_LAYERS) return false
        val before = state
        val id = place(LayerContent.Text(t, textDefaults.style, textDefaults.colour, textDefaults.font), headTop = false)
        history.push(EditStep.Decor(before))
        textSession = id
        textSessionPushed = true
        return true
    }

    fun setTextStyle(style: TextStyleId) = styleText({ it.copy(style = style) }) { textDefaults = textDefaults.copy(style = style) }
    fun setTextColour(colour: Int) = styleText({ it.copy(colour = colour) }) { textDefaults = textDefaults.copy(colour = colour) }
    fun setTextFont(font: FontMood) = styleText({ it.copy(font = font) }) { textDefaults = textDefaults.copy(font = font) }

    private fun styleText(f: (LayerContent.Text) -> LayerContent.Text, remember: () -> Unit) {
        remember()
        val sel = selectedText() ?: return
        beginTextSession(sel.id)
        val updatedLayer = sel.copy(content = f(sel.content as LayerContent.Text))
        update(sel.id) { it.copy(content = updatedLayer.content, scale = minOf(it.scale, maxScale(updatedLayer))) }
    }

    /** Opens a text layer for editing (edit handle or double tap). */
    fun editText(id: Long) {
        if (state.layer(id)?.content !is LayerContent.Text) return
        if (id != textSession) endTextSession()
        selectedId = id
    }

    /** Ends coalescing and removes empty text layers (the sheet closed or another layer was chosen). */
    fun endTextSession() {
        textSession = null
        val empty = state.layers.filter { (it.content as? LayerContent.Text)?.text?.isBlank() == true }.map { it.id }
        if (empty.isNotEmpty()) {
            state = state.copy(layers = state.layers.filterNot { it.id in empty })
            if (selectedId in empty) selectedId = null
        }
        if (textSessionPushed) {
            history.dropIfUnchanged(state)
            textSessionPushed = false
        }
    }

    private fun beginTextSession(id: Long) {
        if (textSession != id) {
            history.push(EditStep.Decor(state))
            textSession = id
            textSessionPushed = true
        }
    }

    private fun selectedText(): Layer? = state.layer(selectedId)?.takeIf { it.content is LayerContent.Text }

    private fun dropHighSurrogateAtEnd(text: String): String {
        return if (text.isNotEmpty() && text.last().isHighSurrogate()) {
            text.dropLast(1)
        } else {
            text
        }
    }

    // -------------------------------------------------------- outline, preset

    fun setOutlineOn(on: Boolean) = setOutline(state.outline.copy(on = on))
    fun setOutlineThickness(t: OutlineThickness) = setOutline(state.outline.copy(thickness = t))
    fun setOutlineColour(colour: Int) = setOutline(state.outline.copy(colour = colour))

    private fun setOutline(o: OutlineStyle) {
        if (o == state.outline) return
        endTextSession()
        history.push(EditStep.Decor(state))
        state = state.copy(outline = o)
    }

    fun setPreset(id: String) {
        if (id == state.preset) return
        endTextSession()
        history.push(EditStep.Decor(state))
        state = state.copy(preset = id)
    }

    // ------------------------------------------------------------------ draw

    /** Starts a marker stroke; false (nothing drawn) when the sticker already holds 8 layers. */
    fun beginStroke(x: Float, y: Float, colour: Int, size: MarkerSize): Boolean {
        if (state.layers.size >= DecorSpec.MAX_LAYERS) return false
        endTextSession()
        selectedId = null
        liveStrokes = liveStrokes + MarkerStroke(listOf(Pt(x, y)), colour, size)
        strokeDown = true
        history.push(EditStep.LiveStroke)
        return true
    }

    fun extendStroke(x: Float, y: Float) {
        if (!strokeDown || liveStrokes.isEmpty()) return
        val last = liveStrokes.last()
        liveStrokes = liveStrokes.dropLast(1) + last.copy(points = last.points + Pt(x, y))
    }

    fun endStroke() { strokeDown = false }

    /**
     * Drops the stroke being drawn and its undo step, as if the finger had never landed (a second
     * finger took the touch to move the zoomed view). False when no stroke is down.
     */
    fun cancelStroke(): Boolean {
        if (!strokeDown || liveStrokes.isEmpty()) return false
        strokeDown = false
        liveStrokes = liveStrokes.dropLast(1)
        history.removeLastLiveStroke()
        return true
    }

    /** Turns the live strokes into one Drawing layer (one undo step). */
    fun flushLiveStrokes(): Long? {
        strokeDown = false
        if (liveStrokes.isEmpty()) return null
        val pts = liveStrokes.flatMap { it.points }
        val cx = (pts.minOf { it.x } + pts.maxOf { it.x }) / 2f
        val cy = (pts.minOf { it.y } + pts.maxOf { it.y }) / 2f
        val rel = liveStrokes.map { s -> s.copy(points = s.points.map { Pt(it.x - cx, it.y - cy) }) }
        history.removeLiveStrokes()
        val before = state
        liveStrokes = emptyList()
        val id = place(LayerContent.Drawing(rel), headTop = false, at = Pt(cx, cy))
        selectedId = null
        history.push(EditStep.Decor(before))
        return id
    }

    // ------------------------------------------------------------ mask, undo

    fun recordMaskStroke() = history.push(EditStep.MaskStroke)
    fun clearMaskHistory() = history.removeMaskStrokes()

    fun undo(): UndoResult {
        val step = history.pop() ?: return UndoResult.Nothing
        textSession = null
        textSessionPushed = false
        gesture = null
        guides = Guides.NONE
        return when (step) {
            EditStep.MaskStroke -> UndoResult.MaskStroke
            EditStep.LiveStroke -> { liveStrokes = liveStrokes.dropLast(1); strokeDown = false; UndoResult.Changed }
            is EditStep.Decor -> {
                state = step.before
                if (state.layer(selectedId) == null) selectedId = null
                UndoResult.Changed
            }
        }
    }
}

/** WhatsApp tags for one sticker (spec §3): emoji layers, then decor pieces, deduplicated, at most 3. */
fun emojiTags(state: DecorState, fallback: List<String>): List<String> {
    val tags = LinkedHashSet<String>()
    state.layers.forEach { (it.content as? LayerContent.Emoji)?.let { e -> tags += e.glyph } }
    state.layers.forEach { (it.content as? LayerContent.Decor)?.let { d -> tags += d.emojis } }
    return tags.filter { it.isNotBlank() }.take(3).ifEmpty { fallback }
}
