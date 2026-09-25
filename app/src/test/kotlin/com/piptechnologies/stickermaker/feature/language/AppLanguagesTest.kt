package com.piptechnologies.stickermaker.feature.language

import org.junit.Assert.assertEquals
import org.junit.Test

/** [AppLanguages.match] maps whatever tag Android reports onto a shipped language. */
class AppLanguagesTest {

    @Test
    fun exactTagsMatchThemselves() {
        for (language in AppLanguages.entries) {
            assertEquals(language.tag, AppLanguages.match(language.tag))
        }
    }

    @Test
    fun regionsFallBackToTheLanguage() {
        assertEquals("pt", AppLanguages.match("pt-PT"))
        assertEquals("pt-BR", AppLanguages.match("pt-BR"))
        assertEquals("pt-BR", AppLanguages.match("pt_BR"))
        assertEquals("en", AppLanguages.match("en-US"))
        assertEquals("ar", AppLanguages.match("ar-EG"))
        assertEquals("zh", AppLanguages.match("zh-Hans-CN"))
    }

    @Test
    fun legacyCodesMapToBcp47() {
        assertEquals("id", AppLanguages.match("in"))
        assertEquals("id", AppLanguages.match("in-ID"))
        assertEquals("he", AppLanguages.match("iw"))
        assertEquals("he", AppLanguages.match("iw-IL"))
    }

    @Test
    fun unknownLanguagesReadAsSystem() {
        assertEquals(AppLanguages.SYSTEM, AppLanguages.match("ja-JP"))
        assertEquals(AppLanguages.SYSTEM, AppLanguages.match(""))
    }
}
