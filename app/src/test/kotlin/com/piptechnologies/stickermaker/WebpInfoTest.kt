package com.piptechnologies.stickermaker

import com.piptechnologies.stickermaker.whatsapp.WebpInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/** Plain JVM tests for the WebP header parser, against committed pack fixtures. */
class WebpInfoTest {

    @Test
    fun parsesAStaticSticker() {
        val info = WebpInfo.parse(File(PackFixtures.packsDir, "gm-gn/01.webp").readBytes())
        assertEquals(512, info.width)
        assertEquals(512, info.height)
        assertFalse(info.isAnimated)
        assertEquals(1, info.frameCount)
    }

    @Test
    fun parsesAnAnimatedSticker() {
        val info = WebpInfo.parse(File(PackFixtures.packsDir, "heartbeat/01.webp").readBytes())
        assertEquals(512, info.width)
        assertEquals(512, info.height)
        assertTrue(info.isAnimated)
        assertEquals(4, info.frameCount)
        assertEquals(150, info.minFrameDurationMs)
        assertEquals(600, info.totalDurationMs)
    }

    @Test
    fun rejectsNonWebpBytes() {
        val pngBytes = File(PackFixtures.packsDir, "gm-gn/tray.png").readBytes()
        try {
            WebpInfo.parse(pngBytes)
            fail("expected IllegalArgumentException for PNG bytes")
        } catch (expected: IllegalArgumentException) {
            // expected
        }
    }
}
