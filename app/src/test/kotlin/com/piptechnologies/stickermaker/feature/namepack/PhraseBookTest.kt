package com.piptechnologies.stickermaker.feature.namepack

import com.piptechnologies.stickermaker.feature.language.AppLanguages
import com.piptechnologies.stickermaker.feature.namepack.engine.PhraseBook
import com.piptechnologies.stickermaker.feature.namepack.engine.Relation
import com.piptechnologies.stickermaker.feature.namepack.engine.Slot
import com.piptechnologies.stickermaker.feature.namepack.engine.Tone
import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class PhraseBookTest {

    private val book = PhraseBook.parse(File("src/main/assets/templates/phrases.json").readText())

    @Test
    fun everyAppLanguageHasItsOwnCompletePhrases() {
        AppLanguages.entries.forEach { lang ->
            assertEquals(lang.tag, book.languageFor(lang.tag))
            Tone.entries.forEach { tone ->
                Slot.entries.forEach { slot ->
                    val phrase = book.raw(lang.tag, tone.id, slot)
                    assertNotNull("${lang.tag}/${tone.id}/${slot.key} missing", phrase)
                    assertTrue("${lang.tag}/${tone.id}/${slot.key} names the love", phrase!!.contains("{n}"))
                    assertEquals(
                        "${lang.tag}/${tone.id}/${slot.key}: own name only in our_names",
                        slot == Slot.OUR_NAMES, phrase.contains("{a}")
                    )
                }
            }
            listOf(Slot.KISS, Slot.HUG, Slot.BE_MINE).forEach { slot ->
                assertNotNull("${lang.tag}/family/${slot.key} missing", book.raw(lang.tag, "family", slot))
                assertNotEquals(
                    "${lang.tag}: family ${slot.key} differs from sweet",
                    book.template(lang.tag, Tone.SWEET, Relation.GIRLFRIEND, slot),
                    book.template(lang.tag, Tone.SWEET, Relation.MOM, slot)
                )
            }
        }
    }

    @Test
    fun everyPhraseHasOneTheirNameAtMostOneYourNameAndNoOtherBraces() {
        val root = JSONObject(File("src/main/assets/templates/phrases.json").readText())
        val bad = mutableListOf<String>()
        root.keys().asSequence().filterNot { it.startsWith("_") }.forEach { lang ->
            val sets = root.getJSONObject(lang)
            sets.keys().forEach { set ->
                val phrases = sets.getJSONObject(set)
                phrases.keys().forEach { key ->
                    val phrase = phrases.getString(key)
                    val rest = phrase.replace("{n}", "").replace("{a}", "")
                    val names = phrase.split("{n}").size - 1
                    val ownNames = phrase.split("{a}").size - 1
                    if (names != 1 || ownNames > 1 || '{' in rest || '}' in rest) bad += "$lang/$set/$key: $phrase"
                }
            }
        }
        assertTrue("Phrases with a stray or missing placeholder:\n" + bad.joinToString("\n"), bad.isEmpty())
    }

    @Test
    fun lettersNamesIntoPhrases() {
        assertEquals("Love you, Sara", book.text("en", Tone.SWEET, Relation.GIRLFRIEND, Slot.LOVE_YOU, "Aymen", "Sara"))
        assertEquals("Aymen ❤ Sara", book.text("en", Tone.FLIRTY, Relation.CRUSH, Slot.OUR_NAMES, "Aymen", "Sara"))
        assertEquals("أحبك يا سارة", book.text("ar", Tone.SWEET, Relation.WIFE, Slot.LOVE_YOU, "", "سارة"))
        assertEquals("Crazy about you, Sara", book.text("en", Tone.FLIRTY, Relation.PARTNER, Slot.LOVE_YOU, "", "Sara"))
    }

    @Test
    fun withoutYourNameOurNamesLettersTheirNameAlone() {
        assertEquals("Sara", book.text("en", Tone.SWEET, Relation.GIRLFRIEND, Slot.OUR_NAMES, "", "Sara"))
        assertEquals("Sara", book.text("en", Tone.SWEET, Relation.GIRLFRIEND, Slot.OUR_NAMES, "  ", "Sara"))
    }

    @Test
    fun familyStaysInItsOwnLanguageAndIsAlwaysSweet() {
        // The prototype fell back to English here ("Love you, سارة").
        assertEquals("أحبك يا سارة", book.text("ar", Tone.FLIRTY, Relation.MOM, Slot.LOVE_YOU, "", "سارة"))
        assertEquals("You're the best, Sara", book.text("en", Tone.FLIRTY, Relation.FRIEND, Slot.BE_MINE, "", "Sara"))
        assertEquals("Personne n'est comme toi, Léa", book.text("fr", Tone.SWEET, Relation.FRIEND, Slot.BE_MINE, "", "Léa"))
        assertEquals("Good night, Sara", book.text("en", Tone.FLIRTY, Relation.MOM, Slot.GOOD_NIGHT, "", "Sara"))
    }

    @Test
    fun languagesFallBackToTheirBaseThenEnglish() {
        assertEquals("pt-BR", book.languageFor("pt-BR"))
        assertEquals("pt", book.languageFor("pt-PT"))
        assertEquals("zh", book.languageFor("zh-CN"))
        assertEquals("en", book.languageFor("ja"))
        assertEquals("Good night, Sara", book.text("ja", Tone.SWEET, Relation.PARTNER, Slot.GOOD_NIGHT, "", "Sara"))
        assertEquals(AppLanguages.entries.map { it.tag }.toSet(), book.languages())
    }
}
