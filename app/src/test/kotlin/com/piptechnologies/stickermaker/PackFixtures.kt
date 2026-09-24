package com.piptechnologies.stickermaker

import java.io.File

/** Locates the repository `packs/` fixtures from wherever the unit tests are run. */
internal object PackFixtures {

    private const val SENTINEL = "packs/gm-gn/pack.json"

    val packsDir: File by lazy {
        locatePacksDir()
            ?: error(
                "Could not locate the repository packs/ directory walking up from " +
                    File(System.getProperty("user.dir") ?: ".").absolutePath
            )
    }

    /** Every pack fixture directory (a directory under packs/ holding a pack.json). */
    val packDirs: List<File> by lazy {
        packsDir.listFiles { file: File -> file.isDirectory && File(file, "pack.json").isFile }
            ?.sortedBy { it.name }
            .orEmpty()
    }

    private fun locatePacksDir(): File? {
        // Gradle runs app unit tests with the module directory (app/) as the working dir.
        val fromModuleDir = File("../packs")
        if (File(fromModuleDir, "gm-gn/pack.json").isFile) {
            return fromModuleDir.canonicalFile
        }
        // Otherwise walk up from the working directory until the repo root is found.
        var dir: File? = File(System.getProperty("user.dir") ?: ".").canonicalFile
        while (dir != null) {
            if (File(dir, SENTINEL).isFile) {
                return File(dir, "packs")
            }
            dir = dir.parentFile
        }
        return null
    }
}
