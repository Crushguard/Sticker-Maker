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
 * sit anywhere ("kitty", "saudade"). Case, accents, punctuation and Arabic spelling variants don't matter ("اسف"
 * finds "آسف", "u up?" finds "u up").
 */
fun matchesQuery(pack: StickerPack, query: String, categories: Map<String, Category>): Boolean =
    matches(searchTexts(pack, categories), queryWords(query))

/**
 * Every pack's search texts, prepared once per catalog (Home rebuilds its state on every keystroke). Packs missing
 * from the index are prepared on the fly.
 */
class HomeSearchIndex(packs: List<StickerPack>, categories: List<Category>) {
    private val categories = categories.associateBy { it.id }
    private val texts: Map<String, List<Searchable>> = packs.associate { it.id to searchTexts(it, this.categories) }

    fun filter(packs: List<StickerPack>, query: String): List<StickerPack> {
        val words = queryWords(query)
        if (words.isEmpty()) return packs
        return packs.filter { pack -> matches(texts[pack.id] ?: searchTexts(pack, categories), words) }
    }
}

private fun queryWords(query: String): List<String> = normalize(query).split(NOT_A_WORD).filter { it.isNotEmpty() }

private fun matches(texts: List<Searchable>, words: List<String>): Boolean =
    words.all { word -> texts.any { it.has(word) } }

private fun searchTexts(pack: StickerPack, categories: Map<String, Category>): List<Searchable> = buildList {
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

private val NOT_A_WORD = Regex("[^\\p{L}\\p{M}\\p{N}]+")
private val MARKS = Regex("[\\p{Mn}\\u0640]")
private const val ANYWHERE_FROM = 4

/** A text as search reads it: normalized, and split into its words once. */
private class Searchable(val text: String) {
    val words: List<String> = text.split(NOT_A_WORD).filter { it.isNotEmpty() }

    fun has(word: String): Boolean = if (word.length >= ANYWHERE_FROM) text.contains(word) else words.any { it.startsWith(word) }
}

/** Texts repeat across packs and catalogs (tag words, category names), so each is prepared once. */
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
