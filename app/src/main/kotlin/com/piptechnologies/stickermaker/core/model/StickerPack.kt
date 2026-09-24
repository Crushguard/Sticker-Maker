package com.piptechnologies.stickermaker.core.model

/**
 * A remote sticker pack from the Firestore `packs` collection (catalog form).
 *
 * [trayUrl], [thumbUrls] and [stickerUrls] are resolved https download URLs,
 * ready for Coil. [trayPath] and [stickerPaths] are the raw Storage object
 * paths ("packs/{id}/01.webp") that the downloader pulls when the pack is
 * added. Catalog list emissions resolve tray + thumb URLs only; [stickerUrls]
 * may be empty until the pack is loaded individually (getPack).
 *
 * @property stickerCount Declared sticker count (thumb/url lists can be
 *   shorter while URLs are still resolving or offline).
 * @property stickerEmojis Optional catalog map of sticker file name to its
 *   emoji tags; empty when the document does not carry one.
 */
data class StickerPack(
    val id: String,
    val name: String,
    val publisher: String,
    val category: String,
    val animated: Boolean,
    val order: Int,
    val downloads: Long,
    val hue: Int,
    val stickerCount: Int,
    val trayUrl: String?,
    val thumbUrls: List<String>,
    val stickerUrls: List<String>,
    val trayPath: String = "",
    val stickerPaths: List<String> = emptyList(),
    val stickerEmojis: Map<String, List<String>> = emptyMap()
)
