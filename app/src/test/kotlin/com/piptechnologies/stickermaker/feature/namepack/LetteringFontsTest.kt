package com.piptechnologies.stickermaker.feature.namepack

import com.piptechnologies.stickermaker.feature.namepack.engine.LetteringFont
import com.piptechnologies.stickermaker.feature.namepack.engine.LetteringFonts
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LetteringFontsTest {

    @Test
    fun latinDevanagariAndTurkishUseBaloo() {
        listOf("Love you, Sara", "प्यार है तुमसे, प्रिया", "İyi geceler, Şule", "Sumba daga gare ni, Ɗanjuma")
            .forEach { assertEquals(it, LetteringFont.BALOO, LetteringFonts.choose(it)) }
    }

    @Test
    fun arabicScriptUsesBhaijaanEvenInsideAnEnglishPhrase() {
        listOf("أحبك يا سارة", "Love you, سارة", "تم سے پیار ہے، سارہ", "دوستت دارم، سارا")
            .forEach { assertEquals(it, LetteringFont.BALOO_BHAIJAAN, LetteringFonts.choose(it)) }
    }

    @Test
    fun pashtoLettersBhaijaanLacksMoveTheWholeStringToTheSystemFont() {
        assertEquals(LetteringFont.SYSTEM_BOLD, LetteringFonts.choose("یو ښکل ستا لپاره، زرمینه"))
        assertEquals(LetteringFont.BALOO_BHAIJAAN, LetteringFonts.choose("سلام، زرمینه"))
    }

    @Test
    fun hebrewAndCyrillicUseRubik() {
        assertEquals(LetteringFont.RUBIK, LetteringFonts.choose("אהבה שלי, נועה"))
        assertEquals(LetteringFont.RUBIK, LetteringFonts.choose("Люблю тебя, Саша"))
    }

    @Test
    fun rtlLanguagesAlwaysLetterRightToLeftOthersFollowTheFirstStrongCharacter() {
        assertTrue(LetteringFonts.isRtlParagraph("Sara کے لیے", rtlLanguage = true))
        assertFalse(LetteringFonts.isRtlParagraph("Love you, سارة", rtlLanguage = false))
        assertTrue(LetteringFonts.isRtlParagraph("سارة", rtlLanguage = false))
        assertFalse(LetteringFonts.isRtlParagraph("12 ♥", rtlLanguage = false))
    }

    @Test
    fun bundledFontsCoverTheirScripts() {
        val baloo = font("lettering_baloo2.ttf")
        val bhaijaan = font("lettering_baloo_bhaijaan2.ttf")
        val rubik = font("lettering_rubik.ttf")
        assertCovers("Baloo 2", baloo, "Love you, Sara İyi geceler Şule Zoë Ñandú ß प्यार है तुमसे क्षत्रज्ञ")
        assertCovers("Baloo Bhaijaan 2", bhaijaan, "أحبك يا سارة دوستت دارم پچژگک تم سے پیار ہے ٹڈڑںہھے ۱۲٣ Love you، ؟")
        assertCovers("Rubik", rubik, "אהבה שלי נועה Люблю тебя Саша Ёё Love you")
        LetteringFonts.PASHTO_GAPS.forEach {
            assertFalse("Bhaijaan now has U+%04X: shrink PASHTO_GAPS".format(it), bhaijaan.covers(it))
        }
    }

    /** Keeps PASHTO_GAPS complete: an Arabic-script letter in a shipped phrase that Bhaijaan lacks must move its phrase to the system face. */
    @Test
    fun everyArabicScriptLetterBhaijaanLacksIsAGap() {
        val bhaijaan = font("lettering_baloo_bhaijaan2.ttf")
        File("src/main/assets/templates/phrases.json").readText().codePoints().distinct()
            .filter { it in 0x0600..0x06FF && Character.isLetter(it) && !bhaijaan.covers(it) }
            .forEach { assertTrue("Bhaijaan lacks U+%04X, used in a phrase: add it to PASHTO_GAPS".format(it), it in LetteringFonts.PASHTO_GAPS) }
    }

    private fun font(name: String): CmapCoverage = CmapCoverage.of(File("src/main/res/font/$name"))

    private fun assertCovers(name: String, font: CmapCoverage, text: String) {
        text.codePoints().filter { !Character.isWhitespace(it) }.forEach {
            assertTrue("$name lacks U+%04X".format(it), font.covers(it))
        }
    }
}
