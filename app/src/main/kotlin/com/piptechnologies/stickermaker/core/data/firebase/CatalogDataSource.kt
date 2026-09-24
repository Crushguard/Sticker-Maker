package com.piptechnologies.stickermaker.core.data.firebase

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.piptechnologies.stickermaker.core.data.di.IoDispatcher
import com.piptechnologies.stickermaker.core.model.Category
import com.piptechnologies.stickermaker.core.model.StickerPack
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

private const val COLLECTION_CATEGORIES = "categories"
private const val COLLECTION_PACKS = "packs"
private const val DEFAULT_PUBLISHER = "PIP Technologies"
private const val DEFAULT_HUE = 340

/**
 * Read-only access to the remote catalog: Firestore `categories` and `packs`
 * (offline persistence is enabled on the injected [FirebaseFirestore]) plus
 * lazy Firebase Storage path -> download-URL resolution cached in memory.
 *
 * Documents are mapped defensively: missing fields fall back to sensible
 * defaults and malformed documents (no name) are skipped. Listener errors
 * surface as empty lists so screens can show their offline empty states.
 */
@Singleton
class CatalogDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    /** Storage object path -> resolved https download URL. */
    private val urlCache = ConcurrentHashMap<String, String>()

    /** Streams the `categories` collection, sorted by their `order` field. */
    fun observeCategories(): Flow<List<Category>> = callbackFlow<List<Category>> {
        val registration = firestore.collection(COLLECTION_CATEGORIES)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val categories = snapshot?.documents.orEmpty()
                    .mapNotNull(::toCategory)
                    .sortedBy { it.order }
                trySend(categories)
            }
        awaitClose { registration.remove() }
    }.conflate().flowOn(ioDispatcher)

    /**
     * Streams the `packs` collection, sorted by `order`. Tray and thumb URLs
     * are resolved (cache-first, concurrently); full-size [StickerPack.stickerUrls]
     * stay empty here and are resolved by [getPack] when a pack is opened.
     */
    fun observePacks(): Flow<List<StickerPack>> = callbackFlow<List<DocumentSnapshot>> {
        val registration = firestore.collection(COLLECTION_PACKS)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents.orEmpty())
            }
        awaitClose { registration.remove() }
    }
        .conflate()
        .map { documents ->
            coroutineScope {
                documents
                    .map { doc -> async { toPack(doc, resolveStickerUrls = false) } }
                    .awaitAll()
                    .filterNotNull()
                    .sortedBy { it.order }
            }
        }
        .flowOn(ioDispatcher)

    /**
     * One pack by id with every URL resolved (tray, thumbs and full-size
     * stickers), or null when the document is missing, malformed or the
     * fetch failed and nothing is cached.
     */
    suspend fun getPack(id: String): StickerPack? = withContext(ioDispatcher) {
        val document = try {
            firestore.collection(COLLECTION_PACKS).document(id).get().await()
        } catch (ce: CancellationException) {
            throw ce
        } catch (ignored: Exception) {
            null
        }
        document?.takeIf { it.exists() }?.let { toPack(it, resolveStickerUrls = true) }
    }

    // ------------------------------------------------------------------ //

    private fun toCategory(doc: DocumentSnapshot): Category? {
        val name = doc.getString("name") ?: return null
        return Category(
            id = doc.id,
            name = name,
            icon = doc.getString("icon") ?: "heart",
            hue = (doc.getLong("hue") ?: DEFAULT_HUE.toLong()).toInt(),
            order = (doc.getLong("order") ?: Int.MAX_VALUE.toLong()).toInt()
        )
    }

    private suspend fun toPack(doc: DocumentSnapshot, resolveStickerUrls: Boolean): StickerPack? {
        val id = doc.id
        val name = doc.getString("name") ?: return null

        val declaredStickerPaths = doc.stringList("stickerPaths")
        val stickerCount = (doc.getLong("stickerCount") ?: declaredStickerPaths.size.toLong()).toInt()
        val stickerPaths = declaredStickerPaths.ifEmpty { defaultStickerPaths(id, stickerCount) }
        val thumbPaths = doc.stringList("thumbPaths").ifEmpty {
            stickerPaths.map { "packs/$id/thumbs/${it.substringAfterLast('/')}" }
        }
        val trayPath = doc.getString("trayPath") ?: "packs/$id/tray.png"

        val (trayUrl, thumbUrls, stickerUrls) = coroutineScope {
            val tray = async { resolveUrl(trayPath) }
            val thumbs = thumbPaths.map { path -> async { resolveUrl(path) } }
            val stickers: List<Deferred<String?>> =
                if (resolveStickerUrls) stickerPaths.map { path -> async { resolveUrl(path) } }
                else emptyList()
            Triple(
                tray.await(),
                thumbs.awaitAll().filterNotNull(),
                stickers.awaitAll().filterNotNull()
            )
        }

        return StickerPack(
            id = id,
            name = name,
            publisher = doc.getString("publisher") ?: DEFAULT_PUBLISHER,
            category = doc.getString("category") ?: "",
            animated = doc.getBoolean("animated") ?: false,
            order = (doc.getLong("order") ?: Int.MAX_VALUE.toLong()).toInt(),
            downloads = doc.getLong("downloads") ?: 0L,
            hue = (doc.getLong("hue") ?: DEFAULT_HUE.toLong()).toInt(),
            stickerCount = stickerCount,
            trayUrl = trayUrl,
            thumbUrls = thumbUrls,
            stickerUrls = stickerUrls,
            trayPath = trayPath,
            stickerPaths = stickerPaths,
            stickerEmojis = emojiMap(doc)
        )
    }

    /** Optional doc field `emojis`: map of sticker file name -> emoji list. */
    private fun emojiMap(doc: DocumentSnapshot): Map<String, List<String>> {
        val raw = doc.get("emojis") as? Map<*, *> ?: return emptyMap()
        return buildMap {
            for ((key, value) in raw) {
                val fileName = key as? String ?: continue
                val emojis = (value as? List<*>)?.filterIsInstance<String>().orEmpty()
                if (emojis.isNotEmpty()) put(fileName, emojis)
            }
        }
    }

    /**
     * Resolves a Storage object path to its download URL, cache-first.
     * Returns null (and stays uncached) when resolution fails, e.g. offline.
     */
    private suspend fun resolveUrl(path: String): String? {
        if (path.isBlank()) return null
        urlCache[path]?.let { return it }
        return try {
            storage.getReference(path).downloadUrl.await().toString()
                .also { urlCache[path] = it }
        } catch (ce: CancellationException) {
            throw ce
        } catch (ignored: Exception) {
            null
        }
    }

    private fun DocumentSnapshot.stringList(field: String): List<String> =
        (get(field) as? List<*>)?.filterIsInstance<String>().orEmpty()

    /** Locked Storage layout: packs/{id}/01.webp ... NN.webp. */
    private fun defaultStickerPaths(id: String, count: Int): List<String> =
        (1..count).map { index -> "packs/$id/" + "%02d.webp".format(Locale.ROOT, index) }
}
