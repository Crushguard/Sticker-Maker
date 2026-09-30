package com.piptechnologies.stickermaker.core.data.di

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.piptechnologies.stickermaker.BuildConfig
import com.piptechnologies.stickermaker.R
import com.piptechnologies.stickermaker.core.data.catalog.CatalogStore
import com.piptechnologies.stickermaker.core.data.catalog.FirestoreCatalogMetaSource
import com.piptechnologies.stickermaker.core.data.catalog.HttpFetcher
import com.piptechnologies.stickermaker.core.data.catalog.OkHttpFetcher
import com.piptechnologies.stickermaker.core.data.catalog.PackArchive
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient

/** Qualifies the [CoroutineDispatcher] used for disk and network work. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/**
 * Firebase, HTTP and catalog singletons plus shared dispatchers. Data sources and repositories are
 * constructor-injected (@Inject @Singleton) and need no @Provides here.
 */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    /**
     * The app's own named Firestore database (config.xml's config_firestore_database), with
     * on-disk offline persistence so the catalog pointer (catalog/meta) is there offline too.
     */
    @Provides
    @Singleton
    fun provideFirestore(@ApplicationContext context: Context): FirebaseFirestore =
        FirebaseFirestore.getInstance(
            FirebaseApp.getInstance(),
            context.getString(R.string.config_firestore_database)
        ).apply {
            emulatorHost()?.let { useEmulator(it, FIRESTORE_EMULATOR_PORT) }
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
                .build()
        }

    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    fun provideHttpFetcher(client: OkHttpClient): HttpFetcher = OkHttpFetcher(client)

    /** The published catalog, cached in filesDir/catalog so Home opens from disk. */
    @Provides
    @Singleton
    fun provideCatalogStore(
        @ApplicationContext context: Context,
        firestore: FirebaseFirestore,
        fetcher: HttpFetcher,
        @IoDispatcher io: CoroutineDispatcher,
    ): CatalogStore =
        CatalogStore(File(context.filesDir, "catalog"), FirestoreCatalogMetaSource(firestore), fetcher, emulatorHost(), io)

    /** Unpacked pack.zip downloads, in the cache (installed packs are copied to filesDir/packs). */
    @Provides
    @Singleton
    fun providePackArchive(
        @ApplicationContext context: Context,
        fetcher: HttpFetcher,
        @IoDispatcher io: CoroutineDispatcher,
    ): PackArchive = PackArchive(File(context.cacheDir, "packs"), fetcher, io)

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    // Default port of `firebase emulators:start` (see README, "Local Firebase emulators").
    private const val FIRESTORE_EMULATOR_PORT = 8080

    /**
     * Host of the local Firebase emulators, set only for debug builds made with
     * -PfirebaseEmulatorHost=<host> (10.0.2.2 from an Android emulator); null
     * means the real project from google-services.json.
     */
    private fun emulatorHost(): String? = BuildConfig.FIREBASE_EMULATOR_HOST.ifBlank { null }
}
