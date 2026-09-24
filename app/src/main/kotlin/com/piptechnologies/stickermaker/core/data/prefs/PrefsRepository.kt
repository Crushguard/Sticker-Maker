package com.piptechnologies.stickermaker.core.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.loveDataStore: DataStore<Preferences> by preferencesDataStore(name = "love_prefs")

/**
 * Small user preferences in DataStore ("love_prefs"): onboarding flag,
 * selected theme (category) ids, hearts counter, new-pack alerts toggle
 * and clear-downloads bookkeeping. The app language lives with AppCompat's
 * per-app locale instead (see AppLanguages).
 *
 * Read failures fall back to defaults instead of failing screens.
 */
@Singleton
class PrefsRepository @Inject constructor(
    @ApplicationContext context: Context
) {

    private val dataStore = context.loveDataStore

    private val data: Flow<Preferences> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }

    /** Whether the onboarding flow has been completed. */
    val onboarded: Flow<Boolean> =
        data.map { it[KEY_ONBOARDED] ?: false }.distinctUntilChanged()

    /** Category ids picked on the customization screen. */
    val selectedThemes: Flow<Set<String>> =
        data.map { it[KEY_SELECTED_THEMES] ?: emptySet() }.distinctUntilChanged()

    /** Session hearts / favorites counter. */
    val heartsCount: Flow<Int> =
        data.map { it[KEY_HEARTS_COUNT] ?: 0 }.distinctUntilChanged()

    /** Ids of packs the user hearted (the Saved screen and the ♥ Saved chip). */
    val favoritePackIds: Flow<Set<String>> =
        data.map { it[KEY_FAVORITE_PACK_IDS] ?: emptySet() }.distinctUntilChanged()

    /** New-pack alerts toggle from Settings. */
    val alertsEnabled: Flow<Boolean> =
        data.map { it[KEY_ALERTS_ENABLED] ?: true }.distinctUntilChanged()

    /** Epoch millis of the last "clear downloads" in Settings, 0 when never. */
    val downloadsClearedAt: Flow<Long> =
        data.map { it[KEY_DOWNLOADS_CLEARED_AT] ?: 0L }.distinctUntilChanged()

    suspend fun setOnboarded(value: Boolean) {
        dataStore.edit { it[KEY_ONBOARDED] = value }
    }

    suspend fun setSelectedThemes(themeIds: Set<String>) {
        dataStore.edit { it[KEY_SELECTED_THEMES] = themeIds }
    }

    suspend fun setHeartsCount(count: Int) {
        dataStore.edit { it[KEY_HEARTS_COUNT] = count.coerceAtLeast(0) }
    }

    /** Adds [by] hearts (may be negative); the counter never drops below 0. */
    suspend fun incrementHearts(by: Int = 1) {
        dataStore.edit { it[KEY_HEARTS_COUNT] = ((it[KEY_HEARTS_COUNT] ?: 0) + by).coerceAtLeast(0) }
    }

    /** Hearts [packId], or un-hearts it when it is already in the set. */
    suspend fun toggleFavorite(packId: String) {
        dataStore.edit {
            val current = it[KEY_FAVORITE_PACK_IDS] ?: emptySet()
            it[KEY_FAVORITE_PACK_IDS] =
                if (packId in current) current - packId else current + packId
        }
    }

    suspend fun setAlertsEnabled(value: Boolean) {
        dataStore.edit { it[KEY_ALERTS_ENABLED] = value }
    }

    suspend fun markDownloadsCleared(at: Long = System.currentTimeMillis()) {
        dataStore.edit { it[KEY_DOWNLOADS_CLEARED_AT] = at }
    }

    companion object {
        private val KEY_ONBOARDED = booleanPreferencesKey("onboarded")
        private val KEY_SELECTED_THEMES = stringSetPreferencesKey("selected_themes")
        private val KEY_HEARTS_COUNT = intPreferencesKey("hearts_count")
        private val KEY_FAVORITE_PACK_IDS = stringSetPreferencesKey("favorite_pack_ids")
        private val KEY_ALERTS_ENABLED = booleanPreferencesKey("alerts_enabled")
        private val KEY_DOWNLOADS_CLEARED_AT = longPreferencesKey("downloads_cleared_at")
    }
}
