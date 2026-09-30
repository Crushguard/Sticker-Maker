package com.piptechnologies.stickermaker.feature.create.decor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import androidx.test.core.app.ApplicationProvider
import com.piptechnologies.stickermaker.feature.create.StickerRenderer
import java.io.File
import java.nio.file.Files
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class)
class SceneRendererTest {

    private val data = DecorData.load(ApplicationProvider.getApplicationContext<Context>().assets)
    private val toneDir: File = Files.createTempDirectory("tones").toFile()
    private val assets = DecorAssets({ File("src/main/assets/$it").inputStream() }, toneDir)
    private val painter = TextLayerPainter(DecorFonts(data.styles, DecorTestFonts::load), data.styles) { false }
    private val cache = LayerRenderCache(assets, painter, data)
    private val renderer = SceneRenderer(cache, data)

    /** The synthetic subject: a 200 px rose disc centred on the canvas. */
    private val disc: Bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).also {
        Canvas(it).drawCircle(256f, 256f, 100f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = DecorSpec.ROSE })
    }
    private val ink = data.styles.outlineColours.first { it.id == "ink" }.argb

    private fun scene(
        layers: List<Layer>,
        outline: OutlineStyle = OutlineStyle(),
        live: List<MarkerStroke> = emptyList()
    ): SceneRenderer.Scene {
        val silhouette = StickerRenderer.outlineOf(disc, renderer.outlineRadius(outline.thickness))
        return SceneRenderer.Scene(disc, silhouette, DecorState(layers, outline), live)
    }

    private val heart = Layer(1, LayerContent.Emoji("red_heart.webp", "❤️"), 80f, 80f, scale = 0.5f)

    @Test
    fun outlineWrapsLayersToo() {
        val on = renderer.renderStill(scene(listOf(heart), OutlineStyle(true, OutlineThickness.Thick, ink)))
        val off = renderer.renderStill(scene(listOf(heart), OutlineStyle(on = false)))
        val y = 80
        val left = (0 until 256).first { Pixels.alpha(off.getPixel(it, y)) > 0 }
        assertTrue("the emoji's edge is inside its box", left > 30)
        assertTrue("ink outline 2 px outside the emoji", Pixels.within(on.getPixel(left - 2, y), ink, 30))
        assertEquals("nothing there without the outline", 0, Pixels.alpha(off.getPixel(left - 2, y)))
    }

    @Test
    fun textOutlineWrapsTheLettersNotTheirGlow() {
        // Classic: an ink drop and a 12 px glow; the die-cut must hug the letters and the drop (spec §5).
        val classic = LayerContent.Text("miss u", TextStyleId.Classic, Color.WHITE, FontMood.Round)
        val caption = Layer(5, classic, 256f, 256f)
        fun render(outline: OutlineStyle) = renderer.renderStill(
            SceneRenderer.Scene(null, null, DecorState(listOf(caption), outline), emptyList())
        )
        val off = render(OutlineStyle(on = false))
        val on = render(OutlineStyle(true, OutlineThickness.Thick, ink))
        val solid = opaqueBounds(off)
        assertTrue("the outline is there", opaqueBounds(on).width() > solid.width() + 20)
        // Past the letters' reach (radius + AA), a crisp die-cut adds nothing: only the glow is left there.
        val reach = renderer.outlineRadius(OutlineThickness.Thick).toInt() + 3
        // bounds() is inclusive; Rect.contains excludes right and bottom, hence the + 1.
        val crisp = Rect(solid.left - reach, solid.top - reach, solid.right + reach + 1, solid.bottom + reach + 1)
        var fuzz = 0
        for (y in 0 until 512) for (x in 0 until 512) {
            if (!crisp.contains(x, y) && Pixels.alpha(on.getPixel(x, y)) > Pixels.alpha(off.getPixel(x, y)) + 10) fuzz++
        }
        assertEquals("outline pixels past the die-cut", 0, fuzz)
    }

    @Test
    fun behindLayersHideUnderTheSubject() {
        val sun = Layer(2, LayerContent.Decor("doodles-23.webp", listOf("☀️")), 256f, 256f)
        val centre = disc.getPixel(256, 256)
        assertEquals(centre, renderer.renderStill(scene(listOf(sun.copy(behind = true)))).getPixel(256, 256))
        assertNotEquals(centre, renderer.renderStill(scene(listOf(sun))).getPixel(256, 256))
    }

    @Test
    fun dimmedLayersAreFortyPercent() {
        val out = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        renderer.drawSticker(Canvas(out), scene(listOf(heart), OutlineStyle(on = false)), dimLayers = true)
        assertEquals(102f, Pixels.alpha(out.getPixel(80, 80)).toFloat(), 3f)
        assertEquals("the subject stays opaque", 255, Pixels.alpha(out.getPixel(256, 256)))
    }

    @Test
    fun liveGestureDrawsTheCachedSizeAtTheLayersScale() {
        fun alone(layer: Layer) = SceneRenderer.Scene(null, null, DecorState(listOf(layer), OutlineStyle(on = false)))
        renderer.renderStill(alone(heart))
        val bytes = cache.cachedBytes
        val grown = heart.copy(scale = 0.6f)
        val live = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        renderer.drawSticker(Canvas(live), alone(grown), liveLayerId = heart.id)
        assertEquals("nothing rendered mid-gesture", bytes, cache.cachedBytes)
        val exact = renderer.renderStill(alone(grown))
        assertTrue("the exact size renders once the gesture ends", cache.cachedBytes > bytes)
        val a = bounds(live, minAlpha = 128)
        val b = bounds(exact, minAlpha = 128)
        listOf(a.left - b.left, a.top - b.top, a.right - b.right, a.bottom - b.bottom).forEach {
            assertTrue("$a vs $b", abs(it) <= 2)
        }
    }

    @Test
    fun flippedLayerMirrors() {
        val arrow = Layer(3, LayerContent.Decor("props-7.webp", listOf("💘")), 256f, 256f)
        assertEquals(Size2(256f, 256f * 218f / 300f), cache.baseSize(arrow.content))
        fun render(layer: Layer) = renderer.renderStill(
            SceneRenderer.Scene(null, null, DecorState(listOf(layer), OutlineStyle(on = false)), emptyList())
        )
        val plain = alphaSums(render(arrow))
        val flipped = alphaSums(render(arrow.copy(flipped = true)))
        assertEquals(plain.left.toDouble(), flipped.right.toDouble(), 0.05 * plain.left)
        assertEquals(plain.right.toDouble(), flipped.left.toDouble(), 0.05 * plain.right)
        // The heart-tipped head points up-right; flipped, it points up-left.
        assertTrue(plain.topRight > 1.2 * plain.topLeft)
        assertTrue(flipped.topLeft > 1.2 * flipped.topRight)
    }

    @Test
    fun liveStrokesHaveAnInkEdge() {
        val stroke = MarkerStroke(listOf(Pt(100f, 400f), Pt(400f, 400f)), DecorSpec.ROSE, MarkerSize.M)
        val tap = MarkerStroke(listOf(Pt(60f, 60f)), DecorSpec.ROSE, MarkerSize.M)
        val out = renderer.renderStill(scene(emptyList(), live = listOf(stroke, tap)))
        assertTrue("rose", Pixels.near(out.getPixel(250, 400), DecorSpec.ROSE, 40))
        assertTrue("ink edge", Pixels.within(out.getPixel(250, 400 - 7 - 2), ink, 30))
        // The (white) outline wraps the live stroke as well.
        assertEquals(Color.WHITE, out.getPixel(250, 400 - 7 - 3 - 5))
        // A tap leaves a round dot with its ink edge.
        assertTrue("dot", Pixels.near(out.getPixel(60, 60), DecorSpec.ROSE, 40))
        assertTrue("dot edge", Pixels.within(out.getPixel(60, 60 - 7 - 2), ink, 30))
    }

    @Test
    fun drawingLayerLooksLikeItsLiveStrokes() {
        val stroke = MarkerStroke(listOf(Pt(100f, 400f), Pt(250f, 380f), Pt(400f, 400f)), DecorSpec.ROSE, MarkerSize.M)
        val live = renderer.renderStill(scene(emptyList(), OutlineStyle(on = false), live = listOf(stroke)))
        // As flushed: points relative to the centre of their box, (250, 390).
        val relative = stroke.copy(points = stroke.points.map { Pt(it.x - 250f, it.y - 390f) })
        val drawing = LayerContent.Drawing(listOf(relative))
        assertEquals(Size2(300f + 20f, 20f + 20f), cache.baseSize(drawing))
        val layer = renderer.renderStill(scene(listOf(Layer(4, drawing, 250f, 390f)), OutlineStyle(on = false)))
        val bounds = Rect(90, 360, 420, 420)
        var differing = 0
        for (y in bounds.top until bounds.bottom) for (x in bounds.left until bounds.right) {
            if (abs(Pixels.alpha(live.getPixel(x, y)) - Pixels.alpha(layer.getPixel(x, y))) > 40) differing++
        }
        assertTrue("$differing pixels differ", differing < 60)
        val top = (bounds.top until bounds.bottom).first { Pixels.alpha(layer.getPixel(250, it)) > 200 }
        assertTrue("ink edge above the colour", Pixels.within(layer.getPixel(250, top + 1), ink, 30))
    }

    @Test
    fun framesMoveAndParticlesShow() {
        val rest = renderer.renderStill(scene(emptyList()))
        val heartbeat = data.motion.byId("heartbeat")
        val frame0 = bounds(renderer.renderFrame(rest, heartbeat, 0))
        val still = bounds(rest)
        fun scaled(v: Int) = 256f + 0.88f * (v - 256f)
        assertEquals(scaled(still.left), frame0.left.toFloat(), 3f)
        assertEquals(scaled(still.top), frame0.top.toFloat(), 3f)
        assertEquals(scaled(still.right), frame0.right.toFloat(), 3f)
        assertEquals(scaled(still.bottom), frame0.bottom.toFloat(), 3f)
        val beat = bounds(renderer.renderFrame(rest, heartbeat, (0.14f * 12).roundToInt()))
        assertTrue(beat.width() > frame0.width())

        val low = (0.7f * 512).toInt()
        fun rosesBelow(b: Bitmap) = (low until 512).sumOf { y ->
            (0 until 512).count { x -> Pixels.near(b.getPixel(x, y), DecorSpec.ROSE, 40) }
        }
        assertEquals("the disc stays above", 0, rosesBelow(rest))
        assertTrue("rising hearts", rosesBelow(renderer.renderFrame(rest, data.motion.byId("hearts"), 5)) > 20)
    }

    @Test
    fun maskedSubjectKeepsTheSourceInsideTheMask() {
        val source = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        val mask = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        Canvas(mask).drawRect(100f, 100f, 200f, 200f, Paint().apply { color = Color.WHITE })
        val out = StickerRenderer.maskedSubject(source, mask)
        assertEquals(512, out.width)
        assertEquals(Color.BLUE, out.getPixel(150, 150))
        assertEquals(0, out.getPixel(50, 50))
    }

    @Test
    fun maskBoundsIsTheBoxOfTheKeptPixels() {
        val mask = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        assertEquals(null, StickerRenderer.maskBounds(mask))
        Canvas(mask).drawRect(100f, 120f, 300f, 400f, Paint().apply { color = Color.WHITE })
        Canvas(mask).drawRect(10f, 10f, 20f, 20f, Paint().apply { color = Color.argb(128, 255, 255, 255) })
        // The half-transparent speck (alpha 128) is not the subject; the box covers the kept pixels.
        assertEquals(Box(100f, 120f, 300f, 400f), StickerRenderer.maskBounds(mask))
    }

    @Test
    fun patchSubjectRedrawsOnlyTheStrokesArea() {
        val source = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        val mask = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        Canvas(mask).drawRect(100f, 100f, 200f, 200f, Paint().apply { color = Color.WHITE })
        val subject = StickerRenderer.maskedSubject(source, mask)
        // An erase across the square's middle and a keep outside it; only the erase is patched.
        StickerRenderer.drawStrokeSegment(mask, keep = false, radiusPx = 10f, PointF(90f, 150f), PointF(210f, 150f))
        StickerRenderer.drawStrokeSegment(mask, keep = true, radiusPx = 10f, PointF(400f, 400f), PointF(400f, 400f))
        StickerRenderer.patchSubject(subject, source, mask, PointF(90f, 150f), PointF(210f, 150f), radiusPx = 10f)
        assertEquals(0, subject.getPixel(150, 150))                                 // erased, live
        assertEquals(Color.BLUE, subject.getPixel(150, 120))                        // kept
        assertEquals("outside the patch nothing changed yet", 0, subject.getPixel(400, 400))
        val full = StickerRenderer.maskedSubject(source, mask)
        for (x in 85..215) assertEquals("x=$x", full.getPixel(x, 150), subject.getPixel(x, 150))
    }

    @Test
    fun tonesComeFromTheDownloadsAndFallBackToTheBundledArt() {
        assertEquals("waving_hand_medium-light.png", DecorAssets.toneFileName("waving_hand.webp", SkinTone.MediumLight))
        val bundled = assets.emoji("waving_hand.webp", SkinTone.Dark)
        assertEquals("no download yet: the default art", 256, bundled?.width)
        File(toneDir, "waving_hand_dark.png").outputStream().use {
            val red = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
            red.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        val dark = assets.emoji("waving_hand.webp", SkinTone.Dark)
        assertEquals(16, dark?.width)
        assertEquals(Color.RED, dark?.getPixel(8, 8))
        assertEquals(218f / 300f, assets.aspect("decor/props-7.webp"), 1e-6f)
    }

    private class AlphaSums(val left: Long, val right: Long, val topLeft: Long, val topRight: Long)

    private fun alphaSums(b: Bitmap): AlphaSums {
        var left = 0L
        var right = 0L
        var topLeft = 0L
        var topRight = 0L
        for (y in 0 until 512) for (x in 0 until 512) {
            val a = Pixels.alpha(b.getPixel(x, y)).toLong()
            if (x < 256) left += a else right += a
            if (y < 256 && x < 256) topLeft += a
            if (y < 256 && x >= 256) topRight += a
        }
        return AlphaSums(left, right, topLeft, topRight)
    }

    /** The bounding box of pixels above [minAlpha] (right and bottom inclusive). */
    private fun bounds(b: Bitmap, minAlpha: Int = 0): Rect {
        val box = Rect(Int.MAX_VALUE, Int.MAX_VALUE, -1, -1)
        for (y in 0 until b.height) for (x in 0 until b.width) {
            if (Pixels.alpha(b.getPixel(x, y)) > minAlpha) {
                box.left = minOf(box.left, x)
                box.top = minOf(box.top, y)
                box.right = maxOf(box.right, x)
                box.bottom = maxOf(box.bottom, y)
            }
        }
        return box
    }

    /** Solid pixels only: the letters and the hard drop, not the soft glow (at most 35% alpha). */
    private fun opaqueBounds(b: Bitmap): Rect = bounds(b, minAlpha = 200)
}
