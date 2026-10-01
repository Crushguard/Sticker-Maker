package com.piptechnologies.stickermaker.core.data.prefs

import org.junit.Assert.assertEquals
import org.junit.Test

class EmojiRecentsTest {

    @Test
    fun recentsAreMostRecentFirstDeduplicatedAndCapped() {
        var stored: String? = null
        assertEquals(emptyList<String>(), PrefsRepository.recentsOf(stored))
        listOf("a", "b", "c").forEach { stored = PrefsRepository.withRecent(stored, "$it.webp") }
        assertEquals("c.webp\nb.webp\na.webp", stored)
        stored = PrefsRepository.withRecent(stored, "a.webp")
        assertEquals(listOf("a.webp", "c.webp", "b.webp"), PrefsRepository.recentsOf(stored))
        repeat(30) { stored = PrefsRepository.withRecent(stored, "e$it.webp") }
        val recents = PrefsRepository.recentsOf(stored)
        assertEquals(18, recents.size)
        assertEquals("e29.webp", recents.first())
        assertEquals("e12.webp", recents.last())
    }
}
