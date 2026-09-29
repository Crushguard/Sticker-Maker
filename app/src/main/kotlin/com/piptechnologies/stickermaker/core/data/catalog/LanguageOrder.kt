package com.piptechnologies.stickermaker.core.data.catalog

import com.piptechnologies.stickermaker.core.model.StickerPack

/**
 * The catalog's rank, grouped by what the reader can read: packs lettered in the app language (or with no
 * text) first, then English, then the rest. Rank order is kept inside each group.
 */
fun List<StickerPack>.inLanguageOrder(appLanguage: String): List<StickerPack> {
    val reader = primary(appLanguage)
    fun group(pack: StickerPack): Int = when {
        pack.lang == TEXT_FREE || primary(pack.lang) == reader -> 0
        primary(pack.lang) == ENGLISH -> 1
        else -> 2
    }
    return sortedBy(::group)
}

private const val TEXT_FREE = "none"
private const val ENGLISH = "en"

private fun primary(tag: String): String = tag.substringBefore('-').lowercase()
