package com.piptechnologies.stickermaker.l10n

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.piptechnologies.stickermaker.R
import com.piptechnologies.stickermaker.feature.language.AppLanguages
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Formats every string and plural the way the app does, in every shipped language, through
 * the compiled resources: a translation that parses but breaks at runtime (a bad specifier,
 * a missing argument) fails here instead of crashing a screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LocalizedFormattingTest {

    private val app: Context = ApplicationProvider.getApplicationContext()
    private val source = L10nFixtures.source()

    private fun inLanguage(tag: String): Context {
        val config = Configuration(app.resources.configuration)
        config.setLocale(Locale.forLanguageTag(tag))
        return app.createConfigurationContext(config)
    }

    /** Sample arguments for [text]'s specifiers, by position. */
    private fun argsFor(text: String): Array<Any> {
        val specs = L10nFixtures.specs(text)
        val args = arrayOfNulls<Any>(specs.size)
        specs.forEachIndexed { order, spec ->
            val index = spec.substringAfter('%').substringBefore('$', "").toIntOrNull()?.minus(1) ?: order
            args[index] = when (spec.last()) {
                'd' -> 7
                'f' -> 12.4
                else -> "Mango"
            }
        }
        return args.map { it ?: "" }.toTypedArray()
    }

    @Test
    fun everyStringFormatsInEveryLanguage() {
        for (language in AppLanguages.entries) {
            val context = inLanguage(language.tag)
            for ((key, english) in source.strings) {
                val id = R.string::class.java.getField(key).getInt(null)
                val text = context.getString(id, *argsFor(english))
                assertTrue("${language.tag}/$key is blank", text.isNotBlank())
                assertTrue("${language.tag}/$key left a specifier: $text", !Regex("%\\d*\\$?[ds]").containsMatchIn(text))
            }
        }
    }

    @Test
    fun everyPluralFormatsForTheCountsTheAppShows() {
        val counts = listOf(0, 1, 2, 3, 4, 5, 11, 12, 21, 22, 25, 30, 100, 101)
        for (language in AppLanguages.entries) {
            val context = inLanguage(language.tag)
            for (key in source.plurals.keys) {
                val id = R.plurals::class.java.getField(key).getInt(null)
                for (count in counts) {
                    val text = context.resources.getQuantityString(id, count, count)
                    assertTrue("${language.tag}/$key($count) is blank", text.isNotBlank())
                }
            }
        }
    }

    @Test
    fun rightToLeftLanguagesLayOutRightToLeft() {
        for (language in AppLanguages.entries) {
            val direction = inLanguage(language.tag).resources.configuration.layoutDirection
            val expected = if (language.rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
            assertEquals("${language.tag} layout direction", expected, direction)
        }
    }

    @Test
    fun translationsResolveForTheirLanguage() {
        // Guards the folder names: values-in for "id", values-iw for "he", values-pt-rBR.
        val english = inLanguage("en").getString(R.string.nav_home)
        for (language in AppLanguages.entries.filter { it.tag != "en" }) {
            val file = L10nFixtures.translation(language.tag)
            val expected = file.strings.getValue("nav_home").replace("\\'", "'").replace("\\\"", "\"")
            val actual = inLanguage(language.tag).getString(R.string.nav_home)
            assertEquals("${language.tag} resolves its own nav_home", expected, actual)
            if (expected != english) assertTrue(actual != english)
        }
    }
}
