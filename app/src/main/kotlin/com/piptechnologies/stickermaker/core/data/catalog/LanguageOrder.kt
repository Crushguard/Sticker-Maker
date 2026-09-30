package com.piptechnologies.stickermaker.core.data.catalog

import com.piptechnologies.stickermaker.core.model.StickerPack

/**
 * The catalog's rank, readable packs first: packs with no text, in English or in many languages are for everyone,
 * and so are packs lettered in the app language; the rest follow. Rank order is kept inside each group.
 */
fun List<StickerPack>.inLanguageOrder(appLanguage: String): List<StickerPack> {
    val reader = primary(appLanguage)
    fun readable(pack: StickerPack): Boolean = pack.langs.any { primary(it).let { lang -> lang in WORLDWIDE || lang == reader } }
    return sortedBy { if (readable(it)) 0 else 1 }
}

/** No text ("none"), English, and one sticker per language ("multi"). */
private val WORLDWIDE = setOf("none", "en", "multi")

private fun primary(tag: String): String = tag.substringBefore('-').lowercase()
