package com.piptechnologies.stickermaker.core.data.catalog

import java.io.File
import java.io.InputStream
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/**
 * The catalog, cache first: the last catalog file saved in [dir] is emitted at once (Home opens from disk,
 * offline too), then a new one whenever catalog/meta names a version not cached yet. A failed download keeps
 * the previous catalog; null is emitted only when nothing is cached and nothing could be loaded.
 *
 * The search words file a catalog names (CatalogFile.wordsPath) is downloaded only when its name changes: a new
 * catalog usually keeps the words of the last one.
 */
class CatalogStore(
    private val dir: File,
    private val metaSource: CatalogMetaSource,
    private val fetcher: HttpFetcher,
    private val emulatorHost: String?,
    private val io: CoroutineDispatcher,
) {

    private data class Cached(val version: Int, val template: String, val catalog: Catalog)

    private val retries = MutableStateFlow(0)

    /**
     * Tries the latest catalog/meta pointer again (Home's offline Retry): downloads it if it isn't cached, and
     * emits null again when there is still nothing to show.
     */
    fun retry() = retries.update { it + 1 }

    val catalog: Flow<Catalog?> = channelFlow {
        var current: Cached? = withContext(io) { readCache() }
        current?.let { send(it.catalog) }
        combine(metaSource.observe(), retries) { meta, _ -> meta }.collect { meta ->
            val cached = current
            if (meta == null) {
                if (cached == null) send(null)
                return@collect
            }
            if (cached != null && cached.version == meta.version && cached.template == meta.urlTemplate) return@collect
            val loaded = try {
                withContext(io) { download(meta) }
            } catch (ce: CancellationException) {
                throw ce
            } catch (failed: Exception) {
                null
            }
            if (loaded != null) {
                current = loaded
                send(loaded.catalog)
            } else if (cached == null) {
                send(null)
            }
        }
    }

    private suspend fun download(meta: CatalogMeta): Cached {
        val urls = CatalogUrls(meta.urlTemplate, emulatorHost)
        val json = fetcher.open(urls.url(meta.path), ::gunzipText)
        val wordsPath = CatalogFile.wordsPath(json)
        val words = wordsPath?.let { path -> cachedWords(path) ?: fetcher.open(urls.url(path), ::gunzipText) }
        val catalog = CatalogFile.parse(json, urls, words)
        writeCache(meta, json, wordsPath, words)
        return Cached(meta.version, meta.urlTemplate, catalog)
    }

    private fun gunzipText(input: InputStream): String = GZIPInputStream(input).readBytes().toString(Charsets.UTF_8)

    /** The cached words file, when it is the one [path] names. */
    private fun cachedWords(path: String): String? =
        if (File(dir, WORDS_PATH_FILE).takeIf { it.isFile }?.readText() == path) File(dir, WORDS_FILE).takeIf { it.isFile }?.readText() else null

    private fun readCache(): Cached? = try {
        val metaLines = File(dir, META_FILE).takeIf { it.isFile }?.readLines().orEmpty()
        val json = File(dir, CATALOG_FILE).takeIf { it.isFile }?.readText()
        if (metaLines.size < 2 || json == null) {
            null
        } else {
            val template = metaLines[1]
            val words = CatalogFile.wordsPath(json)?.let(::cachedWords)
            Cached(metaLines[0].toInt(), template, CatalogFile.parse(json, CatalogUrls(template, emulatorHost), words))
        }
    } catch (unreadable: Exception) {
        null
    }

    /** Words, then catalog, pointer last: a half-written cache is never read as current. */
    private fun writeCache(meta: CatalogMeta, json: String, wordsPath: String?, words: String?) {
        dir.mkdirs()
        if (wordsPath != null && words != null && cachedWords(wordsPath) == null) {
            replace(File(dir, WORDS_FILE), words)
            replace(File(dir, WORDS_PATH_FILE), wordsPath)
        }
        replace(File(dir, CATALOG_FILE), json)
        replace(File(dir, META_FILE), "${meta.version}\n${meta.urlTemplate}\n")
    }

    private fun replace(target: File, text: String) {
        val tmp = File(target.parentFile, "${target.name}.tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        }
    }

    private companion object {
        const val CATALOG_FILE = "catalog.json"
        const val META_FILE = "catalog.meta"
        const val WORDS_FILE = "words.json"
        const val WORDS_PATH_FILE = "words.path"
    }
}
