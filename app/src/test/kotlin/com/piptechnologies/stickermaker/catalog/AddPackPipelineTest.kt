package com.piptechnologies.stickermaker.catalog

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.piptechnologies.stickermaker.core.data.catalog.CatalogMeta
import com.piptechnologies.stickermaker.core.data.catalog.CatalogStore
import com.piptechnologies.stickermaker.core.data.catalog.PackArchive
import com.piptechnologies.stickermaker.core.data.db.LoveDb
import com.piptechnologies.stickermaker.core.data.download.PackDownloader
import com.piptechnologies.stickermaker.core.data.firebase.CatalogDataSource
import com.piptechnologies.stickermaker.core.data.repo.CatalogRepository
import com.piptechnologies.stickermaker.core.model.AddState
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Catalog file → pack.zip → installed files → Room row, with only the network faked. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class AddPackPipelineTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: LoveDb
    private lateinit var repository: CatalogRepository
    private lateinit var fetcher: FakeFetcher
    private val metas = MutableStateFlow(CatalogMeta(1, "public/catalog/v1.json.gz", "https://cdn/{rawPath}"))

    private fun gz(text: String): ByteArray =
        ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(text.toByteArray()) } }.toByteArray()

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray =
        ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip ->
                for ((name, bytes) in entries) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
        }.toByteArray()

    @Before
    fun setUp() {
        File(context.filesDir, "packs").deleteRecursively()
        fetcher = FakeFetcher()
        fetcher["https://cdn/public/catalog/v1.json.gz"] = gz(
            """{"schema":1,"version":1,"categories":[],"packs":[{"id":"gm-gn","name":"Good Morning, Good Night",
               "category":"goodnight","count":2,"version":3,"zip":{"path":"public/packs/gm-gn/v3/pack.zip","bytes":400}}]}"""
        )
        fetcher["https://cdn/public/packs/gm-gn/v3/pack.zip"] = zip(
            "contents.json" to """{"id":"gm-gn","version":3,"stickers":[
                {"file":"01.webp","emojis":["☀️","☕"],"text":"good morning"},
                {"file":"02.webp","emojis":["🌙"],"text":""}]}""".toByteArray(),
            "tray.png" to byteArrayOf(1),
            "01.webp" to byteArrayOf(2),
            "02.webp" to byteArrayOf(3),
        )
        val io = Dispatchers.Unconfined
        val store = CatalogStore(
            File(context.filesDir, "test-pipeline-catalog").apply { deleteRecursively() },
            { metas },
            fetcher,
            emulatorHost = null,
            io = io,
        )
        val archive = PackArchive(File(context.cacheDir, "test-pipeline-archive").apply { deleteRecursively() }, fetcher, io)
        db = Room.inMemoryDatabaseBuilder(context, LoveDb::class.java).allowMainThreadQueries().build()
        val source = CatalogDataSource(store, archive, io)
        repository = CatalogRepository(source, db.installedPackDao(), PackDownloader(context, archive, io), io)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun addingAPackInstallsItsFilesAndRecordsVersionEmojiAndText() = runBlocking {
        val pack = withTimeout(5_000) { repository.observePacks().first { it.isNotEmpty() } }.single()
        val states = withTimeout(5_000) { repository.addPack(pack).toList() }
        assertEquals(AddState.Sent, states.last())

        val installed = db.installedPackDao().getBlocking("gm-gn")!!
        assertEquals(3, installed.imageDataVersion)
        assertEquals(setOf("tray.png", "01.webp", "02.webp"), File(installed.dirPath).list()!!.toSet())
        val stickers = db.installedPackDao().stickersBlocking("gm-gn")
        assertEquals(listOf("01.webp", "02.webp"), stickers.map { it.fileName })
        assertEquals("☀️,☕", stickers[0].emojis)
        assertEquals("good morning", stickers[0].accessibilityText)
        assertNull(stickers[1].accessibilityText)
        assertEquals(1, fetcher.requests.count { it.endsWith("pack.zip") })
    }

    /** Publishes catalog v2, where gm-gn is version 4 with three new stickers. */
    private fun publishVersion4() {
        fetcher["https://cdn/public/catalog/v2.json.gz"] = gz(
            """{"schema":1,"version":2,"categories":[],"packs":[{"id":"gm-gn","name":"Good Morning, Good Night",
               "category":"goodnight","count":3,"version":4,"zip":{"path":"public/packs/gm-gn/v4-0a1b2c3d/pack.zip","bytes":500}}]}"""
        )
        fetcher["https://cdn/public/packs/gm-gn/v4-0a1b2c3d/pack.zip"] = zip(
            "contents.json" to """{"id":"gm-gn","version":4,"stickers":[
                {"file":"01.webp","emojis":["☀️"],"text":"morning"},
                {"file":"02.webp","emojis":["🌙"],"text":""},
                {"file":"03.webp","emojis":["😴"],"text":"sleep"}]}""".toByteArray(),
            "tray.png" to byteArrayOf(7),
            "01.webp" to byteArrayOf(8),
            "02.webp" to byteArrayOf(9),
            "03.webp" to byteArrayOf(10),
        )
        metas.value = CatalogMeta(2, "public/catalog/v2.json.gz", "https://cdn/{rawPath}")
    }

    @Test
    fun openingAnInstalledPackThatHasANewVersionInstallsIt() = runBlocking {
        val v3 = withTimeout(5_000) { repository.observePacks().first { it.isNotEmpty() } }.single()
        withTimeout(5_000) { repository.addPack(v3).toList() }
        db.installedPackDao().setWhitelisted("gm-gn", true)
        val addedAt = db.installedPackDao().getBlocking("gm-gn")!!.addedAt

        publishVersion4()
        withTimeout(5_000) { repository.observePacks().first { it.single().version == 4 } }
        val opened = withTimeout(5_000) { repository.getPack("gm-gn") }!!
        assertTrue(withTimeout(5_000) { repository.refreshInstalled(opened) })

        val installed = db.installedPackDao().getBlocking("gm-gn")!!
        assertEquals(4, installed.imageDataVersion)
        assertTrue("WhatsApp still has the pack", installed.whitelisted)
        assertEquals(addedAt, installed.addedAt)
        assertArrayEquals(byteArrayOf(8), File(installed.dirPath, "01.webp").readBytes())
        val stickers = db.installedPackDao().stickersBlocking("gm-gn")
        assertEquals(listOf("01.webp", "02.webp", "03.webp"), stickers.map { it.fileName })
        assertEquals("sleep", stickers[2].accessibilityText)
        assertEquals("the page's download is reused", 1, fetcher.requests.count { it.endsWith("v4-0a1b2c3d/pack.zip") })

        assertFalse("already current", withTimeout(5_000) { repository.refreshInstalled(opened) })
    }

    @Test
    fun aPackThatIsNotInstalledIsNotRefreshed() = runBlocking {
        val pack = withTimeout(5_000) { repository.getPack("gm-gn") }!!
        assertFalse(withTimeout(5_000) { repository.refreshInstalled(pack) })
        assertNull(db.installedPackDao().getBlocking("gm-gn"))
    }

    @Test
    fun thePackPagePreviewAndAddShareOneDownload() = runBlocking {
        val pack = withTimeout(5_000) { repository.getPack("gm-gn") }!!
        assertEquals(2, pack.stickerUrls.size)
        assertTrue(pack.stickerUrls.all { it.startsWith("file:") })
        withTimeout(5_000) { repository.addPack(pack).toList() }
        assertEquals(1, fetcher.requests.count { it.endsWith("pack.zip") })
    }
}
