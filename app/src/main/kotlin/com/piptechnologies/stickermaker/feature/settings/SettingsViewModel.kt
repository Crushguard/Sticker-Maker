package com.piptechnologies.stickermaker.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piptechnologies.stickermaker.core.data.di.IoDispatcher
import com.piptechnologies.stickermaker.core.data.prefs.PrefsRepository
import com.piptechnologies.stickermaker.core.data.repo.CatalogRepository
import com.piptechnologies.stickermaker.core.data.repo.MyPacksRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * State for the Settings screen. Labels stay raw here (bytes, language tag,
 * theme count); the screen renders them with the design's copy.
 */
data class SettingsUiState(
    val alertsEnabled: Boolean = true,
    val themesCount: Int = 0,
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
    private val prefs: PrefsRepository,
    private val myPacks: MyPacksRepository,
    private val catalog: CatalogRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val _events = Channel<SettingsEvent>(Channel.BUFFERED)
    val events: Flow<SettingsEvent> = _events.receiveAsFlow()

    val uiState: StateFlow<SettingsUiState> =
        combine(
            prefs.alertsEnabled,
            prefs.selectedThemes,
            myPacks.observeInstalled()
        ) { alerts, themes, installed ->
            val bytes = installed.sumOf { pack -> directorySize(File(pack.dir)) }
            SettingsUiState(
                alertsEnabled = alerts,
                themesCount = themes.size,
                downloadedBytes = bytes,
                hasDownloads = installed.isNotEmpty() || bytes > 0L
            )
        }
            // File sizing walks the pack directories; keep it off the main thread.
            .flowOn(ioDispatcher)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = SettingsUiState()
            )

    fun setAlertsEnabled(enabled: Boolean) {
        viewModelScope.launch { prefs.setAlertsEnabled(enabled) }
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
