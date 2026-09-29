package com.piptechnologies.stickermaker.core.data.download

import android.content.Context
import com.piptechnologies.stickermaker.BuildConfig
import com.piptechnologies.stickermaker.core.data.catalog.PackArchive
import com.piptechnologies.stickermaker.core.data.di.IoDispatcher
import com.piptechnologies.stickermaker.core.model.StickerPack
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * Debug-only pause after each installed file, set by the on-device screen tour
 * to hold the Downloading state long enough to capture it. Never read in
 * release builds.
 */
object DownloadPacing {
    @Volatile
    var perFileDelayMs: Long = 0L
}

/** Progress of a pack download: whole files completed out of [totalFiles]. */
data class DownloadProgress(
    val downloadedFiles: Int,
    val totalFiles: Int,
    val fraction: Float
)

/**
 * Installs a catalog pack into filesDir/packs/{packId}/ (tray.png, 01.webp …) for the
 * StickerContentProvider: its pack.zip is fetched once through [PackArchive] (reused when the pack
 * page already fetched it), then copied into a "{dir}.tmp" sibling that is renamed into place only
 * when complete, so a visible pack directory is always whole.
 */
@Singleton
class PackDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val archive: PackArchive,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    /** Local directory a (possibly not yet downloaded) pack lives in. */
    fun packDir(packId: String): File = File(packsRoot(), packId)

    /**
     * Cold flow that fetches and installs [pack], emitting progress (the download is most of it).
     * Throws (to the collector) on any failure.
     */
    fun download(pack: StickerPack): Flow<DownloadProgress> = channelFlow {
        send(DownloadProgress(0, 1, 0f))
        val source = archive.ensure(pack) { fraction ->
            trySend(DownloadProgress(0, 1, fraction * DOWNLOAD_SHARE))
        }
        val files = listOf(TRAY_FILE) + archive.readContents(source).map { it.fileName }
        val finalDir = packDir(pack.id)
        val tmpDir = File(packsRoot(), "${pack.id}.tmp")
        var completed = false
        try {
            tmpDir.deleteRecursively()
            if (!tmpDir.mkdirs()) throw IOException("Could not create ${tmpDir.absolutePath}")
            files.forEachIndexed { index, name ->
                File(source, name).copyTo(File(tmpDir, name), overwrite = true)
                val done = index + 1
                send(DownloadProgress(done, files.size, DOWNLOAD_SHARE + (1f - DOWNLOAD_SHARE) * done / files.size))
                if (BuildConfig.DEBUG && DownloadPacing.perFileDelayMs > 0) {
                    delay(DownloadPacing.perFileDelayMs)
                }
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

    private companion object {
        const val TRAY_FILE = "tray.png"
        const val DOWNLOAD_SHARE = 0.9f
    }
}
