package com.piptechnologies.stickermaker.core.model

/**
 * A remote pack that has been downloaded to local storage and inserted into
 * Room, ready to be served to WhatsApp.
 *
 * @property addedAt Epoch millis of the install, newest first in My Packs.
 * @property dir Absolute path of the local pack directory (filesDir/packs/{id}).
 * @property stickerFiles Sticker file names inside [dir], in pack order.
 * @property whitelisted True once WhatsApp reports the pack as added.
 */
data class InstalledPack(
    val id: String,
    val name: String,
    val publisher: String,
    val animated: Boolean,
    val category: String,
    val addedAt: Long,
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
