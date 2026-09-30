package com.piptechnologies.stickermaker.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piptechnologies.stickermaker.core.data.di.IoDispatcher
import com.piptechnologies.stickermaker.core.data.prefs.PrefsRepository
import com.piptechnologies.stickermaker.core.data.repo.CatalogRepository
import com.piptechnologies.stickermaker.core.data.repo.MyPacksRepository
import com.piptechnologies.stickermaker.core.notifications.PushNotifications
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * State for the Settings screen. Labels stay raw here (bytes, language tag);
 * the screen renders them with the design's copy.
 */
data class SettingsUiState(
    /** The Notifications switch as shown: on only when the user wants it and Android allows it. */
    val alertsEnabled: Boolean = true,
    /** Android's notification permission was requested before (see PrefsRepository). */
    val notificationsAsked: Boolean = false,
    val downloadedBytes: Long = 0L,
    val hasDownloads: Boolean = false
)

/** One-shot signals for the screen's toasts. */
sealed interface SettingsEvent {
    /** Every installed pack was removed and the clear was recorded. */
    data object DownloadsCleared : SettingsEvent
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val prefs: PrefsRepository,
    private val myPacks: MyPacksRepository,
    private val catalog: CatalogRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val _events = Channel<SettingsEvent>(Channel.BUFFERED)
    val events: Flow<SettingsEvent> = _events.receiveAsFlow()

    /** Whether Android lets the app post; the user can change it in system settings meanwhile. */
    private val systemAllows = MutableStateFlow(PushNotifications.systemAllows(appContext))

    val uiState: StateFlow<SettingsUiState> =
        combine(
            prefs.alertsEnabled,
            systemAllows,
            prefs.notificationsAsked,
            myPacks.observeInstalled()
        ) { alerts, allowed, asked, installed ->
            val bytes = installed.sumOf { pack -> directorySize(File(pack.dir)) }
            SettingsUiState(
                alertsEnabled = alerts && allowed,
                notificationsAsked = asked,
                downloadedBytes = bytes,
                hasDownloads = installed.isNotEmpty() || bytes > 0L
            )
        }
            // File sizing walks the pack directories; keep it off the main thread.
            .flowOn(ioDispatcher)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                // Until the stored switch loads, show what Android allows rather than a flash of "on".
                initialValue = SettingsUiState(alertsEnabled = systemAllows.value)
            )

    fun setAlertsEnabled(enabled: Boolean) {
        viewModelScope.launch { prefs.setAlertsEnabled(enabled) }
        systemAllows.value = PushNotifications.systemAllows(appContext)
        PushNotifications.setPushEnabled(enabled && systemAllows.value)
    }

    /** The system permission dialog is about to be shown. */
    fun onNotificationsAsked() {
        viewModelScope.launch { prefs.setNotificationsAsked() }
    }

    /**
     * Back on Settings, maybe from Android's notification settings: re-read what Android allows,
     * and register with FCM (or not) to match.
     */
    fun onResume() {
        val allowed = PushNotifications.systemAllows(appContext)
        systemAllows.value = allowed
        viewModelScope.launch { PushNotifications.setPushEnabled(prefs.alertsEnabled.first() && allowed) }
    }

    /**
     * "Clear downloaded packs": removes every installed catalog pack (rows and
     * files) via the repository, records the clear in prefs, then emits
     * [SettingsEvent.DownloadsCleared] for the confirmation toast. A pack that
     * fails to delete does not stop the rest.
     */
    fun clearDownloads() {
        viewModelScope.launch {
            val installed = myPacks.observeInstalled().first()
            installed.forEach { pack ->
                try {
                    catalog.removePack(pack.id)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Keep clearing the remaining packs.
                }
            }
            _events.send(SettingsEvent.DownloadsCleared)
        }
    }

    private fun directorySize(dir: File): Long = try {
        dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    } catch (_: Exception) {
        0L
    }
}
