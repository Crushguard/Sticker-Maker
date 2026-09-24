package com.piptechnologies.stickermaker.core.data.download

import android.content.Context
import com.google.firebase.storage.FirebaseStorage
import com.piptechnologies.stickermaker.core.data.di.IoDispatcher
import com.piptechnologies.stickermaker.core.model.StickerPack
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/** Progress of a pack download: whole files completed out of [totalFiles]. */
data class DownloadProgress(
    val downloadedFiles: Int,
    val totalFiles: Int,
    val fraction: Float
)

/**
 * Downloads a remote pack from Firebase Storage into
 * filesDir/packs/{packId}/ keeping the Storage file names (tray.png, NN.webp).
 *
 * Files land in a "{dir}.tmp" sibling first; the directory is renamed into
 * place only after every file arrived, so a visible pack directory is always
 * complete. The temp directory is removed on failure or cancellation.
 */
@Singleton
class PackDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storage: FirebaseStorage,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    /** Local directory a (possibly not yet downloaded) pack lives in. */
    fun packDir(packId: String): File = File(packsRoot(), packId)

    /**
     * Cold flow that downloads tray + stickers for [pack], emitting progress
     * after each completed file. Throws (to the collector) on any failure.
     */
    fun download(pack: StickerPack): Flow<DownloadProgress> = flow {
        val paths = storagePaths(pack)
        val finalDir = packDir(pack.id)
        val tmpDir = File(packsRoot(), "${pack.id}.tmp")
        var completed = false
        try {
            tmpDir.deleteRecursively()
            if (!tmpDir.mkdirs()) {
                throw IOException("Could not create ${tmpDir.absolutePath}")
            }
            emit(DownloadProgress(0, paths.size, 0f))
            paths.forEachIndexed { index, path ->
                val fileName = path.substringAfterLast('/')
                storage.getReference(path).getFile(File(tmpDir, fileName)).await()
                val done = index + 1
                emit(DownloadProgress(done, paths.size, done.toFloat() / paths.size))
            }
            if (finalDir.exists() && !finalDir.deleteRecursively()) {
                throw IOException("Could not replace ${finalDir.absolutePath}")
            }
            if (!tmpDir.renameTo(finalDir)) {
                throw IOException("Could not move pack ${pack.id} into place")
            }
            completed = true
        } finally {
            if (!completed) tmpDir.deleteRecursively()
        }
    }.flowOn(ioDispatcher)

    /** Removes the local files of [packId] (final and temp directories). */
    suspend fun delete(packId: String) {
        withContext(ioDispatcher) {
            packDir(packId).deleteRecursively()
            File(packsRoot(), "$packId.tmp").deleteRecursively()
        }
    }

    private fun packsRoot(): File = File(context.filesDir, "packs")

    /** Tray first, then stickers; falls back to the locked Storage layout. */
    private fun storagePaths(pack: StickerPack): List<String> {
        val tray = pack.trayPath.ifBlank { "packs/${pack.id}/tray.png" }
        val stickers = pack.stickerPaths.ifEmpty {
            (1..pack.stickerCount).map { index ->
                "packs/${pack.id}/" + "%02d.webp".format(Locale.ROOT, index)
            }
        }
        check(stickers.isNotEmpty()) { "Pack ${pack.id} has no stickers to download" }
        return listOf(tray) + stickers
    }
}
