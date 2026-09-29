package com.piptechnologies.stickermaker.core.data.firebase

import android.net.Uri
import com.piptechnologies.stickermaker.core.data.catalog.Catalog
import com.piptechnologies.stickermaker.core.data.catalog.CatalogStore
import com.piptechnologies.stickermaker.core.data.catalog.PackArchive
import com.piptechnologies.stickermaker.core.data.di.IoDispatcher
import com.piptechnologies.stickermaker.core.model.Category
import com.piptechnologies.stickermaker.core.model.StickerPack
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn

/**
 * The remote catalog for the screens: one shared [CatalogStore] stream (a single catalog/meta listener however
 * many screens watch) plus pack files through [PackArchive]. An empty list means no catalog is available
 * (offline and nothing cached), which the screens show as their offline state.
 */
@Singleton
class CatalogDataSource @Inject constructor(
    private val store: CatalogStore,
    private val archive: PackArchive,
    @IoDispatcher ioDispatcher: CoroutineDispatcher,
) {

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val catalog: SharedFlow<Catalog?> =
        store.catalog.shareIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), replay = 1)

    /** Categories in chip order. */
    fun observeCategories(): Flow<List<Category>> = catalog.map { it?.categories.orEmpty() }

    /** Packs in rank order. */
    fun observePacks(): Flow<List<StickerPack>> = catalog.map { it?.packs.orEmpty() }

    /** Tries the catalog download again; the result arrives through the streams above. */
    fun retry() = store.retry()

    /**
     * One pack with its stickers on disk ([StickerPack.stickerUrls] are local files), downloading its zip if
     * needed; null when it is not in the catalog or cannot be fetched.
     */
    suspend fun getPack(id: String): StickerPack? {
        val pack = catalog.first()?.packs?.firstOrNull { it.id == id } ?: return null
        return try {
            withFiles(pack)
        } catch (ce: CancellationException) {
            throw ce
        } catch (failed: Exception) {
            null
        }
    }

    /** [pack] with its unpacked sticker files and emoji; throws when the download fails. */
    suspend fun withFiles(pack: StickerPack, onProgress: (Float) -> Unit = {}): StickerPack {
        val dir = archive.ensure(pack, onProgress)
        val stickers = archive.readContents(dir)
        return pack.copy(
            stickerUrls = stickers.map { Uri.fromFile(File(dir, it.fileName)).toString() },
            stickerPaths = stickers.map { File(dir, it.fileName).absolutePath },
            trayPath = File(dir, TRAY_FILE).absolutePath,
            stickerEmojis = stickers.associate { it.fileName to it.emojis },
            stickerTexts = stickers.associate { it.fileName to it.text },
        )
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val TRAY_FILE = "tray.png"
    }
}
