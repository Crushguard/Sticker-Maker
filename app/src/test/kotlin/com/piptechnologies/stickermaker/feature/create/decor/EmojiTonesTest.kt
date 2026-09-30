package com.piptechnologies.stickermaker.feature.create.decor

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmojiTonesTest {
    private val wave = EmojiItem("Waving hand", "1F44B", "👋", "waving_hand.webp", true)
    private val dir: File = Files.createTempDirectory("tones").toFile()

    /** The 8-byte PNG signature, then a few bytes: enough for the check (the tones are PNGs). */
    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3)

    @Test
    fun downloadsOnceThenServesTheCache() = runBlocking {
        var calls = 0
        val tones = EmojiTones(dir) { url ->
            calls++
            assertEquals(EmojiCatalog.toneUrl(wave, SkinTone.Dark), url)
            png
        }
        val f = tones.ensure(wave, SkinTone.Dark)!!
        assertEquals("waving_hand_dark.png", f.name)
        assertEquals(f, tones.ensure(wave, SkinTone.Dark))
        assertEquals(1, calls)
        assertEquals(f, tones.cached(wave, SkinTone.Dark))
    }

    @Test
    fun onlyTheFinishedToneIsLeftAndAnEmptyBodyFails() = runBlocking {
        assertNull(EmojiTones(dir) { ByteArray(0) }.ensure(wave, SkinTone.Medium))
        assertEquals(0, dir.listFiles()!!.size)
        EmojiTones(dir) { png }.ensure(wave, SkinTone.Medium)
        assertEquals(listOf("waving_hand_medium.png"), dir.list()!!.toList())   // the .part file was renamed
    }

    @Test
    fun aBodyThatIsNotAPngFailsLikeAnError() = runBlocking {
        // A captive portal answers 200 with its own page: it must not be kept as the tone.
        val portal = "<html><body>Sign in to the Wi-Fi</body></html>".toByteArray()
        val tones = EmojiTones(dir) { portal }
        assertNull(tones.ensure(wave, SkinTone.Dark))
        assertNull(tones.cached(wave, SkinTone.Dark))
        assertEquals(0, dir.listFiles()!!.size)
        val short = EmojiTones(dir) { png.copyOf(7) }
        assertNull("a short body can't hold the signature", short.ensure(wave, SkinTone.Dark))
    }

    @Test
    fun partFilesLeftByAnEarlierRunAreSwept() {
        val stale = File(dir, "waving_hand_dark.png123.part").apply { writeBytes(png) }
        val kept = File(dir, "waving_hand_light.png").apply { writeBytes(png) }
        val tones = EmojiTones(dir) { null }
        assertFalse(stale.exists())
        assertTrue(kept.exists())
        assertEquals(kept, tones.cached(wave, SkinTone.Light))
    }

    @Test
    fun failureReturnsNullAndCachesNothing() = runBlocking {
        val tones = EmojiTones(dir) { null }
        assertNull(tones.ensure(wave, SkinTone.Light))
        assertNull(tones.cached(wave, SkinTone.Light))
        assertEquals(0, dir.listFiles()!!.size)
    }
}
