package com.piptechnologies.stickermaker.core.data.repo

import com.piptechnologies.stickermaker.core.data.db.InstalledPackDao
import com.piptechnologies.stickermaker.core.data.db.InstalledPackEntity
import com.piptechnologies.stickermaker.core.data.db.OwnPackDao
import com.piptechnologies.stickermaker.core.data.db.OwnPackEntity
import com.piptechnologies.stickermaker.core.data.db.OwnStickerEntity
import com.piptechnologies.stickermaker.core.data.di.IoDispatcher
import com.piptechnologies.stickermaker.core.model.InstalledPack
import com.piptechnologies.stickermaker.core.model.OwnPack
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * The user's local packs for the My Packs screen: installed catalog packs and
 * packs created in the app, joined with their sticker rows, plus the
 * WhatsApp whitelist refresh and own-pack save/delete.
 */
@Singleton
class MyPacksRepository @Inject constructor(
    private val installedPackDao: InstalledPackDao,
    private val ownPackDao: OwnPackDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    /** Installed catalog packs with their sticker file names, newest first. */
    fun observeInstalled(): Flow<List<InstalledPack>> =
        installedPackDao.observeAll()
            .map { entities ->
                entities.map { entity -> entity.toModel(stickerFilesOf(entity.id)) }
            }
            .flowOn(ioDispatcher)

    /** Packs created in the app with their sticker file names, newest first. */
    fun observeOwn(): Flow<List<OwnPack>> =
        ownPackDao.observeAll()
            .map { entities ->
                entities.map { entity -> entity.toModel(ownStickerFilesOf(entity.id)) }
            }
            .flowOn(ioDispatcher)

    /**
     * Re-checks every pack (installed and own) against WhatsApp via [check]
     * (the pack identifier goes in) and persists changed whitelist flags.
     * A pack whose check throws keeps its previous flag.
     */
    suspend fun refreshWhitelist(check: suspend (identifier: String) -> Boolean) {
        withContext(ioDispatcher) {
            installedPackDao.getAll().forEach { pack ->
                val whitelisted = checkSafely(check, pack.id) ?: return@forEach
                if (whitelisted != pack.whitelisted) {
                    installedPackDao.setWhitelisted(pack.id, whitelisted)
                }
            }
            ownPackDao.getAll().forEach { pack ->
                val whitelisted = checkSafely(check, pack.id) ?: return@forEach
                if (whitelisted != pack.whitelisted) {
                    ownPackDao.setWhitelisted(pack.id, whitelisted)
                }
            }
        }
    }

    /**
     * Registers a pack created in the app. The files must already exist in
     * [dir] (including [trayFile]); [stickers] pairs each sticker file name
     * with its emoji tags, in pack order. Returns the generated pack id.
     */
    suspend fun saveOwnPack(
        name: String,
        publisher: String,
        animated: Boolean,
        stickers: List<Pair<String, List<String>>>,
        dir: String,
        trayFile: String
    ): String = withContext(ioDispatcher) {
        val id = "own-" + UUID.randomUUID().toString().replace("-", "").take(8)
        ownPackDao.upsertPack(
            OwnPackEntity(
                id = id,
                name = name,
                publisher = publisher,
                trayFile = trayFile,
                animated = animated,
                createdAt = System.currentTimeMillis(),
                dirPath = dir,
                whitelisted = false
            )
        )
        ownPackDao.upsertStickers(
            stickers.mapIndexed { index, (fileName, emojis) ->
                OwnStickerEntity(
                    packId = id,
                    fileName = fileName,
                    emojis = emojis.joinToString(","),
                    indexInPack = index
                )
            }
        )
        id
    }

    /** Deletes an own pack: Room rows and its directory on disk. */
    suspend fun deleteOwnPack(id: String) {
        withContext(ioDispatcher) {
            val dirPath = ownPackDao.get(id)?.dirPath
            ownPackDao.deleteStickers(id)
            ownPackDao.deletePack(id)
            dirPath?.takeIf { it.isNotBlank() }?.let { File(it).deleteRecursively() }
        }
    }

    // ------------------------------------------------------------------ //

    private suspend fun stickerFilesOf(packId: String): List<String> =
        installedPackDao.stickers(packId)
            .sortedBy { it.indexInPack }
            .map { it.fileName }

    private suspend fun ownStickerFilesOf(packId: String): List<String> =
        ownPackDao.stickers(packId)
            .sortedBy { it.indexInPack }
            .map { it.fileName }

    private suspend fun checkSafely(
        check: suspend (identifier: String) -> Boolean,
        id: String
    ): Boolean? = try {
        check(id)
    } catch (ce: CancellationException) {
        throw ce
    } catch (ignored: Exception) {
        null
    }

    private fun InstalledPackEntity.toModel(stickerFiles: List<String>) = InstalledPack(
        id = id,
        name = name,
        publisher = publisher,
        animated = animated,
        category = category,
        addedAt = addedAt,
        dir = dirPath,
        trayFile = trayFile,
        stickerFiles = stickerFiles,
        whitelisted = whitelisted
    )

    private fun OwnPackEntity.toModel(stickerFiles: List<String>) = OwnPack(
        id = id,
        name = name,
        publisher = publisher,
        animated = animated,
        createdAt = createdAt,
        dir = dirPath,
        trayFile = trayFile,
        stickerFiles = stickerFiles,
        whitelisted = whitelisted
    )
}
