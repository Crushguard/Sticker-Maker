package com.piptechnologies.stickermaker.feature.saved

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piptechnologies.stickermaker.core.data.di.IoDispatcher
import com.piptechnologies.stickermaker.core.data.prefs.PrefsRepository
import com.piptechnologies.stickermaker.core.data.repo.CatalogRepository
import com.piptechnologies.stickermaker.core.data.repo.MyPacksRepository
import com.piptechnologies.stickermaker.core.model.AddState
import com.piptechnologies.stickermaker.core.model.StickerPack
import com.piptechnologies.stickermaker.whatsapp.AddStickerPackFlow
import com.piptechnologies.stickermaker.whatsapp.WhitelistCheck
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---- Copy: exact strings from design/Prototype.dc.html ---------------------

internal const val TITLE_SAVED = "Saved"
internal const val META_YOURS = "yours"

internal const val EMPTY_TITLE = "No saved packs yet"
internal const val EMPTY_BODY = "Tap the heart on any pack to keep it here for later."
internal const val EMPTY_PRIMARY = "Browse packs"

internal const val TOAST_ADDED = "Added to WhatsApp"
internal const val TOAST_ALREADY = "Already in WhatsApp"

internal const val NO_WHATSAPP_TITLE = "WhatsApp isn't installed"
internal const val NO_WHATSAPP_BODY =
    "Stickers are added inside WhatsApp. Install it, then come back to add this pack."
internal const val NO_WHATSAPP_CONFIRM = "Get WhatsApp"
internal const val NO_WHATSAPP_CANCEL = "Not now"

/** "96.4K adds"-style meta segment: the prototype's fmt() ported one to one. */
private fun formatAdds(count: Long): String =
    if (count >= 1000) {
        String.format(Locale.ROOT, "%.1f", count / 1000.0).removeSuffix(".0") + "K"
    } else {
        count.toString()
    }

// ---- UI state --------------------------------------------------------------

/** One hearted pack, rendered as the same browse card Home uses. */
data class SavedRow(
    val id: String,
    val name: String,
    val animated: Boolean,
    val stickerCount: Int,
    /** "96.4K adds" for catalog packs, "yours" for own packs. */
    val metaLabel: String,
    val own: Boolean,
    val addState: AddState,
    /** Coil models: thumb URLs for catalog packs, [File]s for own packs. */
    val thumbModels: List<Any>,
    /** The full catalog pack, for the download pipeline (null for own packs). */
    val remote: StickerPack? = null
)

data class SavedUiState(
    val loading: Boolean = true,
    val rows: List<SavedRow> = emptyList(),
    val showNoWhatsApp: Boolean = false
) {
    val empty: Boolean get() = !loading && rows.isEmpty()
}

sealed interface SavedEvent {
    data class LaunchAddIntent(val intent: Intent) : SavedEvent
    data class Toast(val message: String, val withCheck: Boolean) : SavedEvent
}

/**
 * Saved (My Packs › Saved): every hearted pack, newest catalog order first,
 * then hearted own packs — the same browse cards as Home, with the full add
 * state machine on each card's pill. Un-hearting removes the card in place.
 */
@HiltViewModel
class SavedViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val catalogRepository: CatalogRepository,
    private val myPacksRepository: MyPacksRepository,
    private val prefsRepository: PrefsRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    /** Per-pack add sessions layered over the persisted whitelist state. */
    private val sessions = MutableStateFlow<Map<String, AddState>>(emptyMap())
    private val showNoWhatsApp = MutableStateFlow(false)

    private val _events = Channel<SavedEvent>(Channel.BUFFERED)
    val events: Flow<SavedEvent> = _events.receiveAsFlow()

    private val addJobs = mutableMapOf<String, Job>()

    /** Ids this screen downloaded in the running session (cleanup on failure). */
    private val downloadedIds = mutableSetOf<String>()

    /** The pack whose ENABLE_STICKER_PACK intent is out with WhatsApp. */
    private var pending: PendingAdd? = null

    private data class PendingAdd(val id: String, val name: String, val own: Boolean)

    private val rows: Flow<List<SavedRow>> = combine(
        prefsRepository.favoritePackIds,
        catalogRepository.observePacks().catch { emit(emptyList()) },
        myPacksRepository.observeOwn(),
        myPacksRepository.observeInstalled(),
        sessions
    ) { favorites, catalog, own, installed, sessions ->
        val whitelistedInstalled = installed.filter { it.whitelisted }.map { it.id }.toSet()
        val remoteRows = catalog.filter { it.id in favorites }.map { pack ->
            SavedRow(
                id = pack.id,
                name = pack.name,
                animated = pack.animated,
                stickerCount = pack.stickerCount,
                metaLabel = "${formatAdds(pack.downloads)} adds",
                own = false,
                addState = sessions[pack.id]
                    ?: if (pack.id in whitelistedInstalled) AddState.Added else AddState.Idle,
                thumbModels = pack.thumbUrls.take(6),
                remote = pack
            )
        }
        val ownRows = own.filter { it.id in favorites }.map { pack ->
            SavedRow(
                id = pack.id,
                name = pack.name,
                animated = pack.animated,
                stickerCount = pack.stickerFiles.size,
                metaLabel = META_YOURS,
                own = true,
                addState = sessions[pack.id]
                    ?: if (pack.whitelisted) AddState.Added else AddState.Idle,
                thumbModels = pack.stickerFiles.take(6).map { File(pack.stickerFilePath(it)) }
            )
        }
        remoteRows + ownRows
    }

    val uiState: StateFlow<SavedUiState> = combine(rows, showNoWhatsApp) { rows, noWhatsApp ->
        SavedUiState(loading = false, rows = rows, showNoWhatsApp = noWhatsApp)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SavedUiState())

    // ---- Heart -------------------------------------------------------------

    /** Un-hearting removes the card; the design shows no undo toast. */
    fun onToggleFavorite(id: String) {
        viewModelScope.launch { prefsRepository.toggleFavorite(id) }
    }

    // ---- Add state machine (same as pack detail, per card) -----------------

    fun onAddClicked(id: String) {
        val row = uiState.value.rows.firstOrNull { it.id == id } ?: return
        when (row.addState) {
            is AddState.Downloading, AddState.Sent -> Unit
            AddState.Added -> toast(TOAST_ALREADY)
            AddState.Idle, is AddState.Failed -> beginAdd(row)
        }
    }

    private fun beginAdd(row: SavedRow) {
        if (!AddStickerPackFlow.isWhatsAppInstalled(appContext)) {
            showNoWhatsApp.value = true
            return
        }
        if (row.own) {
            // Own packs are already on disk: hand straight off to WhatsApp.
            sessions.update { it + (row.id to AddState.Sent) }
            sendToWhatsApp(row.id, row.name, own = true)
        } else {
            row.remote?.let(::startDownload)
        }
    }

    private fun startDownload(pack: StickerPack) {
        downloadedIds.remove(pack.id)
        addJobs[pack.id]?.cancel()
        addJobs[pack.id] = viewModelScope.launch {
            catalogRepository.addPack(pack).collect { state ->
                sessions.update { it + (pack.id to state) }
                if (state == AddState.Sent) {
                    downloadedIds.add(pack.id)
                    sendToWhatsApp(pack.id, pack.name, own = false)
                }
            }
        }
    }

    private fun sendToWhatsApp(id: String, name: String, own: Boolean) {
        pending = PendingAdd(id, name, own)
        viewModelScope.launch(ioDispatcher) {
            val intent = AddStickerPackFlow.createBestIntent(appContext, id, name)
            when {
                intent != null -> _events.send(SavedEvent.LaunchAddIntent(intent))
                AddStickerPackFlow.isWhatsAppInstalled(appContext) -> {
                    // Every installed WhatsApp already has the pack.
                    pending = null
                    persistWhitelisted(id, own, whitelisted = true)
                    sessions.update { it - id }
                    _events.send(SavedEvent.Toast(TOAST_ALREADY, false))
                }
                else -> {
                    pending = null
                    sessions.update { it - id }
                    showNoWhatsApp.value = true
                }
            }
        }
    }

    /** The parsed ENABLE_STICKER_PACK activity result, fed back by the screen. */
    fun onAddResult(result: AddStickerPackFlow.AddResult) {
        val current = pending ?: return
        pending = null
        viewModelScope.launch {
            when (result) {
                AddStickerPackFlow.AddResult.Added -> {
                    val verified = withContext(ioDispatcher) {
                        WhitelistCheck.isWhitelisted(appContext, current.id)
                    }
                    persistWhitelisted(current.id, current.own, verified)
                    downloadedIds.remove(current.id)
                    sessions.update { it - current.id }
                    toast(TOAST_ADDED, withCheck = true)
                }
                is AddStickerPackFlow.AddResult.Cancelled -> {
                    cleanUpAfterUnconfirmedAdd(current)
                    // WhatsApp rejected the pack -> Failed (retry); the user backing
                    // out returns the pill to idle, as the design's sent-frame says.
                    sessions.update {
                        val error = result.validationError
                        if (error != null) it + (current.id to AddState.Failed(error))
                        else it - current.id
                    }
                }
            }
        }
    }

    /** The screen could not launch the intent after all (uninstall race). */
    fun onAddLaunchFailed() {
        val current = pending ?: return
        pending = null
        viewModelScope.launch {
            cleanUpAfterUnconfirmedAdd(current)
            sessions.update { it - current.id }
            showNoWhatsApp.value = true
        }
    }

    fun dismissNoWhatsApp() {
        showNoWhatsApp.value = false
    }

    /** Removes the copy this session downloaded, so My Packs stays truthful. */
    private suspend fun cleanUpAfterUnconfirmedAdd(current: PendingAdd) {
        if (current.own || current.id !in downloadedIds) return
        downloadedIds.remove(current.id)
        runCatching { catalogRepository.removePack(current.id) }
    }

    private suspend fun persistWhitelisted(id: String, own: Boolean, whitelisted: Boolean) {
        if (own) {
            withContext(ioDispatcher) {
                runCatching {
                    myPacksRepository.refreshWhitelist { WhitelistCheck.isWhitelisted(appContext, it) }
                }
            }
        } else {
            runCatching { catalogRepository.setWhitelisted(id, whitelisted) }
        }
    }

    private fun toast(message: String, withCheck: Boolean = false) {
        _events.trySend(SavedEvent.Toast(message, withCheck))
    }
}
