package com.piptechnologies.stickermaker.feature.home

import com.piptechnologies.stickermaker.core.model.Category
import com.piptechnologies.stickermaker.core.model.StickerPack

/**
 * Home search: the query (case-insensitive, any word prefix or substring) against the pack's names, its
 * search keywords (tags and sticker texts), and the names and local search words of its categories in
 * every language ("saudade" finds Miss you packs).
 */
fun matchesQuery(pack: StickerPack, query: String, categories: Map<String, Category>): Boolean {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return true
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
    }
    return texts.any { it.lowercase().contains(q) }
}
