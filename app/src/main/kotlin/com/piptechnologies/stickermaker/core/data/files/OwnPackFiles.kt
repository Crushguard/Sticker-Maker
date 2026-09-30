package com.piptechnologies.stickermaker.core.data.files

import java.io.File
import java.io.IOException

/**
 * Own packs' files: `filesDir/own/<dirId>/` holds tray.png and the sticker WebPs. Written into a
 * `.tmp` folder and renamed into place, so a failure never leaves a half-written pack.
 */
object OwnPackFiles {

    const val TRAY_FILE = "tray.png"

    /** Writes [trayBytes] and [stickerFiles] (file name to bytes) as pack [dirId], replacing any previous copy. */
    fun write(filesDir: File, dirId: String, trayBytes: ByteArray, stickerFiles: List<Pair<String, ByteArray>>): File {
        val root = File(filesDir, "own").apply { mkdirs() }
        val tmp = File(root, "$dirId.tmp")
        val finalDir = File(root, dirId)
        var completed = false
        try {
            tmp.deleteRecursively()
            if (!tmp.mkdirs()) throw IOException("Could not create ${tmp.absolutePath}")
            File(tmp, TRAY_FILE).writeBytes(trayBytes)
            stickerFiles.forEach { (name, bytes) -> File(tmp, name).writeBytes(bytes) }
            if (finalDir.exists() && !finalDir.deleteRecursively()) {
                throw IOException("Could not replace ${finalDir.absolutePath}")
            }
            if (!tmp.renameTo(finalDir)) {
                throw IOException("Could not move pack $dirId into place")
            }
            completed = true
            return finalDir
        } finally {
            if (!completed) tmp.deleteRecursively()
        }
    }
}
