package com.piptechnologies.stickermaker.feature.namepack

import com.piptechnologies.stickermaker.feature.namepack.engine.NameInput
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NameInputTest {

    @Test
    fun normalizeTrimsAndCollapsesSpaces() {
        assertEquals("Sara Lee", NameInput.normalize("  Sara    Lee "))
        assertEquals("", NameInput.normalize("   "))
    }

    @Test
    fun countsGraphemesNotChars() {
        assertEquals(3, NameInput.graphemeCount("Zoë"))
        assertEquals(4, NameInput.graphemeCount("سارة"))
        assertEquals(0, NameInput.graphemeCount(""))
    }

    @Test
    fun clampCutsAPasteAtFourteenGraphemes() {
        assertEquals("Anastasia-Mari", NameInput.clamp("Anastasia-Maria"))
        assertEquals("Sara", NameInput.clamp("Sara"))
        assertEquals(14, NameInput.graphemeCount(NameInput.clamp("x".repeat(40))))
        assertEquals("Zoë" + "x".repeat(11), NameInput.clamp("Zoë" + "x".repeat(20)))
    }

    @Test
    fun clampAlsoStopsAtSixtyCharsOnAWholeCluster() {
        // Stacked marks: one cluster, six UTF-16 units. Fourteen would make a pack name WhatsApp refuses.
        val stacked = "e" + "\u0301".repeat(5)
        val clamped = NameInput.clamp(stacked.repeat(14))
        assertEquals(stacked.repeat(10), clamped)
        assertEquals(60, clamped.length)
        assertEquals(10, NameInput.graphemeCount(clamped))
        assertEquals("Anastasia-Mari", NameInput.clamp("Anastasia-Mari"))
    }

    @Test
    fun invisibleDirectionAndFormatMarksAreStripped() {
        val sara = "\u0633\u0627\u0631\u0629"
        assertEquals(sara, NameInput.normalize("\u200F" + sara))
        assertTrue(NameInput.isLetterable(NameInput.normalize("\u200F" + sara)))
        assertEquals("Sara", NameInput.normalize("Sa\u200Bra"))
        listOf(
            "\u200E", "\u200F", "\u061C", "\u202A", "\u202B", "\u202C", "\u202D", "\u202E",
            "\u2066", "\u2067", "\u2068", "\u2069", "\u200B", "\u2060", "\uFEFF"
        ).forEach { mark ->
            assertEquals("U+" + Integer.toHexString(mark[0].code), "Sara", NameInput.stripInvisible("Sa" + mark + "ra"))
        }
    }

    @Test
    fun joinersThatShapeLettersAreKept() {
        val persian = "\u0645\u06CC\u200C\u062E\u0648\u0627\u0647\u0645"
        assertEquals(persian, NameInput.stripInvisible(persian))
        assertEquals(persian, NameInput.normalize(persian))
        assertEquals("Sara\u200D", NameInput.stripInvisible("Sara\u200D"))
    }

    @Test
    fun lettersMarksDigitsAndNamePunctuationAreLetterable() {
        listOf(
            "Sara", "Anastasia-Mari", "O'Brien", "O’Brien", "J.Lo", "Zoë", "Sara 2",
            "سارة", "Ɗanjuma", "נועה", "می‌خواهم", "प्रिया", "Саша", "小雨", "မေ"
        ).forEach { assertTrue(it, NameInput.isLetterable(it)) }
    }

    @Test
    fun emojiSymbolsAndOtherPunctuationAreNot() {
        listOf("Sara 😍", "❤", "Sara❤️", "1️⃣", "Sara!", "@sara", "☀", "Sara‍")
            .forEach { assertFalse(it, NameInput.isLetterable(it)) }
    }

    @Test
    fun initialIsTheFirstGraphemeUpperCasedWhereTheScriptHasCase() {
        assertEquals("S", NameInput.initial(" sara", Locale.ENGLISH))
        assertEquals("İ", NameInput.initial("ipek", Locale.forLanguageTag("tr")))
        assertEquals("س", NameInput.initial("سارة", Locale.forLanguageTag("ar")))
        assertEquals("Z", NameInput.initial("zoë", Locale.ENGLISH))
        assertEquals("", NameInput.initial("  ", Locale.ENGLISH))
    }
}
