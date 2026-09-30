package com.piptechnologies.stickermaker.feature.namepack.engine

import java.text.BreakIterator
import java.util.Locale

/**
 * The name rules of the Custom Stickers flow (spec, "Names"): at most 14 grapheme
 * clusters and 60 UTF-16 units; letters, marks, digits, spaces, apostrophes, hyphens, dots
 * and ZWNJ; lettered verbatim after dropping invisible direction and format marks, trimming
 * and collapsing spaces. On Android java.text.BreakIterator is ICU, so clusters follow
 * Unicode's rules.
 */
object NameInput {

    const val MAX_GRAPHEMES = 14

    /** UTF-16 units per name: both names and the heart between them stay within WhatsApp's 128 for a pack name. */
    const val MAX_CHARS = 60

    private val SPACES = Regex("[\\s\\u00A0\\u2007\\u202F]+")

    /**
     * Marks a copied name can carry invisibly: LRM, RLM, the Arabic letter mark, the direction
     * embeddings, overrides and isolates, zero-width space, word joiner and BOM. Never ZWNJ or
     * ZWJ, which shape letters.
     */
    private val INVISIBLE = Regex("[\\u200E\\u200F\\u061C\\u202A-\\u202E\\u2066-\\u2069\\u200B\\u2060\\uFEFF]")

    /** Space, apostrophes (U+0027, U+2019, U+2018), hyphens (U+002D, U+2010), dot, Hebrew geresh/gershayim, ZWNJ. */
    private val NAME_PUNCTUATION = setOf(
        0x20, 0x27, 0x2019, 0x2018, 0x2D, 0x2010, 0x2E, 0x05F3, 0x05F4, 0x200C
    )

    /** [raw] without [INVISIBLE] marks: a pasted name would otherwise be refused with nothing visible to delete. */
    fun stripInvisible(raw: String): String = raw.replace(INVISIBLE, "")

    /** Drops invisible marks, trims and collapses every run of spaces to one ASCII space. */
    fun normalize(raw: String): String = stripInvisible(raw).replace(SPACES, " ").trim()

    /** Grapheme clusters in [text]: what a reader counts as characters. */
    fun graphemeCount(text: String): Int {
        if (text.isEmpty()) return 0
        val iterator = BreakIterator.getCharacterInstance().apply { setText(text) }
        var count = 0
        while (iterator.next() != BreakIterator.DONE) count++
        return count
    }

    /**
     * [raw] cut to its first [MAX_GRAPHEMES] clusters, and at the last whole cluster that keeps it
     * within [MAX_CHARS] UTF-16 units (typing stops there; a paste can overshoot).
     */
    fun clamp(raw: String): String {
        val iterator = BreakIterator.getCharacterInstance().apply { setText(raw) }
        var end = 0
        repeat(MAX_GRAPHEMES) {
            val next = iterator.next()
            if (next == BreakIterator.DONE || next > MAX_CHARS) return raw.substring(0, end)
            end = next
        }
        return raw.substring(0, end)
    }

    /** Whether every character can be lettered: no emoji, pictographs, symbols or other punctuation. */
    fun isLetterable(text: String): Boolean = text.codePoints().allMatch { cp ->
        java.lang.Character.isLetter(cp) || java.lang.Character.isDigit(cp) || cp in NAME_PUNCTUATION || isNameMark(cp)
    }

    /** First grapheme of [name], upper-cased in [locale] where the script has case (tray initials). */
    fun initial(name: String, locale: Locale): String {
        val trimmed = normalize(name)
        if (trimmed.isEmpty()) return ""
        val iterator = BreakIterator.getCharacterInstance().apply { setText(trimmed) }
        val end = iterator.next().takeIf { it != BreakIterator.DONE } ?: trimmed.length
        return trimmed.substring(0, end).uppercase(locale)
    }

    /** Vowel signs and diacritics, but not the variation selectors emoji ride on. */
    private fun isNameMark(cp: Int): Boolean {
        if (cp in 0xFE00..0xFE0F || cp in 0xE0100..0xE01EF) return false
        // Fully qualified: in this package `Character` is the name-pack enum (NameModel.kt).
        val type = java.lang.Character.getType(cp)
        return type == java.lang.Character.NON_SPACING_MARK.toInt() || type == java.lang.Character.COMBINING_SPACING_MARK.toInt()
    }
}
