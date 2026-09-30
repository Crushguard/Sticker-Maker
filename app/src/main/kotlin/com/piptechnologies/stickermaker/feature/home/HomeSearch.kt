package com.piptechnologies.stickermaker.feature.home

import com.piptechnologies.stickermaker.core.model.Category
import com.piptechnologies.stickermaker.core.model.StickerPack
import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap

/**
 * Home search: every word of the query against the pack's names, its search keywords (its tags and their words in
 * every app language, its lettering languages, phrases and sticker texts: "قطة" finds the cat packs), and the names
 * and local search words of its categories in every language.
 *
 * A short query word (under 4 letters) has to start a word ("cat" finds "cat", not "location"); a longer one may
 * sit anywhere ("kitty", "saudade"). Case, accents and Arabic spelling variants don't matter ("اسف" finds "آسف").
 */
fun matchesQuery(pack: StickerPack, query: String, categories: Map<String, Category>): Boolean {
    val words = normalize(query).split(SPACES).filter { it.isNotEmpty() }
    if (words.isEmpty()) return true
    val texts = buildList {
        add(pack.name)
        addAll(pack.names.values)
        addAll(pack.keywords)
        for (id in listOf(pack.category) + pack.alsoIn) {
            add(id)
            val category = categories[id] ?: continue
            add(category.name)
            addAll(category.names.values)
            category.keywords.values.forEach(::addAll)
        }
    }.map(::searchable)
    return words.all { word -> texts.any { it.has(word) } }
}

private val SPACES = Regex("\\s+")
private val NOT_A_WORD = Regex("[^\\p{L}\\p{M}\\p{N}]+")
private val MARKS = Regex("[\\p{Mn}\\u0640]")
private const val ANYWHERE_FROM = 4

/** A text as search reads it: normalized, and split into its words once. */
private class Searchable(val text: String) {
    val words: List<String> = text.split(NOT_A_WORD).filter { it.isNotEmpty() }

    fun has(word: String): Boolean = if (word.length >= ANYWHERE_FROM) text.contains(word) else words.any { it.startsWith(word) }
}

/** Texts repeat across packs and keystrokes (tag words, category names), so each is prepared once. */
private val prepared = ConcurrentHashMap<String, Searchable>()

private fun searchable(text: String): Searchable = prepared.getOrPut(text) { Searchable(normalize(text)) }

/**
 * Lowercase, without accents or Arabic diacritics and tatweel, with the Arabic letters people type interchangeably
 * made one: alef forms (أ إ آ ٱ → ا), ta marbuta (ة → ه), alef maqsura and Persian yeh (ى ی → ي), Persian kaf (ک → ك).
 */
private fun normalize(text: String): String =
    Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
        .replace(MARKS, "")
        .map {
            when (it) {
                'أ', 'إ', 'آ', 'ٱ' -> 'ا'
                'ة' -> 'ه'
                'ى', 'ی' -> 'ي'
                'ک' -> 'ك'
                else -> it
            }
        }
        .joinToString("")
