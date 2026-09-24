package com.piptechnologies.stickermaker.core.model

/**
 * A pack the user created with the in-app editor. Lives entirely in Room +
 * filesDir and goes through the same add-to-WhatsApp pipeline as remote packs.
 *
 * @property createdAt Epoch millis of creation, newest first in My Packs.
 * @property dir Absolute path of the local pack directory.
 * @property stickerFiles Sticker file names inside [dir], in pack order.
 * @property whitelisted True once WhatsApp reports the pack as added.
 */
data class OwnPack(
    val id: String,
    val name: String,
    val publisher: String,
    val animated: Boolean,
    val createdAt: Long,
    val dir: String,
    val trayFile: String,
    val stickerFiles: List<String>,
    val whitelisted: Boolean
) {
    /** Absolute path of the tray image file. */
    val trayFilePath: String get() = "$dir/$trayFile"

    /** Absolute path of one sticker file inside the pack directory. */
    fun stickerFilePath(fileName: String): String = "$dir/$fileName"
}
