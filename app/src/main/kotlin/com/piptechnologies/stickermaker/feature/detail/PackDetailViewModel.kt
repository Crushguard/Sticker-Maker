package com.piptechnologies.stickermaker.feature.detail

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piptechnologies.stickermaker.R
import com.piptechnologies.stickermaker.core.data.di.IoDispatcher
import com.piptechnologies.stickermaker.core.data.prefs.PrefsRepository
import com.piptechnologies.stickermaker.core.data.repo.CatalogRepository
import com.piptechnologies.stickermaker.core.data.repo.MyPacksRepository
import com.piptechnologies.stickermaker.core.model.AddState
import com.piptechnologies.stickermaker.core.model.InstalledPack
import com.piptechnologies.stickermaker.core.model.OwnPack
import com.piptechnologies.stickermaker.core.model.StickerPack
import com.piptechnologies.stickermaker.core.ui.UiText
import com.piptechnologies.stickermaker.whatsapp.AddStickerPackFlow
import com.piptechnologies.stickermaker.whatsapp.WhitelistCheck
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


// ---- UI state --------------------------------------------------------------

/** One grid cell: a Coil model (https URL for catalog packs, [File] for local packs). */
data class DetailSticker(val key: String, val model: Any)

data class PackDetailUiState(
    val loading: Boolean = true,
    /** The pack could not be resolved (no network, no local copy): offline empty state. */
    val unavailable: Boolean = false,
    val packId: String = "",
    val title: String = "",
    /** "18 stickers · 96.4K adds" / "26 stickers · Made by you". */
    val metaLine: UiText = UiText.Raw(""),
    val animated: Boolean = false,
    val own: Boolean = false,
    val stickers: List<DetailSticker> = emptyList(),
    val favorite: Boolean = false,
    val addState: AddState = AddState.Idle,
    val showNoWhatsApp: Boolean = false
)

/** One-shot effects the screen executes (intent launch, dark toasts). */
sealed interface PackDetailEvent {
    data class LaunchAddIntent(val intent: Intent) : PackDetailEvent
    data class Toast(val message: UiText, val withCheck: Boolean) : PackDetailEvent
}

/** What the grid renders: the remote catalog pack, or a local copy as fallback. */
private sealed interface DetailContent {
    data object Loading : DetailContent
    data object Unavailable : DetailContent
    data class Remote(val pack: StickerPack) : DetailContent
    data class LocalOwn(val pack: OwnPack) : DetailContent
    data class LocalInstalled(val pack: InstalledPack) : DetailContent
}

/**
 * Pack detail + the full add state machine:
 * Idle -> tap -> [CatalogRepository.addPack] (Downloading with real progress -> Sent)
 * -> ENABLE_STICKER_PACK intent (launched by the screen) -> result:
 * confirmed => whitelist check => Added (+ persisted); user cancel => back to Idle
 * (the design's "Cancel returns to idle"), local copy removed; WhatsApp validation
 * error => Failed with retry. Already-whitelisted packs open as Added.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PackDetailViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val catalogRepository: CatalogRepository,
    private val myPacksRepository: MyPacksRepository,
    private val prefsRepository: PrefsRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val packId = MutableStateFlow<String?>(null)
    private val content = MutableStateFlow<DetailContent>(DetailContent.Loading)
    private val session = MutableStateFlow<AddState?>(null)
    private val showNoWhatsApp = MutableStateFlow(false)

    private val _events = Channel<PackDetailEvent>(Channel.BUFFERED)
    val events: Flow<PackDetailEvent> = _events.receiveAsFlow()

    private var addJob: Job? = null

    /** True once this add session downloaded + registered the pack (cleanup on failure). */
    private var downloadedThisSession = false

    /** WhatsApp reports the pack as added in every installed WhatsApp app. */
    private val whitelisted: Flow<Boolean> = packId.flatMapLatest { id ->
        if (id == null) {
            flowOf(false)
        } else {
            combine(
                myPacksRepository.observeInstalled(),
                myPacksRepository.observeOwn()
            ) { installed, own ->
                installed.any { it.id == id && it.whitelisted } ||
                    own.any { it.id == id && it.whitelisted }
            }
        }
    }

    private val favorite: Flow<Boolean> = packId.flatMapLatest { id ->
        if (id == null) flowOf(false) else prefsRepository.favoritePackIds.map { id in it }
    }

    val uiState: StateFlow<PackDetailUiState> = combine(
        content, whitelisted, favorite, session, showNoWhatsApp
    ) { content, whitelisted, favorite, session, noWhatsApp ->
        buildState(content, whitelisted, favorite, session, noWhatsApp)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PackDetailUiState())

    /** Idempotent entry point the screen calls with its nav argument. */
    fun start(id: String) {
        if (packId.value == id) return
        packId.value = id
        resolve(id)
    }

    /** The offline empty state's Retry. */
    fun retryLoad() {
        packId.value?.let(::resolve)
    }

    private fun resolve(id: String) {
        viewModelScope.launch {
            content.value = DetailContent.Loading
            val remote = runCatching { catalogRepository.getPack(id) }.getOrNull()
            if (remote != null) {
                content.value = DetailContent.Remote(remote)
                return@launch
            }
            val own = runCatching {
                myPacksRepository.observeOwn().first().firstOrNull { it.id == id }
            }.getOrNull()
            if (own != null) {
                content.value = DetailContent.LocalOwn(own)
                return@launch
            }
            val installed = runCatching {
                myPacksRepository.observeInstalled().first().firstOrNull { it.id == id }
            }.getOrNull()
            content.value =
                if (installed != null) DetailContent.LocalInstalled(installed)
                else DetailContent.Unavailable
        }
    }

    // ---- Add state machine -------------------------------------------------

    /** The Add bar tap, in every state. */
    fun onAddClicked() {
        when (uiState.value.addState) {
            is AddState.Downloading, AddState.Sent -> Unit
            AddState.Added -> toast(UiText.res(R.string.toast_already_in_whatsapp))
            AddState.Idle, is AddState.Failed -> beginAdd()
        }
    }

    private fun beginAdd() {
        // The design shows the WhatsApp dialog "the moment Add is tapped".
        if (!AddStickerPackFlow.isWhatsAppInstalled(appContext)) {
            showNoWhatsApp.value = true
            return
        }
        downloadedThisSession = false
        when (val c = content.value) {
            is DetailContent.Remote -> startDownload(c.pack)
            // Local packs are already on disk: hand straight off to WhatsApp.
            is DetailContent.LocalOwn -> {
                session.value = AddState.Sent
                sendToWhatsApp(c.pack.id, c.pack.name)
            }
            is DetailContent.LocalInstalled -> {
                session.value = AddState.Sent
                sendToWhatsApp(c.pack.id, c.pack.name)
            }
            else -> Unit
        }
    }

    private fun startDownload(pack: StickerPack) {
        addJob?.cancel()
        addJob = viewModelScope.launch {
            catalogRepository.addPack(pack).collect { state ->
                session.value = state
                if (state == AddState.Sent) {
                    downloadedThisSession = true
                    sendToWhatsApp(pack.id, pack.name)
                }
            }
        }
    }

    private fun sendToWhatsApp(id: String, name: String) {
        viewModelScope.launch(ioDispatcher) {
            val intent = AddStickerPackFlow.createBestIntent(appContext, id, name)
            when {
                intent != null -> _events.send(PackDetailEvent.LaunchAddIntent(intent))
                AddStickerPackFlow.isWhatsAppInstalled(appContext) -> {
                    // Nothing to launch: every installed WhatsApp already has the pack.
                    persistWhitelisted(id, true)
                    session.value = AddState.Added
                    _events.send(PackDetailEvent.Toast(UiText.res(R.string.toast_already_in_whatsapp), false))
                }
                else -> {
                    session.value = AddState.Idle
                    showNoWhatsApp.value = true
                }
            }
        }
    }

    /** The parsed ENABLE_STICKER_PACK activity result, fed back by the screen. */
    fun onAddResult(result: AddStickerPackFlow.AddResult) {
        val id = packId.value ?: return
        viewModelScope.launch {
            when (result) {
                AddStickerPackFlow.AddResult.Added -> {
                    val verified = withContext(ioDispatcher) {
                        WhitelistCheck.isWhitelisted(appContext, id)
                    }
                    persistWhitelisted(id, verified)
                    session.value = AddState.Added
                    _events.send(PackDetailEvent.Toast(UiText.res(R.string.toast_added_to_whatsapp), true))
                }
                is AddStickerPackFlow.AddResult.Cancelled -> {
                    cleanUpAfterUnconfirmedAdd(id)
                    // WhatsApp rejected the pack -> Failed (retry); the user backing out
                    // returns to idle, exactly as the design's sent-frame says.
                    session.value = result.validationError
                        ?.let { AddState.Failed(it) }
                        ?: AddState.Idle
                }
            }
        }
    }

    /** The screen could not launch the intent after all (uninstall race). */
    fun onAddLaunchFailed() {
        val id = packId.value
        viewModelScope.launch {
            if (id != null) cleanUpAfterUnconfirmedAdd(id)
            session.value = AddState.Idle
            showNoWhatsApp.value = true
        }
    }

    /** Removes the copy this session downloaded, so My Packs stays truthful. */
    private suspend fun cleanUpAfterUnconfirmedAdd(id: String) {
        if (!downloadedThisSession) return
        downloadedThisSession = false
        runCatching { catalogRepository.removePack(id) }
    }

    private suspend fun persistWhitelisted(id: String, whitelisted: Boolean) {
        if (content.value is DetailContent.LocalOwn) {
            withContext(ioDispatcher) {
                runCatching {
                    myPacksRepository.refreshWhitelist { WhitelistCheck.isWhitelisted(appContext, it) }
                }
            }
        } else {
            runCatching { catalogRepository.setWhitelisted(id, whitelisted) }
        }
    }

    // ---- Heart + sheets ----------------------------------------------------

    fun onToggleFavorite() {
        val id = packId.value ?: return
        val wasFavorite = uiState.value.favorite
        viewModelScope.launch {
            prefsRepository.toggleFavorite(id)
            if (!wasFavorite) _events.send(PackDetailEvent.Toast(UiText.res(R.string.toast_saved_heart), false))
        }
    }

    fun dismissNoWhatsApp() {
        showNoWhatsApp.value = false
    }

    private fun toast(message: UiText, withCheck: Boolean = false) {
        _events.trySend(PackDetailEvent.Toast(message, withCheck))
    }

    // ---- Mapping -----------------------------------------------------------

    private fun buildState(
        content: DetailContent,
        whitelisted: Boolean,
        favorite: Boolean,
        session: AddState?,
        noWhatsApp: Boolean
    ): PackDetailUiState {
        val addState = session ?: if (whitelisted) AddState.Added else AddState.Idle
        val id = packId.value.orEmpty()
        return when (content) {
            DetailContent.Loading -> PackDetailUiState(
                loading = true, packId = id, favorite = favorite, showNoWhatsApp = noWhatsApp
            )
            DetailContent.Unavailable -> PackDetailUiState(
                loading = false, unavailable = true, packId = id,
                favorite = favorite, showNoWhatsApp = noWhatsApp
            )
            is DetailContent.Remote -> {
                val pack = content.pack
                PackDetailUiState(
                    loading = false,
                    packId = pack.id,
                    title = pack.name,
                    metaLine = UiText.res(
                        R.string.meta_join,
                        UiText.plural(R.plurals.sticker_count, pack.stickerCount),
                        UiText.res(R.string.pack_adds, UiText.Compact(pack.downloads))
                    ),
                    animated = pack.animated,
                    stickers = pack.stickerUrls.ifEmpty { pack.thumbUrls }
                        .mapIndexed { index, url -> DetailSticker("url-$index", url) },
                    favorite = favorite,
                    addState = addState,
                    showNoWhatsApp = noWhatsApp
                )
            }
            is DetailContent.LocalOwn -> {
                val pack = content.pack
                PackDetailUiState(
                    loading = false,
                    packId = pack.id,
                    title = pack.name,
                    metaLine = UiText.res(
                        R.string.meta_join,
                        UiText.plural(R.plurals.sticker_count, pack.stickerFiles.size),
                        UiText.res(R.string.detail_made_by_you)
                    ),
                    animated = pack.animated,
                    own = true,
                    stickers = pack.stickerFiles.mapIndexed { index, file ->
                        DetailSticker("file-$index", File(pack.stickerFilePath(file)))
                    },
                    favorite = favorite,
                    addState = addState,
                    showNoWhatsApp = noWhatsApp
                )
            }
            is DetailContent.LocalInstalled -> {
                val pack = content.pack
                PackDetailUiState(
                    loading = false,
                    packId = pack.id,
                    title = pack.name,
                    metaLine = UiText.plural(R.plurals.sticker_count, pack.stickerFiles.size),
                    animated = pack.animated,
                    stickers = pack.stickerFiles.mapIndexed { index, file ->
                        DetailSticker("file-$index", File(pack.stickerFilePath(file)))
                    },
                    favorite = favorite,
                    addState = addState,
                    showNoWhatsApp = noWhatsApp
                )
            }
        }
    }
}
