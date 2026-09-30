package com.piptechnologies.stickermaker.core.model

/**
 * The eight themes exactly as design/catalog.json ships them (id, name, icon,
 * hue, order). Home uses them whenever the remote `categories` collection is
 * empty (offline first run, Firestore error), so its chip row always renders.
 */
val FallbackCategories: List<Category> = listOf(
    Category(id = "couples", name = "Couples", icon = "heart-handshake", hue = 10, order = 1),
    Category(id = "cute", name = "Cute", icon = "rabbit", hue = 330, order = 2),
    Category(id = "funny", name = "Funny", icon = "laugh", hue = 85, order = 3),
    Category(id = "anime", name = "Anime", icon = "sparkles", hue = 300, order = 4),
    Category(id = "romantic", name = "Romantic", icon = "flower-2", hue = 45, order = 5),
    Category(id = "flirty", name = "Flirty", icon = "message-circle-heart", hue = 200, order = 6),
    Category(id = "goodnight", name = "Good night", icon = "moon", hue = 250, order = 7),
    Category(id = "distance", name = "Long distance", icon = "plane", hue = 150, order = 8)
)
