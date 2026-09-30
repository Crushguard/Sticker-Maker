package com.piptechnologies.stickermaker.core.model

/**
 * One sticker: its file name inside the pack directory plus the emoji tags
 * WhatsApp uses for search (1-3 per sticker) and, for catalog stickers, their
 * lettering as [text] ("" when the art has none).
 */
data class Sticker(
    val fileName: String,
    val emojis: List<String>,
    val text: String = "",
)
