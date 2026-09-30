package com.piptechnologies.stickermaker.feature.namepack

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import com.piptechnologies.stickermaker.feature.language.AppLanguages
import com.piptechnologies.stickermaker.feature.namepack.engine.Character
import com.piptechnologies.stickermaker.feature.namepack.engine.Lettering
import com.piptechnologies.stickermaker.feature.namepack.engine.PhraseBook
import com.piptechnologies.stickermaker.feature.namepack.engine.Relation
import com.piptechnologies.stickermaker.feature.namepack.engine.StickerComposer
import com.piptechnologies.stickermaker.feature.namepack.engine.TemplateSet
import com.piptechnologies.stickermaker.feature.namepack.engine.Tone
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders 4 × 3 sheets to app/build/namepack-sheets/<lang>-<character>-<tone>.png so a person can
 * check the lettering in every script. Opt-in, since it renders 456 composites:
 * `NAMEPACK_SHEETS=1 ./gradlew :app:testDebugUnitTest --tests '*NamePackSheetsTest*' --rerun`
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class)
class NamePackSheetsTest {

    private val templates = File("src/main/assets/templates")
    private val composer = StickerComposer(Lettering(TestFonts::of))
    private val phrases = PhraseBook.parse(File(templates, "phrases.json").readText())
    private val out = File("build/namepack-sheets")

    private val names = mapOf(
        "en" to ("Aymen" to "Sara"), "ar" to ("أيمن" to "سارة"), "hi" to ("आयमन" to "प्रिया"),
        "ru" to ("Айман" to "Саша"), "he" to ("איימן" to "נועה"), "fa" to ("ایمن" to "سارا"),
        "ur" to ("ایمن" to "سارہ"), "ps" to ("ایمن" to "زرمینه"), "zh" to ("艾门" to "小雨"),
        "my" to ("အိုင်မန်" to "မေ"), "ha" to ("Aymen" to "Ɗanjuma"), "tr" to ("Aymen" to "Elif"),
        "de" to ("" to "Anastasia-Mari")
    )

    @Before
    fun onlyWhenAsked() {
        Assume.assumeTrue("set NAMEPACK_SHEETS=1 to render the contact sheets", System.getenv("NAMEPACK_SHEETS") == "1")
    }

    @Test
    fun writesSheets() {
        out.deleteRecursively()
        out.mkdirs()
        var expected = 0
        names.forEach { (lang, pair) ->
            val characters = if (lang == "en" || lang == "ar") Character.entries else listOf(Character.MANGO)
            characters.forEach { character ->
                Tone.entries.forEach { tone ->
                    sheet(lang, character, tone, pair.first, pair.second)
                    expected++
                }
            }
        }
        assertEquals(expected, out.listFiles { f -> f.name.endsWith(".png") }!!.size)
    }

    private fun sheet(lang: String, character: Character, tone: Tone, you: String, love: String) {
        val set = TemplateSet.parse(character, File(templates, "${character.id}.json").readText())
        val rtl = AppLanguages.byTag(lang)?.rtl == true
        val cell = 256
        val sheet = Bitmap.createBitmap(cell * 4, cell * 3, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet).apply { drawColor(Color.rgb(235, 238, 242)) }
        set.stickers.forEachIndexed { i, sticker ->
            val art = BitmapFactory.decodeFile(File(templates, sticker.file).path)
            val text = phrases.text(lang, tone, Relation.GIRLFRIEND, sticker.slot, you, love)
            val img = composer.compose(art, sticker, text, rtl)
            val x = (i % 4) * cell
            val y = (i / 4) * cell
            canvas.drawBitmap(img, null, Rect(x, y, x + cell, y + cell), null)
        }
        File(out, "$lang-${character.id}-${tone.id}.png").outputStream().use {
            sheet.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
