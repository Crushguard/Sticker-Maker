package com.piptechnologies.stickermaker.core.model

/**
 * A catalog category (theme) from the Firestore `categories` collection.
 *
 * @property icon Lucide icon name as the design uses it (e.g. "heart-handshake").
 * @property hue Hue in degrees used to tint chips and theme tiles.
 * @property order Ascending display order in chips and the customization grid.
 */
data class Category(
    val id: String,
    val name: String,
    val icon: String,
    val hue: Int,
    val order: Int
)
