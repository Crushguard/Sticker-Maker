package com.piptechnologies.stickermaker.catalog

import com.piptechnologies.stickermaker.core.data.catalog.CatalogUrls
import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogUrlsTest {

    private val firebase = "https://firebasestorage.googleapis.com/v0/b/play-console-f33dd-stickermaker/o/{path}?alt=media"

    @Test
    fun firebaseTemplateEncodesTheWholePath() {
        assertEquals(
            "https://firebasestorage.googleapis.com/v0/b/play-console-f33dd-stickermaker/o/" +
                "public%2Fpacks%2Fsorry-wiggle%2Fv3%2Fpack.zip?alt=media",
            CatalogUrls(firebase, emulatorHost = null).url("public/packs/sorry-wiggle/v3/pack.zip"),
        )
    }

    @Test
    fun spacesAndNonAsciiAreEncodedAsUrlPath() {
        assertEquals(
            "https://firebasestorage.googleapis.com/v0/b/play-console-f33dd-stickermaker/o/" +
                "public%2Fa%20b%2F%C3%A9.webp?alt=media",
            CatalogUrls(firebase, emulatorHost = null).url("public/a b/é.webp"),
        )
    }

    @Test
    fun rawPathTemplateKeepsThePathAsIs() {
        assertEquals(
            "https://cdn.example.com/public/packs/x/v1/pack.zip",
            CatalogUrls("https://cdn.example.com/{rawPath}", emulatorHost = null).url("public/packs/x/v1/pack.zip"),
        )
    }

    @Test
    fun emulatorBuildsReachTheHostMachine() {
        val template = "http://127.0.0.1:9199/v0/b/b/o/{path}?alt=media"
        assertEquals("http://10.0.2.2:9199/v0/b/b/o/public%2Fc?alt=media", CatalogUrls(template, "10.0.2.2").url("public/c"))
        assertEquals(
            "http://10.0.2.2:9199/v0/b/b/o/public%2Fc?alt=media",
            CatalogUrls("http://localhost:9199/v0/b/b/o/{path}?alt=media", "10.0.2.2").url("public/c"),
        )
        assertEquals("http://127.0.0.1:9199/v0/b/b/o/public%2Fc?alt=media", CatalogUrls(template, null).url("public/c"))
    }
}
