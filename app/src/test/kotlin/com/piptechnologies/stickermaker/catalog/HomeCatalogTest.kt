package com.piptechnologies.stickermaker.catalog

import com.piptechnologies.stickermaker.R
import com.piptechnologies.stickermaker.core.model.Category
import com.piptechnologies.stickermaker.core.model.StickerPack
import com.piptechnologies.stickermaker.core.model.inCategory
import com.piptechnologies.stickermaker.core.model.withPacks
import com.piptechnologies.stickermaker.core.ui.UiText
import com.piptechnologies.stickermaker.core.ui.addsLabel
import com.piptechnologies.stickermaker.feature.home.matchesQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeCatalogTest {

    private fun pack(
        id: String = "sorry-wiggle",
        name: String = "Sorry Wiggle",
        category: String = "sorry",
        alsoIn: List<String> = emptyList(),
        keywords: List<String> = emptyList(),
        names: Map<String, String> = emptyMap(),
    ) = StickerPack(
        id = id, name = name, publisher = "PIP Technologies", category = category, animated = false, order = 0,
        downloads = 0, hue = 0, stickerCount = 6, stickerUrls = emptyList(), alsoIn = alsoIn, keywords = keywords,
        names = names,
    )

    private val categories = listOf(
        Category("sorry", "Sorry", "hand-heart", 20, 4, names = mapOf("en" to "Sorry", "pt-BR" to "Desculpa"),
            keywords = mapOf("pt-BR" to listOf("perdão"), "id" to listOf("maaf")), packCount = 2),
        Category("couples", "Couples", "heart-handshake", 10, 1, names = mapOf("en" to "Couples"), packCount = 1),
        Category("anime", "Anime", "sparkles", 300, 11, names = mapOf("en" to "Anime"), packCount = 0),
    ).associateBy { it.id }

    @Test
    fun addsAreShownOnlyFromOneHundredRealAdds() {
        assertNull(addsLabel(0))
        assertNull(addsLabel(99))
        assertEquals(UiText.res(R.string.pack_adds, UiText.Compact(100)), addsLabel(100))
    }

    @Test
    fun aPackBelongsToItsFolderCategoryAndItsAlsoInCategories() {
        val p = pack(alsoIn = listOf("couples"))
        assertTrue(p.inCategory("sorry"))
        assertTrue(p.inCategory("couples"))
        assertFalse(p.inCategory("anime"))
    }

    @Test
    fun categoriesWithoutLivePacksAreHidden() {
        assertEquals(listOf("sorry", "couples"), categories.values.toList().withPacks().map { it.id })
        val unknownCount = Category("x", "X", "heart", 0, 1)
        assertEquals(listOf("x"), listOf(unknownCount).withPacks().map { it.id })
    }

    @Test
    fun searchMatchesNamesKeywordsAndCategoryWordsInAnyLanguage() {
        val p = pack(alsoIn = listOf("couples"), keywords = listOf("forgive", "bunny"), names = mapOf("ar" to "آسف"))
        assertTrue(matchesQuery(p, "wiggle", categories))
        assertTrue(matchesQuery(p, "FORGIVE", categories))
        assertTrue(matchesQuery(p, "آسف", categories))
        assertTrue(matchesQuery(p, "desculpa", categories), "category name in another language")
        assertTrue(matchesQuery(p, "maaf", categories), "category keyword")
        assertTrue(matchesQuery(p, "couples", categories), "alsoIn category")
        assertFalse(matchesQuery(p, "anime", categories))
        assertTrue(matchesQuery(p, "sor", categories), "prefix")
    }

    private fun assertTrue(condition: Boolean, message: String) = org.junit.Assert.assertTrue(message, condition)
}
