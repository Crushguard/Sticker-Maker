package com.piptechnologies.stickermaker.feature.create.decor

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import androidx.core.content.res.ResourcesCompat
import com.piptechnologies.stickermaker.R
import com.piptechnologies.stickermaker.feature.namepack.engine.LetteringFont
import com.piptechnologies.stickermaker.feature.namepack.engine.LetteringFonts
import java.util.concurrent.ConcurrentHashMap

/** The font files a text layer can be lettered in (spec §5): the UI font, the lettering fonts and the system bold. */
enum class FontFile {
    UI, BALOO, BALOO_BHAIJAAN, RUBIK, SYSTEM_BOLD,
    CAVEAT, MIRZA, KALAM, AMATIC, LILITA, RUSLAN, LALEZAR, YATRA, KARANTINA
}

/** A loaded face; [fakeBold] asks for synthetic bold (Caveat on API 24-25, where its weight axis can't be set). */
data class FontFace(val typeface: Typeface, val fakeBold: Boolean = false)

/**
 * The face of a text layer, picked from the string and never from the UI language (spec §5):
 * Classic uses the UI font; Rounded keeps the Custom Stickers choice ([LetteringFonts.choose],
 * with its Pashto rule); Hand and Display follow `text-styles.json` per script.
 * Faces come from [load] once per file, so tests can pass fonts read from the source tree.
 */
class DecorFonts(private val book: TextStyleBook, private val load: (FontFile) -> FontFace) {

    private val faces = ConcurrentHashMap<FontFile, FontFace>()

    /** The face of [file], loaded on first use. */
    fun face(file: FontFile): FontFace = faces.getOrPut(file) { load(file) }

    /** The face for [text] in a style that uses the UI font ([uiFont]) or the lettering font of [mood]. */
    fun forText(text: String, uiFont: Boolean, mood: FontMood): FontFace = face(fileFor(text, uiFont, mood, book))

    companion object {
        private const val CAVEAT_ASSET = "fonts/lettering_caveat.ttf"

        /** `text-styles.json` family names of the Hand and Display moods. */
        private val FAMILIES = mapOf(
            "Caveat" to FontFile.CAVEAT, "Mirza" to FontFile.MIRZA, "Kalam" to FontFile.KALAM,
            "Amatic SC" to FontFile.AMATIC, "Lilita One" to FontFile.LILITA, "Ruslan Display" to FontFile.RUSLAN,
            "Lalezar" to FontFile.LALEZAR, "Yatra One" to FontFile.YATRA, "Karantina" to FontFile.KARANTINA
        )

        /** The file for [text] in a style that uses the UI font ([uiFont]) or the lettering font of [mood]. */
        fun fileFor(text: String, uiFont: Boolean, mood: FontMood, book: TextStyleBook): FontFile {
            if (uiFont) return FontFile.UI
            if (mood != FontMood.Round) {
                FAMILIES[book.moods[mood]?.get(scriptOf(text))?.family]?.let { return it }
            }
            // Rounded, and the fallback for a family the table doesn't know.
            return when (LetteringFonts.choose(text)) {
                LetteringFont.BALOO -> FontFile.BALOO
                LetteringFont.BALOO_BHAIJAAN -> FontFile.BALOO_BHAIJAAN
                LetteringFont.RUBIK -> FontFile.RUBIK
                LetteringFont.SYSTEM_BOLD -> FontFile.SYSTEM_BOLD
            }
        }

        /** The strongest script in [text]: any Arabic letter wins, then Hebrew, Cyrillic, Devanagari, else Latin. */
        fun scriptOf(text: String): Script {
            val cps = text.codePoints().toArray()
            return when {
                cps.any(::isArabicScript) -> Script.Arabic
                cps.any { it in 0x0590..0x05FF } -> Script.Hebrew
                cps.any { it in 0x0400..0x04FF } -> Script.Cyrillic
                cps.any { it in 0x0900..0x097F } -> Script.Devanagari
                else -> Script.Latin
            }
        }

        /** Production fonts: `res/font` resources, Caveat from assets at weight 700, the system bold. */
        fun android(context: Context, book: TextStyleBook): DecorFonts {
            val app = context.applicationContext
            return DecorFonts(book) { file ->
                when (file) {
                    FontFile.SYSTEM_BOLD -> FontFace(Typeface.DEFAULT_BOLD)
                    FontFile.CAVEAT -> caveat(app)
                    else -> FontFace(ResourcesCompat.getFont(app, resourceOf(file)) ?: Typeface.DEFAULT_BOLD)
                }
            }
        }

        /** Caveat ships as a variable font; its weight axis needs API 26, so older phones get fake bold (§13.9). */
        private fun caveat(context: Context): FontFace =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                FontFace(Typeface.Builder(context.assets, CAVEAT_ASSET).setFontVariationSettings("'wght' 700").build())
            } else {
                FontFace(Typeface.createFromAsset(context.assets, CAVEAT_ASSET), fakeBold = true)
            }

        private fun resourceOf(file: FontFile): Int = when (file) {
            FontFile.UI -> R.font.hg_extrabold
            FontFile.BALOO -> R.font.lettering_baloo2
            FontFile.BALOO_BHAIJAAN -> R.font.lettering_baloo_bhaijaan2
            FontFile.RUBIK -> R.font.lettering_rubik
            FontFile.MIRZA -> R.font.lettering_mirza
            FontFile.KALAM -> R.font.lettering_kalam
            FontFile.AMATIC -> R.font.lettering_amaticsc
            FontFile.LILITA -> R.font.lettering_lilitaone
            FontFile.RUSLAN -> R.font.lettering_ruslandisplay
            FontFile.LALEZAR -> R.font.lettering_lalezar
            FontFile.YATRA -> R.font.lettering_yatraone
            FontFile.KARANTINA -> R.font.lettering_karantina
            FontFile.SYSTEM_BOLD, FontFile.CAVEAT -> error("$file is not a font resource")
        }

        private fun isArabicScript(cp: Int): Boolean =
            cp in 0x0600..0x06FF || cp in 0x0750..0x077F || cp in 0x08A0..0x08FF ||
                cp in 0xFB50..0xFDFF || cp in 0xFE70..0xFEFF
    }
}
