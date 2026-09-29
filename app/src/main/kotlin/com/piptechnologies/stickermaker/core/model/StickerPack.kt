package com.piptechnologies.stickermaker.core.model

/**
 * A pack of the remote catalog (the catalog file, see CatalogFile).
 *
 * The Home card draws its six circles from one cover strip ([coverSmallUrl] for screens up to 2x,
 * [coverLargeUrl] above, [coverTiles] tiles side by side). Everything else lives in one download,
 * [zipUrl]: tray, stickers and each sticker's emoji. Once that zip is unpacked, [stickerUrls] are the
 * local sticker files and [stickerEmojis] their emoji tags by file name.
 *
 * @property order Rank in the catalog (0 = first): pinned, then popular, then newest.
 * @property downloads Real adds counted by Analytics (0 until counted); shown only from 100.
 * @property version WhatsApp's image_data_version: a new version means new files.
 * @property lang Language of the lettering (BCP 47), or "none" for text-free art.
 * @property keywords Search words: tags plus the words of every sticker's text.
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
    val stickerUrls: List<String> = emptyList(),
    val trayPath: String = "",
    val stickerPaths: List<String> = emptyList(),
    val stickerEmojis: Map<String, List<String>> = emptyMap(),
    val stickerTexts: Map<String, String> = emptyMap(),
    val version: Int = 1,
    val names: Map<String, String> = emptyMap(),
    val alsoIn: List<String> = emptyList(),
    val lang: String = "en",
    val keywords: List<String> = emptyList(),
    val coverSmallUrl: String? = null,
    val coverLargeUrl: String? = null,
    val coverTiles: Int = 0,
    val zipUrl: String? = null,
    val zipBytes: Long = 0,
)

/** The pack shows under its folder category and every category it lists in alsoIn. */
fun StickerPack.inCategory(categoryId: String): Boolean = category == categoryId || categoryId in alsoIn
