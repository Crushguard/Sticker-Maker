package com.piptechnologies.stickermaker.catalog

import com.piptechnologies.stickermaker.core.data.catalog.CatalogFile
import com.piptechnologies.stickermaker.core.data.catalog.CatalogUrls
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Robolectric supplies android's org.json. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class CatalogFileTest {

    private val urls = CatalogUrls("https://cdn.example.com/{rawPath}", emulatorHost = null)

    private val json = """
        {
          "schema": 1, "version": 12, "publishedAt": "2026-09-29T14:05:00.000Z", "future": {"x": 1},
          "categories": [
            { "id": "couples", "order": 1, "icon": "heart-handshake", "hue": 10, "names": { "en": "Couples", "ar": "أزواج" },
              "keywords": {}, "packs": 1 },
            { "id": "sorry", "order": 4, "icon": "hand-heart", "hue": 20, "names": { "en": "Sorry" },
              "keywords": { "pt-BR": ["desculpa", "perdão"] }, "packs": 2 },
            { "id": "anime", "order": 11, "icon": "sparkles", "hue": 300, "names": { "en": "Anime" }, "keywords": {}, "packs": 0 }
          ],
          "packs": [
            { "id": "sorry-wiggle", "name": "Sorry Wiggle", "names": { "ar": "آسف" }, "category": "sorry",
              "alsoIn": ["couples"], "lang": "en", "animated": true, "count": 6, "version": 3, "adds": 150,
              "cover": { "s": "public/packs/sorry-wiggle/v3/cover-s.webp", "l": "public/packs/sorry-wiggle/v3/cover-l.webp", "tiles": 6 },
              "zip": { "path": "public/packs/sorry-wiggle/v3/pack.zip", "bytes": 563412 },
              "keywords": ["sorry", "forgive"], "publishedAt": "2026-09-29T14:05:00.000Z", "somethingNew": true },
            { "id": "no-zip", "name": "Broken", "category": "sorry", "count": 3, "version": 1 },
            { "id": "sorry-love", "name": "Sorry, My Love", "category": "sorry", "lang": "none", "count": 18, "version": 1,
              "cover": { "s": "public/packs/sorry-love/v1/cover-s.webp", "l": "public/packs/sorry-love/v1/cover-l.webp", "tiles": 6 },
              "zip": { "path": "public/packs/sorry-love/v1/pack.zip", "bytes": 504000 } }
          ]
        }
    """.trimIndent()

    @Test
    fun readsCategoriesInOrderWithNamesKeywordsAndCounts() {
        val catalog = CatalogFile.parse(json, urls)
        assertEquals(12, catalog.version)
        assertEquals(listOf("couples", "sorry", "anime"), catalog.categories.map { it.id })
        val sorry = catalog.categories[1]
        assertEquals("Sorry", sorry.name)
        assertEquals("hand-heart", sorry.icon)
        assertEquals(20, sorry.hue)
        assertEquals(4, sorry.order)
        assertEquals(2, sorry.packCount)
        assertEquals(listOf("desculpa", "perdão"), sorry.keywords["pt-BR"])
        assertEquals("أزواج", catalog.categories[0].names["ar"])
    }

    @Test
    fun readsPacksInRankOrderWithUrlsAndSkipsPacksWithoutAZip() {
        val catalog = CatalogFile.parse(json, urls)
        assertEquals(listOf("sorry-wiggle", "sorry-love"), catalog.packs.map { it.id })
        val pack = catalog.packs[0]
        assertEquals("Sorry Wiggle", pack.name)
        assertEquals(mapOf("ar" to "آسف"), pack.names)
        assertEquals("sorry", pack.category)
        assertEquals(listOf("couples"), pack.alsoIn)
        assertEquals("en", pack.lang)
        assertTrue(pack.animated)
        assertEquals(6, pack.stickerCount)
        assertEquals(3, pack.version)
        assertEquals(150L, pack.downloads)
        assertEquals(0, pack.order)
        assertEquals(20, pack.hue)
        assertEquals("https://cdn.example.com/public/packs/sorry-wiggle/v3/cover-s.webp", pack.coverSmallUrl)
        assertEquals("https://cdn.example.com/public/packs/sorry-wiggle/v3/cover-l.webp", pack.coverLargeUrl)
        assertEquals(6, pack.coverTiles)
        assertEquals("https://cdn.example.com/public/packs/sorry-wiggle/v3/pack.zip", pack.zipUrl)
        assertEquals(563412L, pack.zipBytes)
        assertEquals(listOf("sorry", "forgive"), pack.keywords)
    }

    @Test
    fun missingOptionalFieldsFallBackToDefaults() {
        val pack = CatalogFile.parse(json, urls).packs[1]
        assertEquals("none", pack.lang)
        assertEquals(emptyList<String>(), pack.alsoIn)
        assertEquals(0L, pack.downloads)
        assertEquals(1, pack.order)
        assertTrue(pack.names.isEmpty())
    }

    @Test
    fun anEmptyOrBrokenCatalogIsAnError() {
        val failure = runCatching { CatalogFile.parse("{oops", urls) }.exceptionOrNull()
        assertTrue(failure != null)
        assertNull(runCatching { CatalogFile.parse("{\"packs\": []}", urls) }.exceptionOrNull())
    }
}
