package com.piptechnologies.stickermaker.feature.create.decor

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EmojiTonesTest {
    private val wave = EmojiItem("Waving hand", "1F44B", "👋", "waving_hand.webp", true)
    private val dir: File = Files.createTempDirectory("tones").toFile()

    @Test
    fun downloadsOnceThenServesTheCache() = runBlocking {
        var calls = 0
        val tones = EmojiTones(dir) { url -> calls++; assertEquals(EmojiCatalog.toneUrl(wave, SkinTone.Dark), url); byteArrayOf(1, 2, 3) }
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
        EmojiTones(dir) { byteArrayOf(9) }.ensure(wave, SkinTone.Medium)
        assertEquals(listOf("waving_hand_medium.png"), dir.list()!!.toList())   // the .part file was renamed
    }

    @Test
    fun failureReturnsNullAndCachesNothing() = runBlocking {
        val tones = EmojiTones(dir) { null }
        assertNull(tones.ensure(wave, SkinTone.Light))
        assertNull(tones.cached(wave, SkinTone.Light))
        assertEquals(0, dir.listFiles()!!.size)
    }
}
