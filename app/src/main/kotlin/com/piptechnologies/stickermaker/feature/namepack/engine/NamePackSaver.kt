package com.piptechnologies.stickermaker.feature.namepack.engine

import com.piptechnologies.stickermaker.core.data.files.OwnPackFiles
import com.piptechnologies.stickermaker.core.data.repo.MyPacksRepository
import com.piptechnologies.stickermaker.whatsapp.StickerContentProvider
import com.piptechnologies.stickermaker.whatsapp.StickerPackValidator
import com.piptechnologies.stickermaker.whatsapp.ValidatablePack
import com.piptechnologies.stickermaker.whatsapp.ValidatableSticker
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Stores a lettered pack as an own pack: validates it with the provider's metadata (as WhatsApp
 * will), writes tray + 01..12.webp atomically to `filesDir/own/<id>`, then records it in Room.
 */
class NamePackSaver(
    private val filesDir: File,
    private val repository: MyPacksRepository,
    private val publisherEmail: String,
    private val privacyPolicyWebsite: String,
) {

    /** Returns the image data version written (MyPacksRepository.saveNamePack chooses it). */
    suspend fun save(id: String, name: String, stickers: List<LetteredSticker>, trayPng: ByteArray): Int {
        val files = stickers.mapIndexed { index, sticker -> fileName(index) to sticker }
        StickerPackValidator.verifyStickerPackValidity(
            ValidatablePack(
                identifier = id,
                name = name,
                publisher = PUBLISHER,
                trayImageFile = OwnPackFiles.TRAY_FILE,
                trayBytes = trayPng,
                animatedStickerPack = false,
                stickers = files.map { (file, sticker) -> ValidatableSticker(file, sticker.webp, sticker.emojis) },
                publisherEmail = publisherEmail,
                privacyPolicyWebsite = privacyPolicyWebsite,
                androidPlayStoreLink = StickerContentProvider.ANDROID_PLAY_STORE_LINK
            )
        )
        // Files and the Room row move together: a cancelled caller (a closing screen) must not leave
        // new images under the old image_data_version, or WhatsApp would keep its stale copy.
        return withContext(NonCancellable) {
            val dir = withContext(Dispatchers.IO) {
                OwnPackFiles.write(filesDir, id, trayPng, files.map { (file, sticker) -> file to sticker.webp })
            }
            repository.saveNamePack(
                id, name, PUBLISHER, files.map { (file, sticker) -> file to sticker.emojis }, dir.absolutePath, OwnPackFiles.TRAY_FILE
            )
        }
    }

    companion object {
        /** The library packs' publisher (CatalogDataSource's default). */
        const val PUBLISHER = "PIP Technologies"

        /** 01.webp … 12.webp, always in ASCII digits. */
        fun fileName(index: Int): String = String.format(Locale.ROOT, "%02d.webp", index + 1)
    }
}
