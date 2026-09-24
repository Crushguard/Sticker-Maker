package com.piptechnologies.stickermaker.l10n

import com.piptechnologies.stickermaker.feature.language.AppLanguages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every shipped translation mirrors values/strings.xml: the same translatable keys, the same
 * placeholders, and the plural categories its language needs. Pure XML checks, so a broken
 * translation fails here with the key named instead of crashing String.format on a device.
 */
class StringResourcesTest {

    private val source = L10nFixtures.source()
    private val translatable = source.strings.keys - source.untranslatable
    private val translations = AppLanguages.entries.map { it.tag }.filter { it != "en" }

    @Test
    fun everyLanguageHasEveryTranslatableKey() {
        for (tag in translations) {
            val file = L10nFixtures.translation(tag)
            assertEquals("$tag: string keys", translatable, file.strings.keys)
            assertEquals("$tag: plurals keys", source.plurals.keys, file.plurals.keys)
        }
    }

    @Test
    fun untranslatableStringsAreNotTranslated() {
        for (tag in translations) {
            val leaked = L10nFixtures.translation(tag).strings.keys.intersect(source.untranslatable)
            assertTrue("$tag translates $leaked", leaked.isEmpty())
        }
    }

    @Test
    fun placeholdersMatchTheSource() {
        for (tag in translations) {
            val file = L10nFixtures.translation(tag)
            for ((key, text) in file.strings) {
                val english = source.strings.getValue(key)
                assertEquals(
                    "$tag/$key placeholders",
                    L10nFixtures.specs(english).sorted(),
                    L10nFixtures.specs(text).sorted()
                )
                if ("%%" in english) assertTrue("$tag/$key must keep %%", "%%" in text)
            }
        }
    }

    @Test
    fun pluralsHaveTheirLanguagesCategories() {
        for (tag in translations) {
            val required = L10nFixtures.requiredPlurals.getValue(tag)
            val allowed = L10nFixtures.allowedPlurals[tag] ?: required
            val exact = L10nFixtures.exactPlurals[tag].orEmpty()
            for ((key, items) in L10nFixtures.translation(tag).plurals) {
                val missing = required - items.keys
                val unknown = items.keys - allowed
                assertTrue("$tag/$key lacks $missing", missing.isEmpty())
                assertTrue("$tag/$key has $unknown", unknown.isEmpty())
                val english = L10nFixtures.specs(source.plurals.getValue(key).getValue("other"))
                for ((quantity, text) in items) {
                    val specs = L10nFixtures.specs(text)
                    if (quantity in exact) {
                        assertTrue("$tag/$key[$quantity] placeholders $specs", english.containsAll(specs))
                    } else {
                        assertEquals("$tag/$key[$quantity] placeholders", english.sorted(), specs.sorted())
                    }
                }
            }
        }
    }

    @Test
    fun translationsAreNotEnglishCopies() {
        for (tag in translations) {
            val file = L10nFixtures.translation(tag)
            val same = file.strings.count { (key, text) -> text == source.strings[key] }
            assertTrue(
                "$tag: $same of ${file.strings.size} strings are identical to English",
                same * 5 < file.strings.size
            )
        }
    }

    @Test
    fun sourceUsesPositionalPlaceholders() {
        // Translators reorder arguments; only positional specifiers survive that.
        for ((key, text) in source.strings) {
            val loose = L10nFixtures.specs(text).filter { '$' !in it }
            assertTrue("$key uses non-positional $loose", loose.isEmpty())
        }
    }
}
