package com.piptechnologies.stickermaker.feature.create.decor

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.nio.file.Files
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class)
class LayerRenderCacheTest {

    private val data = DecorData.load(ApplicationProvider.getApplicationContext<Context>().assets)
    private val toneDir: File = Files.createTempDirectory("tones").toFile()
    private val assets = DecorAssets({ File("src/main/assets/$it").inputStream() }, toneDir)
    private val painter = TextLayerPainter(DecorFonts(data.styles, DecorTestFonts::load), data.styles) { false }
    private val cache = LayerRenderCache(assets, painter, data)

    /** The heart frame: 0.8 of the canvas wide, the largest piece. */
    private val frame = Layer(1, LayerContent.Decor("props-6.webp", listOf("❤️")), 256f, 256f)
    private val caption = Layer(
        2, LayerContent.Text("miss u", TextStyleId.Sticker, DecorSpec.ROSE, FontMood.Round), 256f, 400f
    )

    /** Half a 2^(1/16) step is 2.2%. */
    private fun assertNear(expected: Float, actual: Float) = assertEquals(expected, actual, expected * 0.023f)

    @Test
    fun cachedBytesStayWithinTheBound() {
        val made = Collections.newSetFromMap(IdentityHashMap<Bitmap, Boolean>())
        repeat(48) { i ->
            val r = cache.render(frame.copy(scale = 0.5f * 1.03f.pow(i)), outlineRadius = 8.9f)
            made += r.bitmap
            made += r.silhouette!!
            assertTrue("${cache.cachedBytes} bytes cached", cache.cachedBytes <= LayerRenderCache.MAX_BYTES)
        }
        assertTrue("the bound was reached", made.sumOf { it.byteCount.toLong() } > LayerRenderCache.MAX_BYTES)
    }

    @Test
    fun renderScaleIsWithinHalfAStepAtEverySize() {
        listOf(0.1f, 0.33f, 1f, 2.7f).forEach { s ->
            assertNear(s, cache.render(frame.copy(scale = s), null).renderScale)
        }
    }

    @Test
    fun liveGesturesReuseTheNearestCachedSize() {
        val start = cache.render(frame, null)
        val live = cache.render(frame.copy(scale = 1.3f), null, live = true)
        assertSame("no new render mid-gesture", start.bitmap, live.bitmap)
        assertEquals(start.renderScale, live.renderScale, 0f)
        val ended = cache.render(frame.copy(scale = 1.3f), null)
        assertNotSame(start.bitmap, ended.bitmap)
        assertNear(1.3f, ended.renderScale)
        // Nothing cached yet for this content: even live, it renders at its own size.
        val fresh = cache.render(caption.copy(scale = 0.7f), null, live = true)
        assertNear(0.7f, fresh.renderScale)
    }

    @Test
    fun outlineChangesKeepTheContent() {
        listOf(frame, caption).forEach { layer ->
            val thin = cache.render(layer, 5.6f)
            val thick = cache.render(layer, 13.4f)
            val off = cache.render(layer, null)
            assertSame(thin.bitmap, thick.bitmap)
            assertSame(thin.bitmap, off.bitmap)
            assertNotSame(thin.silhouette, thick.silhouette)
            assertTrue(thick.silhouette!!.width > thin.silhouette!!.width)
            assertNull(off.silhouette)
            assertSame("a silhouette is cached too", thick.silhouette, cache.render(layer, 13.4f).silhouette)
        }
    }

    @Test
    fun nonFiniteScaleRendersAtFullSize() {
        assertEquals(1f, cache.render(frame.copy(scale = Float.NaN), null).renderScale, 0f)
        assertEquals(1f, cache.render(frame.copy(scale = Float.POSITIVE_INFINITY), null).renderScale, 0f)
    }
}
