package com.piptechnologies.stickermaker.l10n

import com.piptechnologies.stickermaker.feature.language.AppLanguages
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * res/xml/locales_config.xml (Android 13+'s per-app language list) is hand-written; it must
 * offer exactly the languages the Language screen offers, each backed by a translation.
 */
class LocalesConfigTest {

    private val tags: List<String> by lazy {
        val file = File(L10nFixtures.resDir, "xml/locales_config.xml")
        val doc = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(file)
        assertEquals("locale-config", doc.documentElement.tagName)
        val nodes = doc.getElementsByTagName("locale")
        (0 until nodes.length).map { i ->
            (nodes.item(i) as Element).getAttributeNS("http://schemas.android.com/apk/res/android", "name")
        }
    }

    @Test
    fun listsExactlyTheLanguageScreensLanguages() {
        assertEquals("duplicates", tags.size, tags.toSet().size)
        assertEquals(AppLanguages.entries.map { it.tag }, tags)
    }

    @Test
    fun shipsTheUnionOfTheSisterAppsLanguages() {
        // RecoverMe and Status Saver together: 18 translations plus English.
        val expected = setOf(
            "en", "ar", "de", "es", "fa", "fr", "ha", "he", "hi", "id",
            "it", "my", "ps", "pt", "pt-BR", "ru", "tr", "ur", "zh"
        )
        assertEquals(expected, tags.toSet())
    }

    @Test
    fun usesBcp47TagsNotFolderCodes() {
        assertTrue(tags.none { it == "in" || it == "iw" || "-r" in it })
    }

    @Test
    fun everyLanguageBesidesEnglishHasAResourceFolder() {
        for (tag in tags.filter { it != "en" }) {
            val folder = File(L10nFixtures.resDir, L10nFixtures.folderFor(tag))
            assertTrue("$tag: ${folder.path}/strings.xml missing", File(folder, "strings.xml").isFile)
        }
    }

    @Test
    fun rightToLeftFlagsMatchTheScripts() {
        val rtl = AppLanguages.entries.filter { it.rtl }.map { it.tag }.toSet()
        assertEquals(setOf("ar", "fa", "he", "ps", "ur"), rtl)
    }
}
