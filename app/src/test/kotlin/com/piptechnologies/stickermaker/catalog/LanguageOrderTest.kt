package com.piptechnologies.stickermaker.catalog

import com.piptechnologies.stickermaker.core.data.catalog.inLanguageOrder
import com.piptechnologies.stickermaker.core.model.StickerPack
import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageOrderTest {

    private fun pack(id: String, vararg langs: String) = StickerPack(
        id = id, name = id, publisher = "PIP Technologies", category = "sorry", animated = false, order = 0,
        downloads = 0, hue = 0, stickerCount = 3,
        langs = langs.toList(),
    )

    private val ranked = listOf(
        pack("en1", "en"), pack("ar1", "ar"), pack("none1", "none"), pack("hi1", "hi"), pack("en2", "en"),
        pack("br1", "pt-BR"), pack("ar2", "ar"), pack("mix1", "ar", "hi", "es"), pack("multi1", "multi", "fr"),
    )

    @Test
    fun textFreeEnglishAndMultilingualPacksAreForEveryoneAndTheAppLanguageJoinsThem() {
        assertEquals(
            listOf("en1", "ar1", "none1", "en2", "ar2", "mix1", "multi1", "hi1", "br1"),
            ranked.inLanguageOrder("ar").map { it.id },
        )
    }

    @Test
    fun aRegionalAppLanguageMatchesItsLanguage() {
        val forPortuguese = listOf("en1", "none1", "en2", "br1", "multi1", "ar1", "hi1", "ar2", "mix1")
        assertEquals(forPortuguese, ranked.inLanguageOrder("pt").map { it.id })
        assertEquals(forPortuguese, ranked.inLanguageOrder("pt-BR").map { it.id })
    }

    @Test
    fun aPackInSeveralLanguagesIsReadableInEachOfThem() {
        assertEquals(
            listOf("en1", "none1", "hi1", "en2", "mix1", "multi1", "ar1", "br1", "ar2"),
            ranked.inLanguageOrder("hi").map { it.id },
        )
    }

    @Test
    fun englishReadersKeepTheRankForWorldwidePacks() {
        assertEquals(
            listOf("en1", "none1", "en2", "multi1", "ar1", "hi1", "br1", "ar2", "mix1"),
            ranked.inLanguageOrder("en").map { it.id },
        )
    }
}
