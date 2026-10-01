package com.piptechnologies.stickermaker.feature.create.decor

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class DecorDataTest {

    private fun asset(path: String) = File("src/main/assets/$path").readText()

    @Test
    fun decorCatalogHas54PiecesAndEveryFileShips() {
        val catalog = DecorCatalog.parse(asset("decor/decor.json"))
        assertEquals(54, catalog.pieces.size)
        assertEquals(30, catalog.set(DecorCatalog.DOODLES).size)
        assertEquals(24, catalog.set(DecorCatalog.PROPS).size)
        catalog.pieces.forEach { piece ->
            assertTrue(piece.file, File("src/main/assets/decor/${piece.file}").isFile)
            assertTrue(piece.file, piece.defaultWidth > 0f && piece.defaultWidth <= 1f)
            assertTrue(piece.file, piece.emojis.isNotEmpty())
        }
        val crown = catalog.byFile("doodles-1.webp")!!
        assertEquals("Crown", crown.label)
        assertTrue(crown.headTop)
        assertEquals(0.34f, crown.defaultWidth, 1e-6f)
    }

    @Test
    fun emojiSubsetHasSevenTabsAnd327BundledFiles() {
        val catalog = EmojiCatalog.parse(asset("emoji/subset.json"))
        assertEquals(listOf("love", "smileys", "hearts", "hands", "animals", "food", "symbols"), catalog.tabs.keys.toList())
        assertEquals(listOf(22, 84, 24, 48, 54, 46, 80), catalog.tabs.values.map { it.size })
        assertEquals(327, catalog.files.size)
        catalog.files.forEach { assertTrue(it, File("src/main/assets/emoji/$it").isFile) }
        val toned = catalog.tabs.values.flatten().filter { it.skinTones }.distinctBy { it.file }
        assertEquals(42, toned.size)
        assertTrue(toned.all { item -> catalog.tab("hands").any { it.file == item.file } })
    }

    @Test
    fun toneUrlFollowsTheFluentLayout() {
        val wave = EmojiItem("Waving hand", "1F44B", "👋", "waving_hand.webp", skinTones = true)
        assertEquals(
            "https://raw.githubusercontent.com/microsoft/fluentui-emoji/main/assets/Waving%20hand/Medium-Light/3D/waving_hand_3d_medium-light.png",
            EmojiCatalog.toneUrl(wave, SkinTone.MediumLight)
        )
    }

    @Test
    fun textStylesColoursFontsOutlineAndMarker() {
        val book = TextStyleBook.parse(asset("text/text-styles.json"))
        assertEquals(TextStyleId.entries.toSet(), book.styles.keys)
        val sticker = book.styles.getValue(TextStyleId.Sticker)
        assertNull(sticker.fill)                              // the chosen colour
        assertEquals(0.16f, sticker.strokeEm, 1e-6f)
        assertEquals(0xFFFFFFFF.toInt(), sticker.strokeColour)
        val stroke = book.styles.getValue(TextStyleId.Stroke)
        assertEquals(0xFFFFFFFF.toInt(), stroke.fill)
        assertNull(stroke.strokeColour)                       // the chosen colour
        assertTrue(book.styles.getValue(TextStyleId.Classic).uiFont)
        val bubble = book.styles.getValue(TextStyleId.Bubble).bubble!!
        assertEquals(28f, bubble.radius, 0f)
        assertEquals(26f, bubble.tailW, 0f)
        assertEquals(8, book.colours.size)
        assertEquals(0xFFC23359.toInt(), book.colours.first { it.id == "rose" }.argb)
        assertEquals(FontRef("Mirza", 700), book.moods.getValue(FontMood.Hand).getValue(Script.Arabic))
        assertEquals(FontRef("Baloo Bhaijaan 2", 800), book.moods.getValue(FontMood.Round).getValue(Script.Arabic))
        assertEquals(mapOf(OutlineThickness.Thin to 5f, OutlineThickness.Medium to 8f, OutlineThickness.Thick to 12f), book.outlinePx)
        assertEquals(5, book.outlineColours.size)
        assertEquals(mapOf(MarkerSize.S to 8f, MarkerSize.M to 14f, MarkerSize.L to 22f), book.markerPx)
        assertEquals(3f, book.markerEdgeExtra, 0f)
    }

    @Test
    fun parseColourHandlesHexRgbaAndPlaceholders() {
        assertEquals(0xFFC23359.toInt(), TextStyleBook.parseColour("#C23359"))
        assertEquals(0xFFFFFFFF.toInt(), TextStyleBook.parseColour("#fff"))
        assertEquals(0xFFF5C542.toInt(), TextStyleBook.parseColour("#F5C542 with #fff core"))
        assertEquals(0x59000000, TextStyleBook.parseColour("rgba(0,0,0,.35)"))
        assertNull(TextStyleBook.parseColour("{colour}"))
    }

    @Test
    fun quickPhrasesResolveLanguageThenEnglish() {
        val phrases = QuickPhrases.parse("""{"_":"x","en":["a"],"pt":["p"],"pt-BR":["b"],"in":["i"],"iw":["h"]}""")
        assertEquals(listOf("a"), phrases.forLocale("de", null))
        assertEquals(listOf("p"), phrases.forLocale("pt", "PT"))
        assertEquals(listOf("b"), phrases.forLocale("pt", "BR"))
        assertEquals(listOf("i"), phrases.forLocale("id", null))
        assertEquals(listOf("h"), phrases.forLocale("he", null))
        val shipped = QuickPhrases.parse(asset("text/quick-phrases.json"))
        listOf("en", "ar", "fr", "hi").forEach { assertEquals(it, 14, shipped.forLocale(it, null).size) }
    }

    @Test
    fun motionPresetsLoopAndStartAtRest() {
        val book = MotionBook.parse(asset("motion/presets.json"))
        assertEquals(12, book.fps)
        assertEquals(
            listOf("none", "heartbeat", "wiggle", "bounce", "float", "jelly", "shake", "hearts", "sparkle"),
            book.presets.map { it.id }
        )
        book.presets.forEach { p ->
            val first = p.keyframes.first()
            val last = p.keyframes.last()
            assertEquals(p.id, 0f, first.t, 0f)
            listOf(first, last).forEach { k ->
                assertEquals(p.id, 1f, k.scaleX, 0f); assertEquals(p.id, 1f, k.scaleY, 0f)
                assertEquals(p.id, 0f, k.rotate, 0f); assertEquals(p.id, 0f, k.dx, 0f); assertEquals(p.id, 0f, k.dy, 0f)
            }
            if (!p.isNone) {
                assertEquals(p.id, 1f, last.t, 0f)
                assertTrue(p.id, p.frames in 12..16)
                assertTrue(p.id, p.baseScale in 0.8f..1f)
            }
        }
        assertEquals(ParticleKind.Hearts, book.byId("hearts").particles!!.kind)
        assertEquals(6, book.byId("hearts").particles!!.count)
        assertEquals(700, book.byId("sparkle").particles!!.lifetimeMs)
        assertEquals(Easing.EaseInOut, book.byId("heartbeat").keyframes[0].easing)
        assertEquals("none", book.byId("does-not-exist").id)
    }

    @Test
    fun quickPhrasesShipForEveryAppLocale() {
        val shipped = QuickPhrases.parse(asset("text/quick-phrases.json"))
        val english = shipped.forLocale("en", null)
        listOf("en", "ar", "de", "es", "fa", "fr", "ha", "hi", "in", "it", "iw", "my", "ps", "pt", "ru", "tr", "ur", "zh").forEach {
            assertEquals(it, 14, shipped.forLocale(it, null).size)
            // A locale missing from the file falls back to English, which would still be 14 long.
            assertTrue("$it has no phrases of its own", it == "en" || shipped.forLocale(it, null) != english)
        }
        assertEquals(14, shipped.forLocale("pt", "BR").size)
        assertTrue(shipped.forLocale("pt", "BR") !== shipped.forLocale("pt", null))
    }
}
