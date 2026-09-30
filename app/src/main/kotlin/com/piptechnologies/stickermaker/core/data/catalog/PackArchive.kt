package com.piptechnologies.stickermaker.core.data.catalog

import com.piptechnologies.stickermaker.core.model.Sticker
import com.piptechnologies.stickermaker.core.model.StickerPack
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * One download per pack version: pack.zip (contents.json, tray.png, 01.webp …) unpacked into
 * [root]/<id>-<version>/. The pack page previews from it and Add copies from it, so a pack is never
 * downloaded twice. Unpacking goes through a temp folder, so a visible folder is always complete.
 */
class PackArchive(
    private val root: File,
    private val fetcher: HttpFetcher,
    private val io: CoroutineDispatcher,
) {

    fun dirFor(pack: StickerPack): File = File(root, "${pack.id}-${pack.version}")

    /** The unpacked pack, downloading it first when needed; [onProgress] gets 0..1 by bytes. */
    suspend fun ensure(pack: StickerPack, onProgress: (Float) -> Unit = {}): File = withContext(io) {
        val dir = dirFor(pack)
        if (File(dir, COMPLETE).isFile) return@withContext dir
        val url = pack.zipUrl ?: throw IOException("Pack ${pack.id} has no download")
        root.mkdirs()
        val tmp = File(root, "${dir.name}.tmp")
        tmp.deleteRecursively()
        try {
            if (!tmp.mkdirs()) throw IOException("Could not create ${tmp.path}")
            fetcher.open(url) { input ->
                val counted = CountingInputStream(input) { read ->
                    if (pack.zipBytes > 0) onProgress((read.toFloat() / pack.zipBytes).coerceIn(0f, 1f))
                }
                unzip(counted, tmp)
            }
            if (!File(tmp, CONTENTS).isFile) throw IOException("Pack ${pack.id} has no contents.json")
            File(tmp, COMPLETE).createNewFile()
            dir.deleteRecursively()
            if (!tmp.renameTo(dir)) throw IOException("Could not move pack ${pack.id} into place")
            removeOtherVersions(pack, dir)
            onProgress(1f)
            dir
        } finally {
            tmp.deleteRecursively()
        }
    }

    /** Stickers in pack order with their emoji, from the folder's contents.json. */
    fun readContents(dir: File): List<Sticker> {
        val stickers = JSONObject(File(dir, CONTENTS).readText()).optJSONArray("stickers")
            ?: return emptyList()
        return (0 until stickers.length()).mapNotNull { index ->
            val sticker = stickers.optJSONObject(index) ?: return@mapNotNull null
            val file = sticker.optString("file").ifBlank { return@mapNotNull null }
            val emojis = sticker.optJSONArray("emojis")
            Sticker(
                fileName = file,
                emojis = (0 until (emojis?.length() ?: 0)).mapNotNull { emojis?.optString(it)?.ifBlank { null } },
                text = sticker.optString("text"),
            )
        }
    }

    /** Deletes every unpacked pack (Settings › Clear downloads). */
    suspend fun clear() = withContext(io) { root.deleteRecursively() }

    private fun unzip(input: InputStream, into: File) {
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory || !SAFE_NAME.matches(entry.name)) {
                    throw IOException("Unexpected entry ${entry.name}")
                }
                File(into, entry.name).outputStream().use { zip.copyTo(it) }
            }
        }
    }

    private fun removeOtherVersions(pack: StickerPack, keep: File) {
        val sameId = Regex("^${Regex.escape(pack.id)}-\\d+$")
        root.listFiles()?.filter { it != keep && sameId.matches(it.name) }?.forEach { it.deleteRecursively() }
    }

    private class CountingInputStream(input: InputStream, private val onRead: (Long) -> Unit) : FilterInputStream(input) {
        private var total = 0L

        override fun read(): Int = super.read().also { if (it >= 0) count(1) }

        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) count(it.toLong()) }

        private fun count(n: Long) {
            total += n
            onRead(total)
        }
    }

    private companion object {
        const val CONTENTS = "contents.json"
        const val COMPLETE = ".complete"
        val SAFE_NAME = Regex("^[A-Za-z0-9_][A-Za-z0-9._-]*$")
    }
}
