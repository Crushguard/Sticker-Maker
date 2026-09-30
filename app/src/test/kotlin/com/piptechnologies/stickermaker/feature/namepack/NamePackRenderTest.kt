package com.piptechnologies.stickermaker.feature.namepack

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import com.piptechnologies.stickermaker.feature.language.AppLanguages
import com.piptechnologies.stickermaker.feature.namepack.engine.Character
import com.piptechnologies.stickermaker.feature.namepack.engine.Lettering
import com.piptechnologies.stickermaker.feature.namepack.engine.LetteringFont
import com.piptechnologies.stickermaker.feature.namepack.engine.PhraseBook
import com.piptechnologies.stickermaker.feature.namepack.engine.Relation
import com.piptechnologies.stickermaker.feature.namepack.engine.StickerComposer
import com.piptechnologies.stickermaker.feature.namepack.engine.TemplateSet
import com.piptechnologies.stickermaker.feature.namepack.engine.Tone
import com.piptechnologies.stickermaker.feature.namepack.engine.TrayRenderer
import com.piptechnologies.stickermaker.feature.namepack.engine.Zone
import com.piptechnologies.stickermaker.whatsapp.WebpInfo
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class)
class NamePackRenderTest {

    private val templates = File("src/main/assets/templates")
    private val lettering = Lettering(TestFonts::of)
    private val composer = StickerComposer(lettering)
    private val phrases = PhraseBook.parse(File(templates, "phrases.json").readText())

    @Test
    fun everyStickerIsAWhatsAppSizedWebpWithItsLettering() {
        val set = TemplateSet.parse(Character.MANGO, File(templates, "mango.json").readText())
        set.stickers.forEach { sticker ->
            val art = BitmapFactory.decodeFile(File(templates, sticker.file).path)
            val text = phrases.text("en", Tone.SWEET, Relation.GIRLFRIEND, sticker.slot, "Aymen", "Sara")
            val out = composer.compose(art, sticker, text, rtlLanguage = false)
            assertEquals(512, out.width)
            assertEquals(512, out.height)
            val webp = StickerComposer.encode(out)
            assertTrue("${sticker.file}: ${webp.size} bytes", webp.size <= 100 * 1024)
            val info = WebpInfo.parse(webp)
            assertEquals(512, info.width)
            assertFalse(info.isAnimated)
            assertTrue("${sticker.file}: lettering drawn", changedPixels(art, out, sticker.zone) > 150)
        }
    }

    @Test
    fun sampleNamesFitEveryZoneInEveryLanguageToneAndRelation() {
        val misses = mutableListOf<String>()
        Character.entries.forEach { character ->
            val set = TemplateSet.parse(character, File(templates, "${character.id}.json").readText())
            // Most relations share their phrases: each distinct lettering is fitted once per zone.
            val fitted = HashSet<String>()
            AppLanguages.entries.forEach { lang ->
                Relation.entries.forEach { relation ->
                    // Family relations are lettered Sweet only, as the phrase book resolves them.
                    val tones = if (relation.family) listOf(Tone.SWEET) else Tone.entries
                    tones.forEach { tone ->
                        set.stickers.forEach { s ->
                            val text = phrases.text(lang.tag, tone, relation, s.slot, "Aymen", "Sara")
                            if (fitted.add(s.slot.key + "\u0000" + text) && !lettering.fit(text, s).ok) {
                                misses += "${character.id}/${lang.tag}/${relation.id}/${tone.id}/${s.slot.key}: $text"
                            }
                        }
                    }
                }
            }
        }
        assertTrue("Phrases that hit the 20 px floor:\n" + misses.joinToString("\n"), misses.isEmpty())
    }

    @Test
    fun theTrayIsASmallPngHeart() {
        val tray = TrayRenderer(TestFonts::of)
        listOf("AS", "S", "أس").forEach { initials ->
            val bitmap = tray.render(initials)
            assertEquals(96, bitmap.width)
            val png = TrayRenderer.encode(bitmap)
            assertTrue("$initials: ${png.size} bytes", png.size <= 50 * 1024)
            assertEquals(0x89.toByte(), png[0])
            assertEquals('P'.code.toByte(), png[1])
            // The heart is rose at its left lobe, and the white initials sit in its middle.
            assertEquals(TrayRenderer.ROSE, bitmap.getPixel(20, 40))
            val white = (24 until 72).sumOf { x -> (20 until 64).count { y -> bitmap.getPixel(x, y) == Color.WHITE } }
            assertTrue("$initials: no white initials inside the heart", white > 20)
        }
    }

    /** Pixels inside the zone's box that the lettering changed. */
    private fun changedPixels(art: Bitmap, out: Bitmap, zone: Zone): Int {
        var changed = 0
        val left = (zone.cx - zone.w / 2).toInt().coerceIn(0, 511)
        val right = (zone.cx + zone.w / 2).toInt().coerceIn(0, 511)
        val top = (zone.cy - zone.h / 2).toInt().coerceIn(0, 511)
        val bottom = (zone.cy + zone.h / 2).toInt().coerceIn(0, 511)
        for (y in top..bottom step 2) for (x in left..right step 2) {
            if (art.getPixel(x, y) != out.getPixel(x, y)) changed++
        }
        return changed
    }
}

/** The bundled fonts loaded from the source tree (the app loads them as font resources). */
internal object TestFonts {
    private val faces by lazy {
        mapOf(
            LetteringFont.BALOO to Typeface.createFromFile(File("src/main/res/font/lettering_baloo2.ttf")),
            LetteringFont.BALOO_BHAIJAAN to Typeface.createFromFile(File("src/main/res/font/lettering_baloo_bhaijaan2.ttf")),
            LetteringFont.RUBIK to Typeface.createFromFile(File("src/main/res/font/lettering_rubik.ttf")),
            LetteringFont.SYSTEM_BOLD to Typeface.DEFAULT_BOLD
        )
    }

    fun of(font: LetteringFont): Typeface = faces.getValue(font)
}
