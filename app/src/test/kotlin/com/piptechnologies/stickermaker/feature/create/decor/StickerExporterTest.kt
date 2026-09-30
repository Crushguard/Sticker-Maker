package com.piptechnologies.stickermaker.feature.create.decor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import com.piptechnologies.stickermaker.feature.create.StickerRenderer
import com.piptechnologies.stickermaker.whatsapp.StickerPackValidator
import com.piptechnologies.stickermaker.whatsapp.WebpInfo
import java.io.File
import java.nio.file.Files
import kotlin.random.Random
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * What each kind of sticker exports to (spec §5, §7), from the scene the live canvas draws, within
 * WhatsApp's limits: 512 px WebP, static ≤ 100 KB, animated ≤ 500 KB with every frame ≥ 8 ms, and a
 * 96 px PNG tray ≤ 50 KB.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class)
class StickerExporterTest {

    private val data = DecorData.load(ApplicationProvider.getApplicationContext<Context>().assets)
    private val toneDir: File = Files.createTempDirectory("tones").toFile()
    private val assets = DecorAssets({ File("src/main/assets/$it").inputStream() }, toneDir)
    private val painter = TextLayerPainter(DecorFonts(data.styles, DecorTestFonts::load), data.styles) { false }
    private val renderer = SceneRenderer(LayerRenderCache(assets, painter, data), data)
    private val exporter = StickerExporter(renderer)

    /** The subject, and its own mask: a 400 px rose disc centred on the canvas. */
    private val disc: Bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).also {
        Canvas(it).drawCircle(256f, 256f, 200f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = DecorSpec.ROSE })
    }

    /** The one decor layer: a crown on the disc's head. */
    private val crown = Layer(1, LayerContent.Decor("doodles-1.webp", listOf("👑")), 256f, 70f)

    @After
    fun deleteTones() {
        toneDir.deleteRecursively()
    }

    /** [subject] cut out by [mask], wearing the crown, inside the default white outline. */
    private fun scene(subject: Bitmap = disc, mask: Bitmap = disc): SceneRenderer.Scene {
        val outline = OutlineStyle()
        val silhouette = StickerRenderer.outlineOf(mask, renderer.outlineRadius(outline.thickness))
        return SceneRenderer.Scene(subject, silhouette, DecorState(listOf(crown), outline))
    }

    @Test
    fun staticIsStillAndSmall() {
        val bytes = exporter.staticSticker(scene())
        val info = WebpInfo.parse(bytes)
        assertFalse(info.isAnimated)
        assertTrue("${bytes.size} bytes", bytes.size <= 100 * 1024)
        assertEquals(512 to 512, info.width to info.height)
    }

    @Test
    fun presetHasItsFramesAndDuration() {
        val bytes = exporter.presetSticker(scene(), data.motion.byId("heartbeat"))
        val info = WebpInfo.parse(bytes)
        assertTrue(info.isAnimated)
        assertEquals(12, info.frameCount)
        assertEquals(1000, info.totalDurationMs)
        assertTrue("${info.minFrameDurationMs} ms", info.minFrameDurationMs >= 8)
        assertTrue("${bytes.size} bytes", bytes.size <= 500 * 1024)
        assertEquals(512 to 512, info.width to info.height)
    }

    @Test
    fun stillInAnimatedPackIsTwoFrames() {
        val info = WebpInfo.parse(exporter.stillInAnimatedPack(scene()))
        assertTrue(info.isAnimated)
        assertEquals(2, info.frameCount)
        assertEquals("two frames of 500 ms", 500 to 1000, info.minFrameDurationMs to info.totalDurationMs)
        assertEquals(512 to 512, info.width to info.height)
        val none = WebpInfo.parse(exporter.presetSticker(scene(), data.motion.byId(MotionPreset.NONE)))
        assertEquals("no motion is a still", 2, none.frameCount)
    }

    @Test
    fun noisyPresetStaysUnderLimit() {
        val random = Random(7)
        val pixels = IntArray(512 * 512) { 0xFF000000.toInt() or random.nextInt(0x1000000) }
        val noise = Bitmap.createBitmap(pixels, 512, 512, Bitmap.Config.ARGB_8888)
        val bytes = exporter.presetSticker(scene(noise, StickerRenderer.fullMask()), data.motion.byId("sparkle"))
        val info = WebpInfo.parse(bytes)
        assertTrue("${bytes.size} bytes", bytes.size <= 500 * 1024)
        assertTrue("${info.frameCount} frames", info.frameCount >= 4)
        assertEquals("fewer frames, the same loop", 1500, info.totalDurationMs)
        assertTrue("${info.minFrameDurationMs} ms", info.minFrameDurationMs >= 8)
    }

    @Test
    fun everyOtherFrameKeepsTheRestPoseAndTheLoopLength() {
        val times = MotionMath.frameDurations(data.motion.byId("sparkle"))
        val (frames, halved) = StickerExporter.everyOtherFrame((0 until 16).toList(), times)
        assertEquals("frame 0 stays first", listOf(0, 2, 4, 6, 8, 10, 12, 14), frames)
        assertEquals(List(8) { times[2 * it] + times[2 * it + 1] }, halved)
        assertEquals(1500, halved.sum())
        // An odd count: the last frame goes, and its time with the final pair.
        assertEquals(
            listOf(0, 2) to listOf(30, 120),
            StickerExporter.everyOtherFrame(listOf(0, 1, 2, 3, 4), listOf(10, 20, 30, 40, 50))
        )
    }

    @Test
    fun clipFramesShareItsLength() {
        val info = WebpInfo.parse(exporter.clipSticker(List(4) { scene() }, totalDurationMs = 2000))
        assertEquals(4, info.frameCount)
        assertEquals(500 to 2000, info.minFrameDurationMs to info.totalDurationMs)
        assertEquals(512 to 512, info.width to info.height)
        val single = WebpInfo.parse(exporter.clipSticker(listOf(scene()), totalDurationMs = 3000))
        assertEquals("one frame is a still in an animated pack", 2, single.frameCount)
    }

    @Test
    fun trayIsA96PngUnder50Kb() {
        val bytes = exporter.tray(scene())
        assertTrue(StickerPackValidator.isPng(bytes))
        assertEquals(96 to 96, StickerPackValidator.pngDimensions(bytes))
        assertTrue("${bytes.size} bytes", bytes.size <= 50 * 1024)
    }

    @Test
    fun theScenesBitmapsStayTheCallers() {
        val scene = scene()
        exporter.staticSticker(scene)
        exporter.presetSticker(scene, data.motion.byId("hearts"))
        exporter.stillInAnimatedPack(scene)
        exporter.clipSticker(listOf(scene, scene), totalDurationMs = 1000)
        exporter.tray(scene)
        assertFalse("the subject", disc.isRecycled)
        assertFalse("the silhouette", checkNotNull(scene.subjectSilhouette).isRecycled)
    }
}
