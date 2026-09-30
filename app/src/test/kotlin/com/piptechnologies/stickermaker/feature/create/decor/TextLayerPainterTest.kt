package com.piptechnologies.stickermaker.feature.create.decor

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import com.piptechnologies.stickermaker.feature.namepack.CmapCoverage
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class)
class TextLayerPainterTest {

    private val book = TextStyleBook.parse(File("src/main/assets/text/text-styles.json").readText())
    private val painter = TextLayerPainter(DecorFonts(book, DecorTestFonts::load), book) { false }

    private fun text(s: String, style: TextStyleId = TextStyleId.Sticker, font: FontMood = FontMood.Round) =
        LayerContent.Text(s, style, DecorSpec.ROSE, font)

    @Test
    fun scriptOfPicksTheStrongestScript() {
        assertEquals(Script.Latin, DecorFonts.scriptOf("love you"))
        assertEquals(Script.Arabic, DecorFonts.scriptOf("love you سارة"))
        assertEquals(Script.Cyrillic, DecorFonts.scriptOf("люблю"))
        assertEquals(Script.Devanagari, DecorFonts.scriptOf("प्यार"))
        assertEquals(Script.Hebrew, DecorFonts.scriptOf("אוהב"))
    }

    @Test
    fun fontsFollowMoodAndScript() {
        val classic = book.styles.getValue(TextStyleId.Classic).uiFont
        val lettering = book.styles.getValue(TextStyleId.Sticker).uiFont
        fun file(text: String, mood: FontMood, ui: Boolean = lettering) = DecorFonts.fileFor(text, ui, mood, book)

        assertEquals(FontFile.UI, file("love", FontMood.Hand, classic))
        assertEquals(FontFile.BALOO, file("love", FontMood.Round))
        assertEquals(FontFile.BALOO_BHAIJAAN, file("أحبك", FontMood.Round))
        assertEquals(FontFile.SYSTEM_BOLD, file("ځ", FontMood.Round))
        assertEquals(FontFile.CAVEAT, file("love", FontMood.Hand))
        assertEquals(FontFile.MIRZA, file("أحبك", FontMood.Hand))
        assertEquals(FontFile.YATRA, file("प्यार", FontMood.Display))
        assertEquals(FontFile.KARANTINA, file("אוהב", FontMood.Display))

        // Faces load once per file, through the injected loader.
        val loads = mutableListOf<FontFile>()
        val fonts = DecorFonts(book) { loads += it; DecorTestFonts.load(it) }
        val hand = fonts.forText("love", uiFont = false, mood = FontMood.Hand)
        assertSame(hand, fonts.forText("miss you", uiFont = false, mood = FontMood.Hand))
        assertSame(hand, fonts.face(FontFile.CAVEAT))
        assertEquals(listOf(FontFile.CAVEAT), loads)
    }

    @Test
    fun handAndDisplayGiveWayWhenTheFontLacksALetter() {
        val fonts = DecorFonts(book, DecorTestFonts::load)
        fun face(text: String, mood: FontMood) = fonts.forText(text, uiFont = false, mood = mood)
        // Lilita One has no ş: the whole string letters in the Rounded choice, not one glyph from a fallback.
        assertSame(fonts.face(FontFile.LILITA), face("Ask", FontMood.Display))
        assertSame(fonts.face(FontFile.BALOO), face("Aşk", FontMood.Display))
        assertSame(fonts.face(FontFile.BALOO), face("İyi geceler Ğ", FontMood.Display))
        // Spaces, punctuation and emoji don't count.
        assertSame(fonts.face(FontFile.LILITA), face("Ask me! ❤️ …", FontMood.Display))
        // Lalezar lacks the Pashto ځ; the Rounded choice for it is the system bold (the Pashto rule).
        assertSame(fonts.face(FontFile.LALEZAR), face("أحبك", FontMood.Display))
        assertSame(fonts.face(FontFile.SYSTEM_BOLD), face("ځان", FontMood.Display))
        // Mirza has it, so Hand keeps its own face.
        assertSame(fonts.face(FontFile.MIRZA), face("ځان", FontMood.Hand))
        // The table choice itself is unchanged.
        assertEquals(FontFile.LILITA, DecorFonts.fileFor("Aşk", false, FontMood.Display, book))
    }

    @Test
    fun fontCoverageReadsTheCmap() {
        val sample = (0x20..0x24F) + (0x400..0x4FF) + (0x590..0x6FF) + (0x900..0x97F) + listOf(0x4F60, 0x1F600)
        // Every Hand and Display font (their coverage decides the fallback); Kalam has a format 12 subtable.
        listOf(
            "assets/fonts/lettering_caveat.ttf", "res/font/lettering_mirza.ttf", "res/font/lettering_kalam.ttf",
            "res/font/lettering_amaticsc.ttf", "res/font/lettering_lilitaone.ttf",
            "res/font/lettering_ruslandisplay.ttf", "res/font/lettering_lalezar.ttf",
            "res/font/lettering_yatraone.ttf", "res/font/lettering_karantina.ttf"
        ).forEach { path ->
            val file = File("src/main/$path")
            val ours = FontCoverage.read(file.readBytes())!!
            val reference = CmapCoverage.of(file)
            sample.forEach { cp -> assertEquals("$path U+%04X".format(cp), reference.covers(cp), ours.covers(cp)) }
        }
        assertEquals(null, FontCoverage.read(ByteArray(12)))
    }

    @Test
    fun sizeGrowsWithTextAndWrapsToTwoLines() {
        assertTrue(painter.baseSize(text("hi")).w < painter.baseSize(text("hello there")).w)

        val phrase = "you are my favourite person ok"
        assertEquals(30, phrase.length)
        val oneWord = painter.baseSize(text("love"))
        val twoLines = painter.baseSize(text(phrase))
        // The second line adds one 1.05 × 52 px line box.
        assertEquals(1.05f * TextStyleBook.DEFAULT_TEXT_PX, twoLines.h - oneWord.h, 1.5f)
        // No-break spaces offer no split: the same words stay on one, wider line.
        val unbroken = painter.baseSize(text(phrase.replace(' ', '\u00A0')))
        assertEquals(oneWord.h, unbroken.h, 0f)
        assertTrue(twoLines.w < unbroken.w)

        val t = text("love you")
        val one = painter.render(t, 1f)
        assertEquals(painter.baseSize(t).w, one.width.toFloat(), 0f)
        assertEquals(painter.baseSize(t).h, one.height.toFloat(), 0f)
        // Ruling 2: the fixed 2 px margin per side does not scale, so allow 6 px.
        assertEquals(2f * one.width, painter.render(t, 2f).width.toFloat(), 6f)
    }

    @Test
    fun inkPastTheStyleBoxIsKeptNotClipped() {
        // Devanagari marks rise above the 1.05 em line box, and the Bold stroke adds 0.1 em around them.
        val tall = text("प्यार है तुम्हें", TextStyleId.Stroke)
        val base = painter.baseSize(tall)
        val out = painter.render(tall, 1f)
        assertTrue("taller than the style box: ${out.height} vs ${base.h}", out.height > base.h)
        assertEquals("still centred: grows on both sides", (out.height - base.h) % 2f, 0f, 0f)
        val top = (0 until out.width).count { Pixels.alpha(out.getPixel(it, 0)) > 8 }
        val bottom = (0 until out.width).count { Pixels.alpha(out.getPixel(it, out.height - 1)) > 8 }
        assertTrue("the ink reaches one edge, not beyond", top + bottom > 0)
    }

    @Test
    fun longAndMixedScripts() {
        val cjk = "我爱你".repeat(10)
        assertEquals(30, cjk.length)
        assertEquals("no space: one line", painter.baseSize(text("hi")).h, painter.baseSize(text(cjk)).h, 0f)
        val long = painter.render(text(cjk), 1f)
        assertTrue(Pixels.count(long) { Pixels.alpha(it) > 0 } > 200)

        val mixed = painter.render(text("love you سارة"), 1f)
        assertTrue("rose fill", Pixels.count(mixed) { Pixels.near(it, DecorSpec.ROSE, 40) } > 100)
        assertTrue("white edge", Pixels.count(mixed) { Pixels.near(it, Color.WHITE, 40) } > 100)
    }

    @Test
    fun aCaptionReadsInTheDirectionOfItsFirstStrongLetter() {
        val arabicApp = TextLayerPainter(DecorFonts(book, DecorTestFonts::load), book) { true }
        // "OK!" letters left to right in an Arabic app: the exclamation mark ends it, it doesn't lead.
        assertFalse(arabicApp.isRtl(text("OK!")))
        assertFalse(arabicApp.isRtl(text("love you سارة")))
        // An Arabic phrase in an English app reads right to left.
        assertTrue(painter.isRtl(text("أحبك")))
        assertTrue(painter.isRtl(text("!أحبك")))
        // With no strong letter the app language decides: emoji, digits, marks.
        assertFalse(painter.isRtl(text("❤️❤️")))
        assertTrue(arabicApp.isRtl(text("❤️❤️")))
        assertTrue(arabicApp.isRtl(text("12 ♥")))
        assertFalse(painter.isRtl(text("")))
    }

    @Test
    fun bubbleHasWhiteFillAndInkBorder() {
        val bubble = painter.render(text("hi", TextStyleId.Bubble), 1f)
        val y = bubble.height / 2
        // The body is centred on the bitmap; the tail hangs into the bottom margin.
        val border = (0 until bubble.width).first { Pixels.alpha(bubble.getPixel(it, y)) > 200 }
        assertTrue("ink border", Pixels.within(bubble.getPixel(border, y), INK, 30))
        val white = (border until border + 20).firstOrNull { bubble.getPixel(it, y) == Color.WHITE }
        assertTrue("white body next to the border", white != null)
    }

    @Test
    fun stickerStyleHasAWhiteEdge() {
        val sticker = painter.render(text("love"), 1f)
        val y = sticker.height / 2
        val row = IntArray(sticker.width) { sticker.getPixel(it, y) }
        val firstWhite = row.indexOfFirst { Pixels.near(it, Color.WHITE, 30) }
        val firstRose = row.indexOfFirst { Pixels.near(it, DecorSpec.ROSE, 40) }
        assertTrue("rose on the row", firstRose > 0)
        assertTrue("white ($firstWhite) before rose ($firstRose)", firstWhite in 0 until firstRose)
    }

    private companion object {
        val INK = 0xFF1E2128.toInt()
    }
}

/** The Create fonts from the source tree (the app loads them as font resources, Caveat as an asset). */
internal object DecorTestFonts {
    private val paths = mapOf(
        FontFile.UI to "res/font/hg_extrabold.ttf",
        FontFile.BALOO to "res/font/lettering_baloo2.ttf",
        FontFile.BALOO_BHAIJAAN to "res/font/lettering_baloo_bhaijaan2.ttf",
        FontFile.RUBIK to "res/font/lettering_rubik.ttf",
        FontFile.CAVEAT to "assets/fonts/lettering_caveat.ttf",
        FontFile.MIRZA to "res/font/lettering_mirza.ttf",
        FontFile.KALAM to "res/font/lettering_kalam.ttf",
        FontFile.AMATIC to "res/font/lettering_amaticsc.ttf",
        FontFile.LILITA to "res/font/lettering_lilitaone.ttf",
        FontFile.RUSLAN to "res/font/lettering_ruslandisplay.ttf",
        FontFile.LALEZAR to "res/font/lettering_lalezar.ttf",
        FontFile.YATRA to "res/font/lettering_yatraone.ttf",
        FontFile.KARANTINA to "res/font/lettering_karantina.ttf"
    )

    fun load(file: FontFile): FontFace {
        if (file == FontFile.SYSTEM_BOLD) return FontFace(Typeface.DEFAULT_BOLD)
        val font = File("src/main/" + paths.getValue(file))
        return FontFace(Typeface.createFromFile(font), coverage = FontCoverage.read(font.readBytes()))
    }
}

/** Pixel checks shared by the Create rendering tests. */
internal object Pixels {
    fun alpha(c: Int): Int = c ushr 24

    /** Opaque (alpha > 200) and closer than [distance] to [target] in RGB space. */
    fun near(c: Int, target: Int, distance: Int): Boolean {
        if (alpha(c) <= 200) return false
        val dr = (Color.red(c) - Color.red(target)).toDouble()
        val dg = (Color.green(c) - Color.green(target)).toDouble()
        val db = (Color.blue(c) - Color.blue(target)).toDouble()
        return sqrt(dr * dr + dg * dg + db * db) < distance
    }

    /** Opaque (alpha > 200) and within [tolerance] of [target] on every channel. */
    fun within(c: Int, target: Int, tolerance: Int): Boolean = alpha(c) > 200 &&
        abs(Color.red(c) - Color.red(target)) <= tolerance &&
        abs(Color.green(c) - Color.green(target)) <= tolerance &&
        abs(Color.blue(c) - Color.blue(target)) <= tolerance

    fun count(bitmap: Bitmap, test: (Int) -> Boolean): Int {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.count(test)
    }
}
