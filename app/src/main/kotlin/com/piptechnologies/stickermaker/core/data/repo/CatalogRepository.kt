package com.piptechnologies.stickermaker.core.data.repo

import com.piptechnologies.stickermaker.core.data.db.InstalledPackDao
import com.piptechnologies.stickermaker.core.data.db.InstalledPackEntity
import com.piptechnologies.stickermaker.core.data.db.InstalledStickerEntity
import com.piptechnologies.stickermaker.core.data.di.IoDispatcher
import com.piptechnologies.stickermaker.core.data.download.PackDownloader
import com.piptechnologies.stickermaker.core.data.firebase.CatalogDataSource
import com.piptechnologies.stickermaker.core.model.AddState
import com.piptechnologies.stickermaker.core.model.Category
import com.piptechnologies.stickermaker.core.model.StickerPack
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * The remote catalog combined with what is installed locally: streams for
 * Home/Detail plus the add/remove pipeline (download -> Room; the ADD_PACK
 * intent itself is fired by the UI layer once [AddState.Sent] is reached).
 */
@Singleton
class CatalogRepository @Inject constructor(
    private val catalog: CatalogDataSource,
    private val installedPackDao: InstalledPackDao,
    private val downloader: PackDownloader,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    /** Remote categories, sorted by order. */
    fun observeCategories(): Flow<List<Category>> = catalog.observeCategories()

    /** Remote packs, sorted by order, with tray + thumb URLs resolved. */
    fun observePacks(): Flow<List<StickerPack>> = catalog.observePacks()

    /** Ids of packs currently installed locally (for ADDED badges). */
    fun observeInstalledIds(): Flow<Set<String>> =
        installedPackDao.observeAll()
            .map { packs -> packs.map { it.id }.toSet() }
            .distinctUntilChanged()

    /** One pack with all URLs resolved; null when unavailable. */
    suspend fun getPack(id: String): StickerPack? = catalog.getPack(id)

    /**
     * Downloads [pack] and registers it in Room. Emits
     * [AddState.Downloading] with fraction 0f..1f while files arrive, then a
     * final [AddState.Sent] once files + rows are ready for the ADD_PACK
     * intent. Any error surfaces as [AddState.Failed].
     */
    fun addPack(pack: StickerPack): Flow<AddState> = flow<AddState> {
        emit(AddState.Downloading(0f))
        downloader.download(pack).collect { progress ->
            emit(AddState.Downloading(progress.fraction))
        }
        val emojis = pack.stickerEmojis.ifEmpty {
            catalog.getPack(pack.id)?.stickerEmojis.orEmpty()
        }
        insertInstalled(pack, emojis)
        emit(AddState.Sent)
    }
        .catch { error ->
            if (error is CancellationException) throw error
            emit(AddState.Failed(error.message))
        }
        .flowOn(ioDispatcher)

    /** Removes an installed pack: Room rows first, then the local files. */
    suspend fun removePack(id: String) {
        withContext(ioDispatcher) {
            installedPackDao.deleteStickers(id)
            installedPackDao.deletePack(id)
            downloader.delete(id)
        }
    }

    /** Records whether WhatsApp reports the installed pack as added. */
    suspend fun setWhitelisted(id: String, whitelisted: Boolean) {
        installedPackDao.setWhitelisted(id, whitelisted)
    }

    private suspend fun insertInstalled(pack: StickerPack, emojis: Map<String, List<String>>) {
        val trayFile = pack.trayPath.substringAfterLast('/').ifBlank { "tray.png" }
        val stickerFiles = pack.stickerPaths
            .map { it.substringAfterLast('/') }
            .filter { it.isNotBlank() }
            .ifEmpty {
                (1..pack.stickerCount).map { "%02d.webp".format(Locale.ROOT, it) }
            }
        installedPackDao.upsertPack(
            InstalledPackEntity(
                id = pack.id,
                name = pack.name,
                publisher = pack.publisher,
                trayFile = trayFile,
                animated = pack.animated,
                category = pack.category,
                sortOrder = pack.order,
                addedAt = System.currentTimeMillis(),
                dirPath = downloader.packDir(pack.id).absolutePath,
                whitelisted = false
            )
        )
        installedPackDao.deleteStickers(pack.id)
        installedPackDao.upsertStickers(
            stickerFiles.mapIndexed { index, fileName ->
                InstalledStickerEntity(
                    packId = pack.id,
                    fileName = fileName,
                    emojis = (emojis[fileName] ?: DEFAULT_EMOJIS).joinToString(","),
                    indexInPack = index
                )
            }
        )
    }

    private companion object {
        /** Fallback emoji tags when the catalog carries none for a sticker. */
        val DEFAULT_EMOJIS = listOf("❤️", "😊")
    }
}
