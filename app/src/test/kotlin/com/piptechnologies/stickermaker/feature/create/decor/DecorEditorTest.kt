package com.piptechnologies.stickermaker.feature.create.decor

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DecorEditorTest {

    private var ids = 0L
    private var subject: Box? = Box(156f, 120f, 356f, 500f)
    private lateinit var editor: DecorEditor

    // Every layer is 100 × 50 at scale 1, except emoji (153.6 square).
    private val sizeOf: (LayerContent) -> Size2 = { c -> if (c is LayerContent.Emoji) Size2(153.6f, 153.6f) else Size2(100f, 50f) }

    @Before
    fun setUp() { editor = DecorEditor(sizeOf, { subject }, { ++ids }) }

    private fun text(t: String = "hi") = LayerContent.Text(t, TextStyleId.Sticker, DecorSpec.ROSE, FontMood.Round)
    private fun decor(file: String = "doodles-6.webp") = LayerContent.Decor(file, listOf("💕"))
    private fun emoji(glyph: String = "❤️") = LayerContent.Emoji("red_heart.webp", glyph)

    @Test
    fun firstTextGoesBottomCentreLaterTextCentre() {
        val a = editor.add(text())!!
        assertEquals(256f, editor.state.layer(a)!!.cx, 0f)
        assertEquals(0.87f * 512f, editor.state.layer(a)!!.cy, 1e-3f)
        val b = editor.add(text("b"))!!
        assertEquals(256f, editor.state.layer(b)!!.cy, 0f)
        assertEquals(b, editor.selectedId)
    }

    @Test
    fun headTopPiecesSitOnTheSubject() {
        val id = editor.add(decor("doodles-1.webp"), headTop = true)!!
        val layer = editor.state.layer(id)!!
        assertEquals(256f, layer.cx, 0f)                 // subject box centre
        assertEquals(120f - 50f / 4f, layer.cy, 1e-3f)   // a quarter of its height above the top
        subject = null
        val centred = editor.add(decor("doodles-2.webp"), headTop = true)!!
        assertEquals(256f, editor.state.layer(centred)!!.cy, 0f)
    }

    @Test
    fun ninthLayerIsRefused() {
        repeat(8) { assertNotNull(editor.add(decor())) }
        assertNull(editor.add(decor()))
        assertEquals(8, editor.state.layers.size)
        assertNull(editor.duplicate(editor.state.layers.first().id))
    }

    @Test
    fun duplicateFlipBehindDeleteAreOneUndoStepEach() {
        val id = editor.add(decor())!!
        val copy = editor.duplicate(id)!!
        assertEquals(256f + 0.08f * 512f, editor.state.layer(copy)!!.cx, 1e-3f)
        editor.flip(copy)
        assertTrue(editor.state.layer(copy)!!.flipped)
        editor.toggleBehind(copy)
        assertTrue(editor.state.layer(copy)!!.behind)
        editor.delete(copy)
        assertNull(editor.state.layer(copy))
        assertNull(editor.selectedId)
        assertEquals(UndoResult.Changed, editor.undo()); assertNotNull(editor.state.layer(copy))
        assertEquals(UndoResult.Changed, editor.undo()); assertFalse(editor.state.layer(copy)!!.behind)
        assertEquals(UndoResult.Changed, editor.undo()); assertFalse(editor.state.layer(copy)!!.flipped)
        assertEquals(UndoResult.Changed, editor.undo()); assertNull(editor.state.layer(copy))
        assertEquals(UndoResult.Changed, editor.undo()); assertTrue(editor.state.layers.isEmpty())
        assertEquals(UndoResult.Nothing, editor.undo())
    }

    @Test
    fun dragSnapsToTheCentreAndStaysInsideTheCanvas() {
        val id = editor.add(decor())!!
        editor.beginGesture(id)
        editor.drag(id, 40f, 0f)
        assertFalse(editor.guides.x)
        editor.drag(id, -37f, 0f)                           // raw 259: within 6 px of the centre
        assertEquals(256f, editor.state.layer(id)!!.cx, 0f)
        assertTrue(editor.guides.x)
        editor.drag(id, 5000f, 5000f)
        assertEquals(512f, editor.state.layer(id)!!.cx, 0f)  // the centre never leaves the canvas
        assertEquals(512f, editor.state.layer(id)!!.cy, 0f)
        editor.endGesture()
        assertFalse(editor.guides.x)
        assertEquals(UndoResult.Changed, editor.undo())      // the whole gesture is one step
        assertEquals(256f, editor.state.layer(id)!!.cx, 0f)
    }

    @Test
    fun pinchClampsScaleAndSnapsRotation() {
        val e = editor.add(emoji())!!
        editor.beginGesture(e)
        editor.pinch(e, zoom = 10f, rotationDeg = 3f)
        val layer = editor.state.layer(e)!!
        assertEquals(0.5f * 512f / 153.6f, layer.scale, 1e-4f)   // emoji cap: half the canvas
        assertEquals(0f, layer.rotation, 0f)                       // within ±4° snaps to 0
        editor.pinch(e, zoom = 0.001f, rotationDeg = 20f)
        assertEquals(0.1f * 512f / 153.6f, editor.state.layer(e)!!.scale, 1e-4f)
        assertEquals(23f, editor.state.layer(e)!!.rotation, 1e-3f)
        editor.endGesture()
        editor.setScaleRotation(e, scale = 1f, rotation = -2f, start = true)
        assertEquals(0f, editor.state.layer(e)!!.rotation, 0f)
    }

    @Test
    fun scaleIsCappedByTheLongerSide() {
        // A thin upright doodle (20 × 400) and a tall emoji (100 × 200): capped by width, the doodle
        // could reach 25.6× (a 10,240 px tall bitmap).
        val tall: (LayerContent) -> Size2 = { c ->
            if (c is LayerContent.Emoji) Size2(100f, 200f) else Size2(20f, 400f)
        }
        val e = DecorEditor(tall, { null }, { ++ids })
        val doodle = e.add(decor())!!
        e.beginGesture(doodle); e.pinch(doodle, zoom = 100f, rotationDeg = 0f); e.endGesture()
        assertEquals(512f / 400f, e.state.layer(doodle)!!.scale, 1e-4f)
        assertEquals(512f, e.renderedSize(e.state.layer(doodle)!!).h, 1e-2f)     // the canvas height, no more
        val heart = e.add(emoji())!!
        e.setScaleRotation(heart, scale = 100f, rotation = 0f, start = true); e.endGesture()
        assertEquals(0.5f * 512f / 200f, e.state.layer(heart)!!.scale, 1e-4f)  // emoji: half the canvas
        // A piece taller than the canvas at scale 1 arrives scaled to fit.
        val huge = DecorEditor({ Size2(40f, 1024f) }, { null }, { ++ids })
        val id = huge.add(decor())!!
        assertEquals(0.5f, huge.state.layer(id)!!.scale, 1e-4f)
    }

    @Test
    fun theFloorIsOnTheLongerSideToo() {
        // A thin upright piece (40 × 400) arrives at scale 1. With a floor on its width (10% of 512 = 51.2 px
        // wide, scale 1.28) it could not shrink at all; on its longer side the floor is 51.2 / 400 = 0.128.
        val e = DecorEditor({ Size2(40f, 400f) }, { null }, { ++ids })
        val id = e.add(decor())!!
        assertEquals(1f, e.state.layer(id)!!.scale, 0f)
        e.beginGesture(id)
        e.pinch(id, zoom = 0.5f, rotationDeg = 0f)
        assertEquals(0.5f, e.state.layer(id)!!.scale, 1e-4f)          // below its arrival scale
        e.pinch(id, zoom = 0.01f, rotationDeg = 0f)
        assertEquals(0.1f * 512f / 400f, e.state.layer(id)!!.scale, 1e-4f)
        e.endGesture()
    }

    @Test
    fun aFullHeightDrawingKeepsItsDrawnSize() {
        // The renderer's drawing box: the strokes' extent around the centre plus the widest ink (Medium 14 + 2 × 3).
        val ink = 20f
        val drawn: (LayerContent) -> Size2 = { c ->
            val points = (c as LayerContent.Drawing).strokes.flatMap { it.points }
            Size2(2f * points.maxOf { abs(it.x) } + ink, 2f * points.maxOf { abs(it.y) } + ink)
        }
        val e = DecorEditor(drawn, { null }, { ++ids })
        e.beginStroke(256f, 0f, DecorSpec.ROSE, MarkerSize.M)
        e.extendStroke(256f, 512f)
        e.endStroke()
        val id = e.flushLiveStrokes()!!
        assertEquals("not shrunk to 512 / 532 when Draw is left", 1f, e.state.layer(id)!!.scale, 0f)
        e.beginGesture(id)
        e.pinch(id, zoom = 2f, rotationDeg = 0f)
        assertEquals("it can't grow past its drawn size", 1f, e.state.layer(id)!!.scale, 0f)
        e.pinch(id, zoom = 0.5f, rotationDeg = 0f)
        assertEquals("but it can shrink", 0.5f, e.state.layer(id)!!.scale, 1e-4f)
        e.endGesture()
    }

    @Test
    fun typingIsOneUndoStepAndEmptyTextIsDroppedOnClose() {
        assertTrue(editor.setText("m"))
        assertTrue(editor.setText("mi"))
        assertTrue(editor.setText("miss u"))
        val id = editor.selectedId!!
        assertEquals("miss u", (editor.state.layer(id)!!.content as LayerContent.Text).text)
        editor.setTextColour(0xFF000000.toInt())
        editor.setTextStyle(TextStyleId.Bubble)
        editor.endTextSession()
        assertEquals(UndoResult.Changed, editor.undo())
        assertTrue(editor.state.layers.isEmpty())           // one step removes typing and style edits together
        editor.setText("x"); editor.setText("")
        editor.endTextSession()
        assertTrue(editor.state.layers.isEmpty())
    }

    @Test
    fun styleWithoutATextLayerSetsTheDefaultsForTheNextOne() {
        editor.setTextStyle(TextStyleId.Classic)
        editor.setTextFont(FontMood.Hand)
        assertTrue(editor.state.layers.isEmpty())
        editor.setText("yo")
        val t = editor.state.layers.single().content as LayerContent.Text
        assertEquals(TextStyleId.Classic, t.style)
        assertEquals(FontMood.Hand, t.font)
    }

    @Test
    fun liveStrokesUndoOneByOneThenFlushIntoOneLayer() {
        assertTrue(editor.beginStroke(10f, 10f, DecorSpec.ROSE, MarkerSize.M))
        editor.extendStroke(30f, 10f); editor.endStroke()
        assertTrue(editor.beginStroke(10f, 50f, DecorSpec.ROSE, MarkerSize.L))
        editor.extendStroke(30f, 70f); editor.endStroke()
        assertEquals(2, editor.liveStrokes.size)
        assertEquals(UndoResult.Changed, editor.undo())
        assertEquals(1, editor.liveStrokes.size)
        val id = editor.flushLiveStrokes()!!
        val layer = editor.state.layer(id)!!
        assertEquals(20f, layer.cx, 1e-3f); assertEquals(10f, layer.cy, 1e-3f)
        assertTrue(editor.liveStrokes.isEmpty())
        assertEquals(UndoResult.Changed, editor.undo())
        assertTrue(editor.state.layers.isEmpty())
    }

    @Test
    fun flushWhileStrokeInProgress() {
        editor.beginStroke(10f, 10f, DecorSpec.ROSE, MarkerSize.S)
        editor.extendStroke(40f, 40f)                        // finger still down
        assertNotNull(editor.flushLiveStrokes())
        assertTrue(editor.liveStrokes.isEmpty())
        editor.extendStroke(60f, 60f)                        // stray move after the flush is ignored
        editor.endStroke()
        assertTrue(editor.liveStrokes.isEmpty())
        assertEquals(1, editor.state.layers.size)
    }

    @Test
    fun aCancelledStrokeLeavesNoDotAndNoUndoStep() {
        assertFalse("nothing to cancel", editor.cancelStroke())
        editor.beginStroke(10f, 10f, DecorSpec.ROSE, MarkerSize.M)
        assertTrue(editor.canUndo)
        assertTrue(editor.cancelStroke())
        assertTrue(editor.liveStrokes.isEmpty())
        assertFalse(editor.canUndo)
        editor.extendStroke(30f, 30f)                        // a stray move after the cancel is ignored
        assertTrue(editor.liveStrokes.isEmpty())
        assertNull(editor.flushLiveStrokes())

        // Only the stroke being drawn goes: the one before it keeps its points and its step.
        editor.beginStroke(10f, 10f, DecorSpec.ROSE, MarkerSize.M)
        editor.extendStroke(40f, 10f); editor.endStroke()
        editor.beginStroke(90f, 90f, DecorSpec.ROSE, MarkerSize.M)
        assertTrue(editor.cancelStroke())
        assertFalse("a finished stroke is not cancelled", editor.cancelStroke())
        assertEquals(listOf(Pt(10f, 10f), Pt(40f, 10f)), editor.liveStrokes.single().points)
        assertEquals(UndoResult.Changed, editor.undo())
        assertTrue(editor.liveStrokes.isEmpty())
        assertEquals(UndoResult.Nothing, editor.undo())
    }

    @Test
    fun strokesAreRefusedAtTheLayerLimit() {
        repeat(8) { editor.add(decor()) }
        assertFalse(editor.beginStroke(0f, 0f, DecorSpec.ROSE, MarkerSize.M))
    }

    @Test
    fun clearMaskHistoryKeepsDecorSteps() {
        editor.recordMaskStroke()
        val id = editor.add(decor())!!
        editor.recordMaskStroke()
        editor.setOutlineThickness(OutlineThickness.Thick)
        editor.clearMaskHistory()                            // Auto re-ran the cut-out
        assertEquals(UndoResult.Changed, editor.undo())
        assertEquals(OutlineThickness.Medium, editor.state.outline.thickness)
        assertEquals(UndoResult.Changed, editor.undo())
        assertNull(editor.state.layer(id))
        assertEquals(UndoResult.Nothing, editor.undo())
    }

    @Test
    fun maskStrokesUndoThroughTheCaller() {
        editor.recordMaskStroke()
        assertTrue(editor.canUndo)
        assertEquals(UndoResult.MaskStroke, editor.undo())
        assertFalse(editor.canUndo)
    }

    @Test
    fun hitTestPrefersFrontLayersAndRespectsRotation() {
        val back = editor.add(decor())!!
        editor.toggleBehind(back)
        val front = editor.add(decor())!!
        assertEquals(front, editor.hitTest(256f, 256f))
        editor.delete(front)
        assertEquals(back, editor.hitTest(256f, 256f))
        editor.beginGesture(back); editor.pinch(back, 1f, 90f); editor.endGesture()
        assertEquals(back, editor.hitTest(256f, 256f + 45f))     // 100 × 50 turned upright
        assertNull(editor.hitTest(256f + 70f, 256f))
    }

    @Test
    fun aTapUnderTheSelectedLayerCyclesDownTheStackAndAGestureKeepsIt() {
        // A caption, an emoji over it and a big frame over both, all at the centre.
        val caption = editor.add(text())!!
        editor.beginGesture(caption)
        editor.drag(caption, 0f, 256f - editor.state.layer(caption)!!.cy)
        editor.endGesture()
        val emoji = editor.add(emoji())!!
        val frame = editor.add(decor())!!
        editor.beginGesture(frame); editor.pinch(frame, 4f, 0f); editor.endGesture()
        editor.select(null)

        assertEquals("nothing selected: the top-most", frame, editor.tapTarget(256f, 256f))
        editor.select(frame)
        assertEquals("under the selected frame: the next one down", emoji, editor.tapTarget(256f, 256f))
        editor.select(emoji)
        assertEquals(caption, editor.tapTarget(256f, 256f))
        editor.select(caption)
        assertEquals("the bottom wraps to the top-most", frame, editor.tapTarget(256f, 256f))
        // Where only the frame is, the selected frame stays; where the selected layer isn't, the top-most.
        editor.select(frame)
        assertEquals(frame, editor.tapTarget(256f + 150f, 256f))
        editor.select(caption)
        assertEquals(frame, editor.tapTarget(256f + 150f, 256f))
        assertNull(editor.tapTarget(10f, 10f))

        // A drag that starts inside the selected layer's box moves it, even under the frame.
        editor.select(emoji)
        assertEquals(emoji, editor.gestureTarget(256f, 256f))
        editor.select(null)
        assertEquals(frame, editor.gestureTarget(256f, 256f))
        editor.select(caption)
        assertEquals("the selected layer isn't there: the top-most", frame, editor.gestureTarget(256f + 150f, 256f))

        // A double tap edits the selected caption under the point, else the top-most caption there.
        assertEquals(caption, editor.doubleTapTarget(256f, 256f))
        editor.select(frame)
        assertEquals(caption, editor.doubleTapTarget(256f, 256f))
        assertNull(editor.doubleTapTarget(256f + 150f, 256f))
    }

    @Test
    fun presetAndOutlineChangesAreUndoable() {
        editor.setPreset("heartbeat")
        assertTrue(editor.state.animated)
        editor.setOutlineOn(false)
        editor.setOutlineColour(DecorSpec.ROSE)
        editor.undo(); editor.undo()
        assertTrue(editor.state.outline.on)
        editor.undo()
        assertFalse(editor.state.animated)
    }

    @Test
    fun emojiTagsPreferEmojiLayersThenDecorThenFallback() {
        assertEquals(listOf("❤️", "😊"), emojiTags(DecorState(), listOf("❤️", "😊")))
        editor.add(decor())
        editor.add(emoji("😍"))
        editor.add(emoji("🥺"))
        editor.add(emoji("😍"))
        assertEquals(listOf("😍", "🥺", "💕"), emojiTags(editor.state, listOf("❤️")))
    }

    @Test
    fun singleLetterTextStartsAtScale1() {
        var idCounter = 0L
        val contentSizeOf: (LayerContent) -> Size2 = { c ->
            when (c) {
                is LayerContent.Text -> Size2(30f * c.text.length, 60f)
                is LayerContent.Drawing -> {
                    val pts = c.strokes.flatMap { it.points }
                    if (pts.isEmpty()) Size2(100f, 50f) else {
                        val minX = pts.minOf { it.x }
                        val maxX = pts.maxOf { it.x }
                        val minY = pts.minOf { it.y }
                        val maxY = pts.maxOf { it.y }
                        Size2(maxX - minX + 20f, maxY - minY + 20f)
                    }
                }
                else -> Size2(100f, 50f)
            }
        }
        val e = DecorEditor(contentSizeOf, { null }, { ++idCounter })
        val id = e.add(LayerContent.Text("i", TextStyleId.Sticker, DecorSpec.ROSE, FontMood.Round))!!
        assertEquals(1f, e.state.layer(id)!!.scale, 1e-4f)
    }

    @Test
    fun pinchClampsToMinNotStartScale() {
        var idCounter = 0L
        val contentSizeOf: (LayerContent) -> Size2 = { c ->
            when (c) {
                is LayerContent.Text -> Size2(30f * c.text.length, 60f)
                is LayerContent.Drawing -> {
                    val pts = c.strokes.flatMap { it.points }
                    if (pts.isEmpty()) Size2(100f, 50f) else {
                        val minX = pts.minOf { it.x }
                        val maxX = pts.maxOf { it.x }
                        val minY = pts.minOf { it.y }
                        val maxY = pts.maxOf { it.y }
                        Size2(maxX - minX + 20f, maxY - minY + 20f)
                    }
                }
                else -> Size2(100f, 50f)
            }
        }
        val e = DecorEditor(contentSizeOf, { null }, { ++idCounter })
        val iId = e.add(LayerContent.Text("i", TextStyleId.Sticker, DecorSpec.ROSE, FontMood.Round))!!
        e.beginGesture(iId)
        e.pinch(iId, zoom = 0.5f, rotationDeg = 0f)
        assertEquals(0.1f * 512f / 60f, e.state.layer(iId)!!.scale, 1e-4f)   // the floor on its longer side (60)

        val decorId = e.add(LayerContent.Decor("doodles.webp", listOf("💕")))!!
        e.beginGesture(decorId)
        e.pinch(decorId, zoom = 0.1f, rotationDeg = 0f)
        assertEquals(0.512f, e.state.layer(decorId)!!.scale, 1e-3f)
        e.endGesture()
    }

    @Test
    fun textEditCapsScaleToNewMax() {
        var idCounter = 0L
        val contentSizeOf: (LayerContent) -> Size2 = { c ->
            when (c) {
                is LayerContent.Text -> Size2(30f * c.text.length, 60f)
                is LayerContent.Drawing -> {
                    val pts = c.strokes.flatMap { it.points }
                    if (pts.isEmpty()) Size2(100f, 50f) else {
                        val minX = pts.minOf { it.x }
                        val maxX = pts.maxOf { it.x }
                        val minY = pts.minOf { it.y }
                        val maxY = pts.maxOf { it.y }
                        Size2(maxX - minX + 20f, maxY - minY + 20f)
                    }
                }
                else -> Size2(100f, 50f)
            }
        }
        val e = DecorEditor(contentSizeOf, { null }, { ++idCounter })
        val id = e.add(LayerContent.Text("a", TextStyleId.Sticker, DecorSpec.ROSE, FontMood.Round))!!
        assertEquals(1f, e.state.layer(id)!!.scale, 1e-4f)

        e.setText("a".repeat(30))
        val expectedScale = 512f / (30f * 30f)
        assertEquals(expectedScale, e.state.layer(id)!!.scale, 1e-4f)

        e.setText("ab")
        // Short edit doesn't raise the scale back; it stays capped
        assertEquals(expectedScale, e.state.layer(id)!!.scale, 1e-4f)
    }

    @Test
    fun noOpGestureDoesNotLeaveUndoStep() {
        val id = editor.add(decor())!!
        editor.beginGesture(id)
        editor.endGesture()
        assertEquals(UndoResult.Changed, editor.undo())
        assertTrue(editor.state.layers.isEmpty())
        assertEquals(UndoResult.Nothing, editor.undo())
    }

    @Test
    fun emptyTextSessionDoesNotLeaveUndoStep() {
        assertTrue(editor.setText("a"))
        assertTrue(editor.setText(""))
        editor.endTextSession()
        assertFalse(editor.canUndo)
    }

    @Test
    fun deleteLayerDuringGestureDoesNotCrash() {
        val id = editor.add(decor())!!
        editor.beginGesture(id)
        editor.delete(id)
        editor.pinch(id, zoom = 1.1f, rotationDeg = 0f)
        assertTrue(editor.state.layers.isEmpty())
    }

    @Test
    fun nonFiniteGestureInputIsIgnored() {
        val id = editor.add(decor())!!
        val before = editor.state.layer(id)!!
        editor.setScaleRotation(id, Float.NaN, 0f, start = true)
        editor.setScaleRotation(id, 1.2f, Float.POSITIVE_INFINITY)
        editor.pinch(id, Float.POSITIVE_INFINITY, 0f)
        editor.pinch(id, 1.1f, Float.NaN)
        editor.drag(id, Float.NaN, 5f)
        assertEquals(before, editor.state.layer(id))
        // The gesture itself still runs: a finite move applies, as one undo step.
        editor.pinch(id, 1.5f, 0f)
        assertEquals(1.5f, editor.state.layer(id)!!.scale, 1e-4f)
        editor.endGesture()
        assertEquals(UndoResult.Changed, editor.undo())
        assertEquals(before, editor.state.layer(id))
    }

    @Test
    fun textWithTrailingEmojiDropsSurrogate() {
        val textContent = "a".repeat(29) + "😍"
        editor.setText(textContent)
        val id = editor.selectedId!!
        val actualText = (editor.state.layer(id)!!.content as LayerContent.Text).text
        assertEquals("a".repeat(29), actualText)
    }
}
