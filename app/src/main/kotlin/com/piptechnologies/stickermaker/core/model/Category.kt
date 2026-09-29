package com.piptechnologies.stickermaker.core.model

/**
 * A catalog category (theme): one Home chip and one library folder.
 *
 * @property name English name; [names] holds it in every app language (BCP 47 keys).
 * @property icon Lucide icon name as the design uses it (e.g. "heart-handshake").
 * @property hue Hue in degrees used to tint chips and theme tiles.
 * @property order Ascending display order in chips.
 * @property keywords Local search words by language ("saudade" finds Miss you).
 * @property packCount Live packs in it, counting packs that list it in alsoIn; 0 hides the chip.
 */
data class Category(
    val id: String,
    val name: String,
    val icon: String,
    val hue: Int,
    val order: Int,
    val names: Map<String, String> = emptyMap(),
    val keywords: Map<String, List<String>> = emptyMap(),
    val packCount: Int = -1,
)

/** Categories to show as chips: those with live packs (an unknown count, -1, counts as some). */
fun List<Category>.withPacks(): List<Category> = filter { it.packCount != 0 }
