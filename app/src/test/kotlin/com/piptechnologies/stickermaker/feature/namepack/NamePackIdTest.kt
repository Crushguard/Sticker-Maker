package com.piptechnologies.stickermaker.feature.namepack

import com.piptechnologies.stickermaker.feature.namepack.engine.NamePackId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NamePackIdTest {

    @Test
    fun theSameNamesAndLanguageGiveTheSamePack() {
        assertEquals(NamePackId.of("en", "Aymen", "Sara"), NamePackId.of("en", "Aymen", "Sara"))
    }

    @Test
    fun anyChangeGivesANewPack() {
        val base = NamePackId.of("en", "Aymen", "Sara")
        assertNotEquals(base, NamePackId.of("ar", "Aymen", "Sara"))
        assertNotEquals(base, NamePackId.of("en", "", "Sara"))
        assertNotEquals(base, NamePackId.of("en", "Aymen", "Lea"))
        assertNotEquals(NamePackId.of("en", "ab", "c"), NamePackId.of("en", "a", "bc"))
    }

    @Test
    fun theSchemeIsPinnedByKnownAnswers() {
        // (language, your name, their name): a scheme change would give every pack already in WhatsApp a new id.
        assertEquals("own-np-c3ee7ded2012", NamePackId.of("en", "Aymen", "Sara"))
        assertEquals("own-np-5eef43a22e6a", NamePackId.of("en", "\u0623\u064A\u0645\u0646", "\u0633\u0627\u0631\u0629"))
    }

    @Test
    fun idsAreValidOwnPackIdentifiers() {
        val id = NamePackId.of("en", "أيمن", "سارة")
        assertTrue(id, id.matches(Regex("own-np-[0-9a-f]{12}")))
    }
}
