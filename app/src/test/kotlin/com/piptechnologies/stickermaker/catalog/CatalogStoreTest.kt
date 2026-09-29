package com.piptechnologies.stickermaker.catalog

import androidx.test.core.app.ApplicationProvider
import com.piptechnologies.stickermaker.core.data.catalog.Catalog
import com.piptechnologies.stickermaker.core.data.catalog.CatalogMeta
import com.piptechnologies.stickermaker.core.data.catalog.CatalogStore
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class CatalogStoreTest {

    private lateinit var dir: File
    private val template = "https://cdn.example.com/{rawPath}"

    private fun gz(text: String): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(text.toByteArray()) }
        return out.toByteArray()
    }

    private fun catalogJson(version: Int, packId: String) = """
        {"schema":1,"version":$version,"categories":[{"id":"sorry","order":1,"names":{"en":"Sorry"},"packs":1}],
         "packs":[{"id":"$packId","name":"$packId","category":"sorry","count":3,"version":1,
                   "zip":{"path":"public/packs/$packId/v1/pack.zip","bytes":10}}]}
    """.trimIndent()

    private fun meta(version: Int) = CatalogMeta(version, "public/catalog/v$version.json.gz", template)

    private fun store(metas: MutableStateFlow<CatalogMeta?>, fetcher: FakeFetcher) =
        CatalogStore(dir, { metas }, fetcher, emulatorHost = null, io = Dispatchers.Unconfined)

    private suspend fun CatalogStore.firstCatalog(): Catalog? = withTimeout(5_000) { catalog.first() }

    @Before
    fun setUp() {
        dir = File(ApplicationProvider.getApplicationContext<android.content.Context>().filesDir, "test-catalog")
        dir.deleteRecursively()
    }

    @Test
    fun downloadsTheCatalogThePointerNamesAndCachesIt() = runBlocking {
        val fetcher = FakeFetcher().apply { this["https://cdn.example.com/public/catalog/v1.json.gz"] = gz(catalogJson(1, "a")) }
        val catalog = store(MutableStateFlow(meta(1)), fetcher).firstCatalog()!!
        assertEquals(listOf("a"), catalog.packs.map { it.id })
        assertEquals("https://cdn.example.com/public/packs/a/v1/pack.zip", catalog.packs[0].zipUrl)

        // Offline next launch: the cached catalog comes straight from disk.
        val offline = store(MutableStateFlow(null), FakeFetcher()).firstCatalog()!!
        assertEquals(listOf("a"), offline.packs.map { it.id })
    }

    @Test
    fun anUnchangedVersionIsNotDownloadedAgain() = runBlocking {
        val fetcher = FakeFetcher().apply { this["https://cdn.example.com/public/catalog/v1.json.gz"] = gz(catalogJson(1, "a")) }
        store(MutableStateFlow(meta(1)), fetcher).firstCatalog()
        val again = FakeFetcher()
        val catalog = withTimeout(5_000) { store(MutableStateFlow(meta(1)), again).catalog.take(1).toList() }
        assertEquals(listOf("a"), catalog.single()!!.packs.map { it.id })
        assertEquals(emptyList<String>(), again.requests)
    }

    @Test
    fun aFailedDownloadKeepsThePreviousCatalog() = runBlocking {
        val fetcher = FakeFetcher().apply { this["https://cdn.example.com/public/catalog/v1.json.gz"] = gz(catalogJson(1, "a")) }
        store(MutableStateFlow(meta(1)), fetcher).firstCatalog()
        val metas = MutableStateFlow<CatalogMeta?>(meta(2))
        val catalog = store(metas, FakeFetcher()).firstCatalog()!!
        assertEquals(listOf("a"), catalog.packs.map { it.id })
    }

    @Test
    fun aNewVersionReplacesTheCachedCatalog() = runBlocking {
        val fetcher = FakeFetcher().apply {
            this["https://cdn.example.com/public/catalog/v1.json.gz"] = gz(catalogJson(1, "a"))
            this["https://cdn.example.com/public/catalog/v2.json.gz"] = gz(catalogJson(2, "b"))
        }
        store(MutableStateFlow(meta(1)), fetcher).firstCatalog()
        val emissions = withTimeout(5_000) { store(MutableStateFlow(meta(2)), fetcher).catalog.take(2).toList() }
        assertEquals(listOf(listOf("a"), listOf("b")), emissions.map { c -> c!!.packs.map { it.id } })
    }

    @Test
    fun nothingCachedAndNoPointerMeansNoCatalog() = runBlocking {
        assertNull(store(MutableStateFlow(null), FakeFetcher()).firstCatalog())
    }

    @Test
    fun retryDownloadsAPointerWhoseDownloadFailed() = runBlocking {
        val fetcher = FakeFetcher()
        val store = store(MutableStateFlow(meta(1)), fetcher)
        val emissions = mutableListOf<Catalog?>()
        withTimeout(5_000) {
            val job = launch(Dispatchers.Unconfined) { store.catalog.collect { emissions += it } }
            while (emissions.isEmpty()) yield()
            assertNull("the download failed and nothing is cached", emissions.single())

            fetcher["https://cdn.example.com/public/catalog/v1.json.gz"] = gz(catalogJson(1, "a"))
            store.retry()
            while (emissions.last() == null) yield()
            job.cancel()
        }
        assertEquals(listOf("a"), emissions.last()!!.packs.map { it.id })
    }

    @Test
    fun retryWithNothingToLoadSaysSoAgain() = runBlocking {
        val store = store(MutableStateFlow(null), FakeFetcher())
        val emissions = mutableListOf<Catalog?>()
        withTimeout(5_000) {
            val job = launch(Dispatchers.Unconfined) { store.catalog.collect { emissions += it } }
            while (emissions.isEmpty()) yield()
            store.retry()
            while (emissions.size < 2) yield()
            job.cancel()
        }
        assertEquals(listOf<Catalog?>(null, null), emissions)
    }

    @Test
    fun retryWithTheCatalogCurrentDownloadsNothing() = runBlocking {
        val fetcher = FakeFetcher().apply { this["https://cdn.example.com/public/catalog/v1.json.gz"] = gz(catalogJson(1, "a")) }
        val store = store(MutableStateFlow(meta(1)), fetcher)
        val emissions = mutableListOf<Catalog?>()
        withTimeout(5_000) {
            val job = launch(Dispatchers.Unconfined) { store.catalog.collect { emissions += it } }
            while (emissions.isEmpty()) yield()
            store.retry()
            yield()
            job.cancel()
        }
        assertEquals(1, fetcher.requests.size)
        assertEquals(1, emissions.size)
    }
}
