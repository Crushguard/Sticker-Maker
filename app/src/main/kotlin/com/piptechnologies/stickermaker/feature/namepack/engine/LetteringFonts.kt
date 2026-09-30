package com.piptechnologies.stickermaker.feature.namepack.engine

/** The bundled lettering faces (all weight 800), plus the system bold for gaps. */
enum class LetteringFont { BALOO, BALOO_BHAIJAAN, RUBIK, SYSTEM_BOLD }

/**
 * Picks the face from the string (spec, Rendering): Arabic script → Baloo Bhaijaan 2, unless the
 * string holds a Pashto letter it lacks; Hebrew or Cyrillic → Rubik; anything else → Baloo 2.
 * Scripts none of them draw (Chinese, Burmese, Hausa hook letters) fall back per glyph.
 */
object LetteringFonts {

    /**
     * ټ ځ څ ډ ړ ږ ښ ګ ڼ ۍ ې: Pashto letters absent from Baloo Bhaijaan 2. A string holding one is lettered in the
     * system bold face whole, never with one letter from another font (LetteringFontsTest keeps
     * this list true and complete for the shipped phrases).
     */
    val PASHTO_GAPS: Set<Int> = setOf(
        0x067C, 0x0681, 0x0685, 0x0689, 0x0693, 0x0696, 0x069A, 0x06AB, 0x06BC, 0x06CD, 0x06D0
    )

    fun choose(text: String): LetteringFont {
        val cps = text.codePoints().toArray()
        return when {
            cps.any(::isArabicScript) ->
                if (cps.any { it in PASHTO_GAPS }) LetteringFont.SYSTEM_BOLD else LetteringFont.BALOO_BHAIJAAN
            cps.any { isHebrew(it) || isCyrillic(it) } -> LetteringFont.RUBIK
            else -> LetteringFont.BALOO
        }
    }

    /**
     * Paragraph direction of a phrase: right to left in RTL app languages (their phrases are
     * RTL even when a Latin name comes first), else set by the first strong character.
     */
    fun isRtlParagraph(text: String, rtlLanguage: Boolean): Boolean {
        if (rtlLanguage) return true
        for (cp in text.codePoints().toArray()) {
            // Fully qualified: in this package `Character` is the name-pack enum (NameModel.kt).
            when (java.lang.Character.getDirectionality(cp)) {
                java.lang.Character.DIRECTIONALITY_LEFT_TO_RIGHT -> return false
                java.lang.Character.DIRECTIONALITY_RIGHT_TO_LEFT, java.lang.Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC -> return true
            }
        }
        return false
    }

    private fun isArabicScript(cp: Int): Boolean =
        cp in 0x0600..0x06FF || cp in 0x0750..0x077F || cp in 0x08A0..0x08FF ||
            cp in 0xFB50..0xFDFF || cp in 0xFE70..0xFEFF

    private fun isHebrew(cp: Int): Boolean = cp in 0x0590..0x05FF

    private fun isCyrillic(cp: Int): Boolean = cp in 0x0400..0x04FF
}
