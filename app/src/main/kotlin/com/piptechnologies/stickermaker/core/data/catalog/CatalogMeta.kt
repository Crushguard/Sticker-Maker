package com.piptechnologies.stickermaker.core.data.catalog

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Firestore catalog/meta: which catalog file is live and how to build its URLs. */
data class CatalogMeta(
    val version: Int,
    val path: String,
    val urlTemplate: String,
)

/** Where catalog/meta updates come from; null means it is not available (offline, not published yet). */
fun interface CatalogMetaSource {
    fun observe(): Flow<CatalogMeta?>
}

/** The one Firestore document the app reads (database "stickermaker"). */
class FirestoreCatalogMetaSource(private val firestore: FirebaseFirestore) : CatalogMetaSource {

    override fun observe(): Flow<CatalogMeta?> = callbackFlow {
        val registration = firestore.document(META_DOCUMENT).addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null || !snapshot.exists()) {
                trySend(null)
                return@addSnapshotListener
            }
            val meta = CatalogMeta(
                version = (snapshot.getLong("version") ?: 0L).toInt(),
                path = snapshot.getString("path").orEmpty(),
                urlTemplate = snapshot.getString("urlTemplate").orEmpty(),
            )
            trySend(meta.takeIf { it.path.isNotBlank() && it.urlTemplate.isNotBlank() })
        }
        awaitClose { registration.remove() }
    }

    private companion object {
        const val META_DOCUMENT = "catalog/meta"
    }
}
