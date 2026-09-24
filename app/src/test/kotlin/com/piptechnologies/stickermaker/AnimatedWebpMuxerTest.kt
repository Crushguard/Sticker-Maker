package com.piptechnologies.stickermaker

import com.piptechnologies.stickermaker.whatsapp.AnimatedWebpMuxer
import com.piptechnologies.stickermaker.whatsapp.WebpInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/** Plain JVM tests for the animated WebP writer: mux static fixtures, re-parse the result. */
class AnimatedWebpMuxerTest {

    @Test
    fun muxesTwoStaticFramesIntoAnAnimatedWebp() {
        val frame1 = File(PackFixtures.packsDir, "gm-gn/01.webp").readBytes()
        val frame2 = File(PackFixtures.packsDir, "gm-gn/02.webp").readBytes()

        val animated = AnimatedWebpMuxer.mux(listOf(frame1, frame2), listOf(100, 200))

        val info = WebpInfo.parse(animated)
        assertTrue("output should carry the ANIM flag", info.isAnimated)
        assertEquals(2, info.frameCount)
        assertEquals("shortest frame duration", 100, info.minFrameDurationMs)
        assertEquals("sum of the frame durations", 300, info.totalDurationMs)
        assertEquals(512, info.width)
        assertEquals(512, info.height)
        assertTrue("alpha flag should be set", info.hasAlpha)
    }

    @Test
    fun rejectsMismatchedDurations() {
        val frame = File(PackFixtures.packsDir, "gm-gn/01.webp").readBytes()
        try {
            AnimatedWebpMuxer.mux(listOf(frame), listOf(100, 200))
            fail("expected IllegalArgumentException for mismatched durations")
        } catch (expected: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun rejectsEmptyInput() {
        try {
            AnimatedWebpMuxer.mux(emptyList(), emptyList())
            fail("expected IllegalArgumentException for empty input")
        } catch (expected: IllegalArgumentException) {
            // expected
        }
    }
}
