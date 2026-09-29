package com.piptechnologies.stickermaker.catalog

import com.piptechnologies.stickermaker.core.data.catalog.inLanguageOrder
import com.piptechnologies.stickermaker.core.model.StickerPack
import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageOrderTest {

    private fun pack(id: String, lang: String) = StickerPack(
        id = id, name = id, publisher = "PIP Technologies", category = "sorry", animated = false, order = 0,
        downloads = 0, hue = 0, stickerCount = 3,
        lang = lang,
    )

    private val ranked = listOf(pack("en1", "en"), pack("ar1", "ar"), pack("none1", "none"), pack("hi1", "hi"),
        pack("en2", "en"), pack("br1", "pt-BR"), pack("ar2", "ar"))

    @Test
    fun theAppLanguageAndTextFreePacksComeFirstThenEnglishThenTheRestKeepingRank() {
        assertEquals(
            listOf("ar1", "none1", "ar2", "en1", "en2", "hi1", "br1"),
            ranked.inLanguageOrder("ar").map { it.id },
        )
    }

    @Test
    fun aRegionalAppLanguageMatchesItsLanguage() {
        assertEquals(listOf("none1", "br1", "en1", "en2", "ar1", "hi1", "ar2"), ranked.inLanguageOrder("pt").map { it.id })
        assertEquals(listOf("none1", "br1", "en1", "en2", "ar1", "hi1", "ar2"), ranked.inLanguageOrder("pt-BR").map { it.id })
    }

    @Test
    fun englishReadersKeepTheRankForEnglishAndTextFreePacks() {
        assertEquals(
            listOf("en1", "none1", "en2", "ar1", "hi1", "br1", "ar2"),
            ranked.inLanguageOrder("en").map { it.id },
        )
    }
}
