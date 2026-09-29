package com.piptechnologies.stickermaker.catalog

import androidx.test.core.app.ApplicationProvider
import com.piptechnologies.stickermaker.core.data.catalog.PackArchive
import com.piptechnologies.stickermaker.core.model.StickerPack
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class PackArchiveTest {

    private lateinit var fetcher: FakeFetcher
    private lateinit var archive: PackArchive
    private lateinit var root: File

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, bytes) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private val contents = """
        {"id":"gm-gn","name":"Good Morning, Good Night","version":2,"animated":false,
         "stickers":[{"file":"01.webp","emojis":["☀️","☕"],"text":"good morning"},
                     {"file":"02.webp","emojis":["🌙"],"text":""}]}
    """.trimIndent().toByteArray()

    private fun pack(version: Int, url: String = "https://cdn/gm-gn-v$version.zip") = StickerPack(
        id = "gm-gn", name = "Good Morning, Good Night", publisher = "PIP Technologies", category = "goodnight",
        animated = false, order = 0, downloads = 0, hue = 250, stickerCount = 2, trayUrl = null,
        thumbUrls = emptyList(), stickerUrls = emptyList(), version = version, zipUrl = url, zipBytes = 1000,
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        root = File(context.cacheDir, "test-archives").apply { deleteRecursively() }
        fetcher = FakeFetcher()
        archive = PackArchive(root, fetcher, Dispatchers.Unconfined)
        fetcher["https://cdn/gm-gn-v2.zip"] = zip(
            "contents.json" to contents, "tray.png" to byteArrayOf(1), "01.webp" to byteArrayOf(2), "02.webp" to byteArrayOf(3),
        )
    }

    @Test
    fun unpacksThePackOnceAndReusesIt() = runBlocking {
        val progress = mutableListOf<Float>()
        val dir = archive.ensure(pack(2)) { progress += it }
        assertEquals(setOf("contents.json", "tray.png", "01.webp", "02.webp"), dir.list()!!.filter { !it.startsWith(".") }.toSet())
        assertTrue(progress.isNotEmpty() && progress.last() > 0f)
        assertEquals(dir, archive.ensure(pack(2)))
        assertEquals(1, fetcher.requests.size)
    }

    @Test
    fun readsEachStickersEmojiAndText() = runBlocking {
        val stickers = archive.readContents(archive.ensure(pack(2)))
        assertEquals(listOf("01.webp", "02.webp"), stickers.map { it.fileName })
        assertEquals(listOf("☀️", "☕"), stickers[0].emojis)
        assertEquals(listOf("🌙"), stickers[1].emojis)
    }

    @Test
    fun aBrokenDownloadLeavesNothingBehind() = runBlocking {
        fetcher["https://cdn/gm-gn-v3.zip"] = byteArrayOf(0x50, 0x4b, 9, 9, 9)
        assertTrue(runCatching { archive.ensure(pack(3)) }.isFailure)
        assertFalse(File(root, "gm-gn-3").exists())
        assertTrue(root.listFiles().orEmpty().none { it.name.startsWith("gm-gn-3") })
    }

    @Test
    fun aZipWithoutContentsOrWithPathsOutsideThePackIsRejected() = runBlocking {
        fetcher["https://cdn/gm-gn-v4.zip"] = zip("tray.png" to byteArrayOf(1), "01.webp" to byteArrayOf(2))
        assertTrue(runCatching { archive.ensure(pack(4)) }.isFailure)
        fetcher["https://cdn/gm-gn-v5.zip"] = zip("contents.json" to contents, "../evil.webp" to byteArrayOf(1))
        assertTrue(runCatching { archive.ensure(pack(5)) }.isFailure)
        assertFalse(File(root.parentFile, "evil.webp").exists())
    }

    @Test
    fun aNewVersionReplacesTheOldOne() = runBlocking {
        val old = archive.ensure(pack(2))
        fetcher["https://cdn/gm-gn-v6.zip"] = zip("contents.json" to contents, "tray.png" to byteArrayOf(1), "01.webp" to byteArrayOf(2))
        val new = archive.ensure(pack(6))
        assertTrue(new.exists())
        assertFalse(old.exists())
    }
}
