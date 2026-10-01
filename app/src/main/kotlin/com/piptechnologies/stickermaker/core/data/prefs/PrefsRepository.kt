package com.piptechnologies.stickermaker.core.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
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
 * Small user preferences in DataStore ("love_prefs"): the onboarding flag,
 * hearted pack ids, the notifications switch, whether Android's
 * notification permission was asked and the Create editor's recent emoji.
 * The app language lives with AppCompat's per-app locale instead (see
 * AppLanguages).
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

    /** Ids of packs the user hearted (the Saved screen and the ♥ Saved chip). */
    val favoritePackIds: Flow<Set<String>> =
        data.map { it[KEY_FAVORITE_PACK_IDS] ?: emptySet() }.distinctUntilChanged()

    /** Settings' Notifications switch (alerts and updates). */
    val alertsEnabled: Flow<Boolean> =
        data.map { it[KEY_ALERTS_ENABLED] ?: true }.distinctUntilChanged()

    /**
     * Whether Android's notification permission was ever requested. Once it was and Android stops
     * showing the question, the switch has to send the user to system settings instead.
     */
    val notificationsAsked: Flow<Boolean> =
        data.map { it[KEY_NOTIFICATIONS_ASKED] ?: false }.distinctUntilChanged()

    /** Emoji files picked in Create › Add › Emoji, most recent first, at most [MAX_EMOJI_RECENTS]. */
    val emojiRecents: Flow<List<String>> =
        data.map { recentsOf(it[KEY_EMOJI_RECENTS]) }.distinctUntilChanged()

    suspend fun setOnboarded(value: Boolean) {
        dataStore.edit { it[KEY_ONBOARDED] = value }
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

    suspend fun setNotificationsAsked() {
        dataStore.edit { it[KEY_NOTIFICATIONS_ASKED] = true }
    }

    /** Puts the emoji [file] first in [emojiRecents] (moved up when it is already there). */
    suspend fun pushEmojiRecent(file: String) {
        if (file.isEmpty() || '\n' in file) return
        dataStore.edit { it[KEY_EMOJI_RECENTS] = withRecent(it[KEY_EMOJI_RECENTS], file) }
    }

    companion object {
        private val KEY_ONBOARDED = booleanPreferencesKey("onboarded")
        private val KEY_FAVORITE_PACK_IDS = stringSetPreferencesKey("favorite_pack_ids")
        private val KEY_ALERTS_ENABLED = booleanPreferencesKey("alerts_enabled")
        private val KEY_NOTIFICATIONS_ASKED = booleanPreferencesKey("notifications_asked")
        private val KEY_EMOJI_RECENTS = stringPreferencesKey("emoji_recents")

        /** How many recent emoji Create keeps (spec §13.10). */
        const val MAX_EMOJI_RECENTS = 18

        /** The stored recents (`\n`-joined), most recent first. */
        internal fun recentsOf(stored: String?): List<String> =
            stored?.split('\n')?.filter { it.isNotEmpty() }.orEmpty()

        /** [stored] with [file] first and no second copy of it, cut to [MAX_EMOJI_RECENTS], `\n`-joined. */
        internal fun withRecent(stored: String?, file: String): String =
            (listOf(file) + recentsOf(stored).filterNot { it == file }).take(MAX_EMOJI_RECENTS).joinToString("\n")
    }
}
