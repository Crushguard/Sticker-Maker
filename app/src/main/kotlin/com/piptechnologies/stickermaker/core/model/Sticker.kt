package com.piptechnologies.stickermaker.core.model

/**
 * One sticker: its file name inside the pack directory plus the emoji tags
 * WhatsApp uses for search (1-3 per sticker).
 */
data class Sticker(
    val fileName: String,
    val emojis: List<String>
)
