package com.piptechnologies.stickermaker.feature.create.decor

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
}
