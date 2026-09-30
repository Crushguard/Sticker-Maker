package com.piptechnologies.stickermaker.feature.create.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.piptechnologies.stickermaker.feature.create.LayerKind
import com.piptechnologies.stickermaker.feature.create.LayerUi
import com.piptechnologies.stickermaker.feature.create.decor.Affine
import com.piptechnologies.stickermaker.feature.create.decor.Easing
import com.piptechnologies.stickermaker.feature.create.decor.Keyframe
import com.piptechnologies.stickermaker.feature.create.decor.MotionPreset
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanvasGeometryTest {

    private val still = Keyframe(0f, 1f, 1f, 0f, 0f, 0f, Easing.Linear)

    /** Heartbeat's shape: shrunk to 0.88 at rest, 1.12× a seventh of the way in (about the centre). */
    private val heartbeat = MotionPreset(
        id = "heartbeat", durationMs = 1000, frames = 12, baseScale = 0.88f, pivotX = 0.5f, pivotY = 0.5f,
        keyframes = listOf(
            still.copy(easing = Easing.EaseInOut),
            Keyframe(0.14f, 1.12f, 1.12f, 0f, 0f, 0f, Easing.EaseInOut),
            still.copy(t = 1f)
        ),
        particles = null
    )

    /** Wiggle's shape: turns about a pivot low on the canvas. */
    private val wiggle = MotionPreset(
        id = "wiggle", durationMs = 900, frames = 12, baseScale = 0.9f, pivotX = 0.5f, pivotY = 0.8f,
        keyframes = listOf(still, Keyframe(0.25f, 1f, 1f, -5f, 0f, 0f, Easing.Linear), still.copy(t = 1f)),
        particles = null
    )

    private fun layer(cx: Float, cy: Float, w: Float, h: Float, rotation: Float = 0f) =
        LayerUi(7L, LayerKind.Emoji, cx, cy, w, h, scale = 1f, rotation = rotation, flipped = false, behind = false)

    private fun assertNear(message: String, expected: Offset, actual: Offset, tolerance: Float = 0.01f) {
        assertEquals("$message: x of $actual", expected.x, actual.x, tolerance)
        assertEquals("$message: y of $actual", expected.y, actual.y, tolerance)
    }

    private fun assertNear(expected: Offset, actual: Offset, tolerance: Float = 0.01f) =
        assertNear("", expected, actual, tolerance)

    // ------------------------------------------------------------- mapping

    @Test
    fun theViewFitsTheCanvasIntoTheBoxAndZoomsAboutItsCentre() {
        val fit = viewTransform(edge = 1024f, zoom = 1f, panX = 0f, panY = 0f)
        assertNear(Offset.Zero, fit.mapPoint(Offset.Zero))
        assertNear(Offset(1024f, 1024f), fit.mapPoint(Offset(512f, 512f)))
        val zoomed = viewTransform(edge = 1024f, zoom = 2f, panX = 30f, panY = -10f)
        assertNear(
            "the canvas centre stays at the box centre, panned",
            Offset(542f, 502f),
            zoomed.mapPoint(Offset(256f, 256f))
        )
        assertNear("a canvas px is 4 box px at 2×", Offset(942f, 502f), zoomed.mapPoint(Offset(356f, 256f)))
    }

    @Test
    fun theRestPoseShrinksTheStickerAboutTheCentre() {
        assertEquals(Affine.IDENTITY, motionPose(null, 500f))
        val rest = motionPose(heartbeat, 0f)
        assertNear(Offset(256f, 256f), rest.mapPoint(Offset(256f, 256f)))
        val corner = 256f - 256f * 0.88f
        assertNear("a corner comes in by 12%", Offset(corner, corner), rest.mapPoint(Offset.Zero))
        val peak = motionPose(heartbeat, 140f)
        val beat = Offset(256f + 100f * 1.12f * 0.88f, 256f)
        assertNear("1.12 × 0.88 at the first beat", beat, peak.mapPoint(Offset(356f, 256f)))
    }

    @Test
    fun theInverseTakesABoxPointBackToTheCanvasPointUnderIt() {
        val frame = viewTransform(edge = 777f, zoom = 1.6f, panX = -40f, panY = 25f) * motionPose(wiggle, 225f)
        val back = frame.inverse()
        for (p in listOf(Offset.Zero, Offset(256f, 256f), Offset(100f, 400f), Offset(512f, 37f))) {
            assertNear(p, back.mapPoint(frame.mapPoint(p)), 0.05f)
        }
        val move = Offset(12f, -30f)
        assertNear("a move maps by the linear part alone", move, back.mapVector(frame.mapVector(move)), 0.01f)
        assertEquals("a box not laid out yet maps nothing", Affine.IDENTITY, viewTransform(0f, 1f, 0f, 0f).inverse())
    }

    @Test
    fun theCanvasDrawsWithTheSameMatrixValuesTheMapGives() {
        val frame = viewTransform(edge = 777f, zoom = 1.6f, panX = -40f, panY = 25f) * motionPose(wiggle, 225f)
        val values = FloatArray(9) { -1f }
        assertArrayEquals(frame.matrixValues(), frame.writeMatrix(values), 0f)
        assertArrayEquals("written in place", frame.matrixValues(), values, 0f)
    }

    @Test
    fun theZoomedViewKeepsCoveringItsBox() {
        assertEquals(0f, clampPan(50f, zoom = 1f, edge = 800f), 0f)
        assertEquals(240f, clampPan(500f, zoom = 1.6f, edge = 800f), 0.001f)
        assertEquals(-240f, clampPan(-500f, zoom = 1.6f, edge = 800f), 0.001f)
        assertEquals(100f, clampPan(100f, zoom = 1.6f, edge = 800f), 0f)
    }

    // ------------------------------------------------------- selection box

    @Test
    fun theBoxTurnsWithTheLayerAndItsCornersSitTheOutsetOut() {
        val view = viewTransform(edge = 1024f, zoom = 1f, panX = 0f, panY = 0f)   // 2 box px per canvas px
        val upright = layerBox(layer(256f, 256f, 100f, 50f), view)
        assertNear(Offset(512f, 512f), upright.centre)
        assertEquals(100f, upright.halfWidth, 0.001f)
        assertEquals(50f, upright.halfHeight, 0.001f)
        assertNear("top-left, 6 px out", Offset(512f - 106f, 512f - 56f), upright.corner(-1f, -1f, 6f))
        val turned = layerBox(layer(256f, 256f, 100f, 50f, rotation = 90f), view)
        assertEquals(90f, turned.angle, 0.01f)
        // Turned a quarter clockwise, the layer's top-left corner is at the top-right on screen.
        assertNear(Offset(512f + 56f, 512f - 106f), turned.corner(-1f, -1f, 6f))
    }

    @Test
    fun theBoxFollowsTheZoomThePanAndTheMotionPose() {
        val frame = viewTransform(edge = 512f, zoom = 2f, panX = 10f, panY = 0f) * motionPose(heartbeat, 0f)
        val box = layerBox(layer(356f, 256f, 40f, 20f), frame)
        assertNear(Offset(256f + 10f + 100f * 0.88f * 2f, 256f), box.centre)
        assertEquals(20f * 0.88f * 2f, box.halfWidth, 0.001f)
        assertEquals(10f * 0.88f * 2f, box.halfHeight, 0.001f)
    }

    @Test
    fun handlesSitOnTheirCornersAndSwapStartAndEndInRightToLeft() {
        val box = layerBox(layer(256f, 256f, 100f, 50f), viewTransform(512f, 1f, 0f, 0f))
        val ltr = handleCorners(box, outset = 6f, rtl = false)
        assertNear("delete top-left", Offset(200f, 225f), ltr.delete)
        assertNear("edit top-right", Offset(312f, 225f), ltr.edit)
        assertNear("resize bottom-right", Offset(312f, 287f), ltr.transform)
        val rtl = handleCorners(box, outset = 6f, rtl = true)
        assertNear("delete top-right", Offset(312f, 225f), rtl.delete)
        assertNear("edit top-left", Offset(200f, 225f), rtl.edit)
        assertNear("resize bottom-left", Offset(200f, 287f), rtl.transform)
    }

    @Test
    fun theCornerHandleScalesWithTheDistanceAndTurnsWithTheAngle() {
        val centre = Offset(100f, 100f)
        val twice = handleTransform(centre, Offset(150f, 100f), Offset(100f, 200f), scale = 0.8f, rotation = 10f)
        assertEquals(1.6f, twice.scale, 0.0001f)
        assertEquals("a quarter turn clockwise", 100f, twice.rotation, 0.001f)
        val back = handleTransform(centre, Offset(150f, 150f), Offset(125f, 125f), scale = 1f, rotation = 0f)
        assertEquals(0.5f, back.scale, 0.0001f)
        assertEquals(0f, back.rotation, 0.001f)
        assertEquals(
            "no reach, no change",
            ScaleRotation(1.2f, 30f),
            handleTransform(centre, from = centre, to = Offset(0f, 0f), scale = 1.2f, rotation = 30f)
        )
    }

    // ---------------------------------------------------------------- rules

    @Test
    fun toolLabelsFallBackToNineAndAHalfThenHide() {
        val widths = mapOf(10f to 60, 9.5f to 57)
        assertEquals(10f, toolLabelSp(60f) { widths.getValue(it) }, 0f)
        assertEquals(9.5f, toolLabelSp(58f) { widths.getValue(it) }, 0f)
        assertEquals(0f, toolLabelSp(56.5f) { widths.getValue(it) }, 0f)
    }

    @Test
    fun theActionPillAddsPaddingIconGapAndInsetToItsLabels() {
        // 4 × (12 + 15 + 6 + 12) + 3 × 2 + 2 × 2 = 190 dp around the labels.
        assertEquals(190f + 150f, actionPillWidth(listOf(60, 20, 40, 30), dp = 1f), 0.001f)
        val atTwo = actionPillWidth(listOf(60, 20, 40, 30), dp = 2f)
        assertEquals("the labels are px already", 190f * 2f + 150f, atTwo, 0.001f)
        val tight = actionPillWidth(listOf(60, 20, 40, 30), dp = 1f, sidePadding = 8f)
        assertEquals("8 dp padding saves 4 × 8 dp", 190f - 32f + 150f, tight, 0.001f)
    }

    @Test
    fun theActionPillNarrowsItsPaddingBeforeItDropsItsLabels() {
        val labels = listOf(60, 20, 40, 30)
        assertEquals(PillFit.Full, actionPillFit(labels, dp = 1f, room = 340f))
        assertEquals(PillFit.Tight, actionPillFit(labels, dp = 1f, room = 339f))
        assertEquals(PillFit.Tight, actionPillFit(labels, dp = 1f, room = 308f))
        assertEquals(PillFit.Icons, actionPillFit(labels, dp = 1f, room = 307f))
        assertEquals(8f, PillFit.Tight.sidePadding, 0f)
        assertFalse(PillFit.Icons.labels)
    }

    @Test
    fun theActionPillWithIconsOnlyIsItsPaddingAndIcons() {
        val labels = listOf(60, 20, 40, 30)
        assertEquals(actionPillWidth(labels, dp = 2f), actionPillWidth(PillFit.Full, labels, dp = 2f), 0.001f)
        val tight = actionPillWidth(labels, dp = 2f, sidePadding = 8f)
        assertEquals(tight, actionPillWidth(PillFit.Tight, labels, dp = 2f), 0.001f)
        // 4 × (12 + 15 + 12) + 3 × 2 + 2 × 2 = 166 dp, no labels.
        assertEquals(166f * 2f, actionPillWidth(PillFit.Icons, labels, dp = 2f), 0.001f)
    }

    @Test
    fun theSelectionBoundsGrowByTheOutsetAndFollowTheTurn() {
        val view = viewTransform(edge = 512f, zoom = 1f, panX = 0f, panY = 0f)
        val upright = layerBox(layer(256f, 256f, 100f, 50f), view).bounds(17f)
        assertEquals(Rect(256f - 67f, 256f - 42f, 256f + 67f, 256f + 42f), upright)
        val turned = layerBox(layer(256f, 256f, 100f, 50f, rotation = 90f), view).bounds(17f)
        assertEquals(256f - 42f, turned.left, 0.001f)
        assertEquals(256f - 67f, turned.top, 0.001f)
        assertEquals(256f + 42f, turned.right, 0.001f)
        assertEquals(256f + 67f, turned.bottom, 0.001f)
    }

    @Test
    fun thePillZonesSitCentredAtTheCardsBottomOrTop() {
        val card = Size(350f, 350f)
        val pill = Size(300f, 40f)
        assertEquals(Rect(25f, 350f - 6f - 40f, 325f, 350f - 6f), pillZone(card, pill, PillPlace.Bottom, gap = 6f))
        assertEquals(Rect(25f, 52f, 325f, 92f), pillZone(card, pill, PillPlace.Top, gap = 52f))
    }

    @Test
    fun thePillMovesToTheTopOnlyWhenTheSelectionIsUnderItAndNotOverTheTop() {
        val bottom = Rect(25f, 304f, 325f, 344f)
        val top = Rect(25f, 52f, 325f, 92f)
        // A caption low on the sticker: its handles reach into the pill.
        assertEquals(PillPlace.Top, pillPlace(Rect(120f, 280f, 230f, 320f), bottom, top))
        // An emoji at the centre: clear of both.
        assertEquals(PillPlace.Bottom, pillPlace(Rect(150f, 150f, 200f, 200f), bottom, top))
        // A frame over the whole card: both zones are under it, so the spec's bottom stays.
        assertEquals(PillPlace.Bottom, pillPlace(Rect(0f, 0f, 350f, 350f), bottom, top))
        // A layer at the top: the bottom is free.
        assertEquals(PillPlace.Bottom, pillPlace(Rect(100f, 30f, 250f, 100f), bottom, top))
        // Touching edges don't overlap.
        assertEquals(PillPlace.Bottom, pillPlace(Rect(100f, 200f, 250f, 304f), bottom, top))
    }

    @Test
    fun thePillKeepsItsPlaceThroughAGestureAndDecidesForANewLayerAtOnce() {
        val placer = PillPlacer()
        var decided = 0
        val place = { at: PillPlace -> { decided++; at } }
        assertEquals(PillPlace.Top, placer.place(1L, live = false, decide = place(PillPlace.Top)))
        val kept = placer.place(1L, live = true, decide = place(PillPlace.Bottom))
        assertEquals("kept through the gesture", PillPlace.Top, kept)
        assertEquals(1, decided)
        val lifted = placer.place(1L, live = false, decide = place(PillPlace.Bottom))
        assertEquals("decided again once the finger lifts", PillPlace.Bottom, lifted)
        val other = placer.place(2L, live = true, decide = place(PillPlace.Top))
        assertEquals("another layer, mid-gesture: its own place, now", PillPlace.Top, other)
        assertEquals(PillPlace.Top, placer.place(2L, live = true, decide = place(PillPlace.Bottom)))
        assertEquals(3, decided)
    }

    @Test
    fun aTouchStaysATapUntilItPassesTheSlopThenCatchesUpAtOnce() {
        val touch = TouchTracker(slop = 10f)
        var asked = 0
        val decide = { asked++; TouchKind.Layer }
        assertEquals(Offset.Zero, touch.step(1, Offset(3f, 4f), decide))
        assertEquals("10 px is still the slop", Offset.Zero, touch.step(1, Offset(3f, 4f), decide))
        assertTrue(touch.isTap)
        val caughtUp = touch.step(1, Offset(3f, 4f), decide)
        assertEquals("the whole drift lands on the deciding event", Offset(9f, 12f), caughtUp)
        assertEquals(TouchKind.Layer, touch.kind)
        assertFalse(touch.isTap)
        assertEquals(Offset(1f, -2f), touch.step(1, Offset(1f, -2f), decide))
        assertEquals("decided once", 1, asked)
    }

    @Test
    fun aSecondFingerDecidesAtOnceAndIsNeverATap() {
        val touch = TouchTracker(slop = 10f)
        assertEquals(Offset.Zero, touch.step(1, Offset(3f, 4f)) { TouchKind.View })
        val pinch = touch.step(2, Offset(1f, 1f)) { TouchKind.View }
        assertEquals("a pinch starts from its own pan, not the first finger's drift", Offset(1f, 1f), pinch)
        assertEquals(TouchKind.View, touch.kind)
        assertFalse(touch.isTap)
        val still = TouchTracker(slop = 10f)
        still.step(2, Offset.Zero) { TouchKind.Ignored }
        still.step(1, Offset.Zero) { TouchKind.Ignored }
        assertFalse("a two-finger touch that didn't move is no tap", still.isTap)
    }

    @Test
    fun aTouchMovesTheLayerItStartedOnElseTheZoomedViewElseNothing() {
        val onLayer = Offset(300f, 300f)
        val empty = Offset(10f, 10f)
        val layerAt = { p: Offset -> p == onLayer }
        assertEquals("never also the view", TouchKind.Layer, touchKind(listOf(onLayer), layerAt, zoomed = true))
        assertEquals(TouchKind.View, touchKind(listOf(empty), layerAt, zoomed = true))
        val nothing = touchKind(listOf(empty), layerAt, zoomed = false)
        assertEquals("a drag on empty canvas does nothing unless zoomed", TouchKind.Ignored, nothing)
    }

    @Test
    fun aPinchMovesTheLayerUnderAnyFingerTheFirstFingerFirst() {
        val onLayer = Offset(300f, 300f)
        val empty = Offset(10f, 10f)
        val asked = mutableListOf<Offset>()
        val layerAt = { p: Offset -> asked += p; p == onLayer }
        assertEquals(
            "only the second finger is on a layer: the pinch still takes it",
            TouchKind.Layer,
            touchKind(listOf(empty, onLayer), layerAt, zoomed = false)
        )
        assertEquals(listOf(empty, onLayer), asked)
        asked.clear()
        assertEquals(TouchKind.Layer, touchKind(listOf(onLayer, empty), layerAt, zoomed = true))
        assertEquals("the first finger's layer wins; the others aren't asked", listOf(onLayer), asked)
        val none = touchKind(listOf(empty, Offset(20f, 20f)), layerAt, zoomed = false)
        assertEquals("a pinch with no finger on a layer does nothing unless zoomed", TouchKind.Ignored, none)
        assertEquals(TouchKind.View, touchKind(listOf(empty, Offset(20f, 20f)), layerAt, zoomed = true))
    }

    @Test
    fun aSecondTapCountsWithinTheTimeoutAndTheSlop() {
        val first = CanvasTap(Offset(100f, 100f), upAt = 1_000L)
        assertTrue(isSecondTap(first, downAt = 1_300L, at = Offset(110f, 90f), slop = 63f))
        assertFalse("too late", isSecondTap(first, downAt = 1_301L, at = Offset(100f, 100f), slop = 63f))
        assertFalse("too far", isSecondTap(first, downAt = 1_100L, at = Offset(100f, 164f), slop = 63f))
        assertFalse("no first tap", isSecondTap(null, downAt = 1_100L, at = Offset(100f, 100f), slop = 63f))
    }
}
