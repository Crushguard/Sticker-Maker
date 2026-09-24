package com.piptechnologies.stickermaker.core.data.di

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.storage.FirebaseStorage
import com.piptechnologies.stickermaker.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** Qualifies the [CoroutineDispatcher] used for disk and network work. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/**
 * Firebase singletons and shared dispatchers. Data sources and repositories
 * are constructor-injected (@Inject @Singleton) and need no @Provides here.
 */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    /** Firestore with on-disk offline persistence so the catalog renders offline. */
    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore =
        FirebaseFirestore.getInstance().apply {
            emulatorHost()?.let { useEmulator(it, FIRESTORE_EMULATOR_PORT) }
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
                .build()
        }

    @Provides
    @Singleton
    fun provideStorage(): FirebaseStorage =
        FirebaseStorage.getInstance().apply {
            emulatorHost()?.let { useEmulator(it, STORAGE_EMULATOR_PORT) }
        }

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    // Default ports of `firebase emulators:start` (see README, "Local Firebase emulators").
    private const val FIRESTORE_EMULATOR_PORT = 8080
    private const val STORAGE_EMULATOR_PORT = 9199

    /**
     * Host of the local Firebase emulators, set only for debug builds made with
     * -PfirebaseEmulatorHost=<host> (10.0.2.2 from an Android emulator); null
     * means the real project from google-services.json.
     */
    private fun emulatorHost(): String? = BuildConfig.FIREBASE_EMULATOR_HOST.ifBlank { null }
}
