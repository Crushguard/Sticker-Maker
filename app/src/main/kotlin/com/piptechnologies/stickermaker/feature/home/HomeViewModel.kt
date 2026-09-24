package com.piptechnologies.stickermaker.feature.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piptechnologies.stickermaker.core.data.prefs.PrefsRepository
import com.piptechnologies.stickermaker.core.data.repo.CatalogRepository
import com.piptechnologies.stickermaker.core.design.components.AddVisualState
import com.piptechnologies.stickermaker.core.model.AddState
import com.piptechnologies.stickermaker.core.model.Category
import com.piptechnologies.stickermaker.core.model.StickerPack
import com.piptechnologies.stickermaker.feature.customize.FallbackCategories
import com.piptechnologies.stickermaker.whatsapp.AddStickerPackFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Fixed chips ahead of the user's themes (Prototype `chips`).
const val CHIP_TRENDING = "trending"
const val CHIP_SAVED = "saved"
const val CHIP_ANIMATED = "animated"

// Exact Prototype copy.
private const val LABEL_TRENDING = "Trending"
private const val LABEL_ANIMATED = "Animated"
private const val TOAST_SAVED = "Saved · find it under ♥ Saved on Home"
private const val TOAST_ALREADY_ADDED = "Already in WhatsApp"
private const val TOAST_ADDED = "Added to WhatsApp"
private const val TOAST_STILL_OFFLINE = "Still offline. Try again in a moment."
private const val TOAST_OPENING_PLAY_STORE = "Opening Play Store…"

/** One chip in the Home filter row; [showHeart] marks the Saved chip. */
data class HomeChipUi(
    val id: String,
    val label: String,
    val showHeart: Boolean = false
)

/** One catalog pack as the Home card renders it. */
data class HomePackUi(
    val id: String,
    val name: String,
    val stickerCount: Int,
    val downloadsLabel: String,
    val animated: Boolean,
    val hue: Int,
    val thumbUrls: List<String>,
    val favorite: Boolean,
    val addState: AddVisualState,
    val addProgress: Float
)

/** A download that finished; the screen must now fire the ADD_PACK intent. */
data class PendingWhatsAppAdd(
    val packId: String,
    val packName: String
)

/** One dark toast queued by the ViewModel; [withCheck] leads with a green check. */
data class HomeToast(
    val message: String,
    val withCheck: Boolean = false
)

/**
 * State for Home.
 *
 * @property loading true until the first catalog emission.
 * @property offline the catalog emitted empty (Firestore error / no cache),
 * so Home shows the full offline state with Retry.
 * @property activeChipId the effective chip: falls back to Trending when the
 * Saved chip lost its last heart or a theme chip was deselected in Settings.
 * @property whatsAppMissingPackId non-null shows the "WhatsApp isn't
 * installed" confirmation sheet.
 * @property pendingWhatsAppAdd non-null makes the screen launch WhatsApp's
 * own confirm via the ENABLE_STICKER_PACK activity result.
 */
data class HomeUiState(
    val loading: Boolean = true,
    val offline: Boolean = false,
    val searchOpen: Boolean = false,
    val query: String = "",
    val chips: List<HomeChipUi> = emptyList(),
    val activeChipId: String = CHIP_TRENDING,
    val packs: List<HomePackUi> = emptyList(),
    val noResults: Boolean = false,
    val noResultsTitle: String = "",
    val whatsAppMissingPackId: String? = null,
    val pendingWhatsAppAdd: PendingWhatsAppAdd? = null
)

/**
 * Browse state and the card-pill add machine (Prototype `is.home` +
 * `startAdd`): filter chips over the user's themes, local search across
 * titles and theme names, favorites hearts, and per-card
 * idle -> downloading -> sent -> added, with failed -> retry.
 *
 * The repository stops at [AddState.Sent]; the screen fires the ADD_PACK
 * intent and reports WhatsApp's verdict back through [onWhatsAppResult].
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val prefsRepository: PrefsRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    /** Screen-local controls folded into one flow for the combine below. */
    private data class Controls(
        val chipId: String = CHIP_TRENDING,
        val searchOpen: Boolean = false,
        val query: String = "",
        val addStates: Map<String, AddState> = emptyMap(),
        val whatsAppMissingPackId: String? = null,
        val pendingWhatsAppAdd: PendingWhatsAppAdd? = null
    )

    private data class HomeData(
        val packs: List<StickerPack>?,
        val installed: Set<String>,
        val favorites: Set<String>,
        val themes: Set<String>,
        val categories: List<Category>
    )

    private val controls = MutableStateFlow(Controls())
    private val retrySignal = MutableStateFlow(0)
    private val packsMirror = MutableStateFlow<List<StickerPack>?>(null)
    private val installedMirror = MutableStateFlow<Set<String>>(emptySet())
    private val favoritesMirror = MutableStateFlow<Set<String>>(emptySet())
    private val themesMirror = MutableStateFlow<Set<String>>(emptySet())
    private val categoriesMirror = MutableStateFlow(FallbackCategories)

    private val _toasts = MutableSharedFlow<HomeToast>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** Dark-toast queue the screen drains into its ToastHost. */
    val toasts: SharedFlow<HomeToast> = _toasts.asSharedFlow()

    private val addJobs = mutableMapOf<String, Job>()
    private var awaitingResultFor: String? = null
    private var pendingRetryToast = false

    init {
        viewModelScope.launch {
            retrySignal.collectLatest {
                catalogRepository.observePacks().collect { packs ->
                    packsMirror.value = packs
                    if (pendingRetryToast) {
                        pendingRetryToast = false
                        if (packs.isEmpty()) _toasts.tryEmit(HomeToast(TOAST_STILL_OFFLINE))
                    }
                }
            }
        }
        viewModelScope.launch {
            catalogRepository.observeInstalledIds().collect { ids ->
                installedMirror.value = ids
                // A cancelled add leaves an Idle override that masks the pack
                // while its local copy is deleted; drop it once really gone.
                controls.update { c ->
                    val cleaned = c.addStates.filterNot { (id, st) ->
                        st is AddState.Idle && id !in ids
                    }
                    if (cleaned.size == c.addStates.size) c else c.copy(addStates = cleaned)
                }
            }
        }
        viewModelScope.launch {
            prefsRepository.favoritePackIds.collect { favoritesMirror.value = it }
        }
        viewModelScope.launch {
            prefsRepository.selectedThemes.collect { themesMirror.value = it }
        }
        viewModelScope.launch {
            catalogRepository.observeCategories().collect { remote ->
                categoriesMirror.value = remote.ifEmpty { FallbackCategories }
            }
        }
    }

    private val dataFlow = combine(
        packsMirror, installedMirror, favoritesMirror, themesMirror, categoriesMirror
    ) { packs, installed, favorites, themes, categories ->
        HomeData(packs, installed, favorites, themes, categories)
    }

    val uiState: StateFlow<HomeUiState> =
        combine(dataFlow, controls) { data, c -> buildState(data, c) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    // ------------------------------------------------------------- events //

    fun onSelectChip(id: String) = controls.update { it.copy(chipId = id) }

    fun onOpenSearch() = controls.update { it.copy(searchOpen = true) }

    fun onCloseSearch() = controls.update { it.copy(searchOpen = false, query = "") }

    fun onQueryChange(query: String) = controls.update { it.copy(query = query) }

    /** Offline Retry: resubscribes the catalog stream. */
    fun onRetry() {
        pendingRetryToast = true
        retrySignal.update { it + 1 }
    }

    fun onToggleFavorite(packId: String) {
        val turningOn = packId !in favoritesMirror.value
        viewModelScope.launch {
            prefsRepository.toggleFavorite(packId)
            if (turningOn) _toasts.tryEmit(HomeToast(TOAST_SAVED))
        }
    }

    /**
     * The card pill (Prototype `startAdd`): ignores taps mid-flight, toasts
     * on an already-added pack, raises the missing-WhatsApp sheet, otherwise
     * downloads (Failed retries from zero).
     */
    fun onAddClicked(packId: String) {
        when (effectiveAddState(packId, controls.value.addStates, installedMirror.value)) {
            is AddState.Downloading, AddState.Sent -> return
            AddState.Added -> {
                _toasts.tryEmit(HomeToast(TOAST_ALREADY_ADDED))
                return
            }
            else -> Unit
        }
        if (!AddStickerPackFlow.isWhatsAppInstalled(appContext)) {
            controls.update { it.copy(whatsAppMissingPackId = packId) }
            return
        }
        startDownload(packId)
    }

    /** The screen launched the ADD_PACK intent for [packId]. */
    fun onWhatsAppLaunched(packId: String) {
        awaitingResultFor = packId
        controls.update { it.copy(pendingWhatsAppAdd = null) }
    }

    /** WhatsApp disappeared between the check and the launch. */
    fun onWhatsAppMissingAtLaunch(packId: String) {
        setAddState(packId, AddState.Idle)
        controls.update {
            it.copy(pendingWhatsAppAdd = null, whatsAppMissingPackId = packId)
        }
        viewModelScope.launch { catalogRepository.removePack(packId) }
    }

    /** Both WhatsApp apps already carry the pack: nothing to launch. */
    fun onAlreadyInWhatsApp(packId: String) {
        controls.update { it.copy(pendingWhatsAppAdd = null) }
        setAddState(packId, AddState.Added)
        viewModelScope.launch { catalogRepository.setWhitelisted(packId, true) }
        _toasts.tryEmit(HomeToast(TOAST_ADDED, withCheck = true))
    }

    /** WhatsApp's activity result for the pack launched last. */
    fun onWhatsAppResult(result: AddStickerPackFlow.AddResult) {
        val packId = awaitingResultFor ?: return
        awaitingResultFor = null
        when (result) {
            AddStickerPackFlow.AddResult.Added -> {
                setAddState(packId, AddState.Added)
                viewModelScope.launch { catalogRepository.setWhitelisted(packId, true) }
                _toasts.tryEmit(HomeToast(TOAST_ADDED, withCheck = true))
            }
            is AddStickerPackFlow.AddResult.Cancelled -> {
                // Prototype cancelWA: back to idle; drop the local copy so a
                // re-add runs the full download again.
                setAddState(packId, AddState.Idle)
                viewModelScope.launch { catalogRepository.removePack(packId) }
            }
        }
    }

    fun onDismissWhatsAppMissing() =
        controls.update { it.copy(whatsAppMissingPackId = null) }

    /** "Get WhatsApp": the screen opens the store page after this. */
    fun onGetWhatsApp() {
        controls.update { it.copy(whatsAppMissingPackId = null) }
        _toasts.tryEmit(HomeToast(TOAST_OPENING_PLAY_STORE))
    }

    // -------------------------------------------------------------- internals //

    private fun startDownload(packId: String) {
        val pack = packsMirror.value?.firstOrNull { it.id == packId } ?: return
        addJobs.remove(packId)?.cancel()
        addJobs[packId] = viewModelScope.launch {
            catalogRepository.addPack(pack).collect { state ->
                setAddState(packId, state)
                if (state is AddState.Sent) {
                    controls.update {
                        it.copy(pendingWhatsAppAdd = PendingWhatsAppAdd(packId, pack.name))
                    }
                }
            }
        }
    }

    private fun setAddState(packId: String, state: AddState) =
        controls.update { it.copy(addStates = it.addStates + (packId to state)) }

    private fun effectiveAddState(
        packId: String,
        transient: Map<String, AddState>,
        installed: Set<String>
    ): AddState = transient[packId]
        ?: if (packId in installed) AddState.Added else AddState.Idle

    private fun buildState(data: HomeData, c: Controls): HomeUiState {
        val catalog = data.packs
        val loading = catalog == null
        val offline = catalog != null && catalog.isEmpty()
        val catalogIds = catalog.orEmpty().mapTo(mutableSetOf()) { it.id }
        val favCount = data.favorites.count { it in catalogIds }
        val selectedCategories = data.categories.filter { it.id in data.themes }
        val chips = buildList {
            add(HomeChipUi(CHIP_TRENDING, LABEL_TRENDING))
            if (favCount > 0) add(HomeChipUi(CHIP_SAVED, "Saved · $favCount", showHeart = true))
            add(HomeChipUi(CHIP_ANIMATED, LABEL_ANIMATED))
            selectedCategories.forEach { add(HomeChipUi(it.id, it.name)) }
        }
        val chip = effectiveChip(c.chipId, favCount, selectedCategories.mapTo(mutableSetOf()) { it.id })
        val query = c.query.trim()
        val list = filterPacks(catalog.orEmpty(), data, c.searchOpen, query, chip)
        val packsUi = list.map { pack ->
            val addState = effectiveAddState(pack.id, c.addStates, data.installed)
            HomePackUi(
                id = pack.id,
                name = pack.name,
                stickerCount = pack.stickerCount,
                downloadsLabel = formatAdds(pack.downloads),
                animated = pack.animated,
                hue = pack.hue,
                thumbUrls = pack.thumbUrls.take(6),
                favorite = pack.id in data.favorites,
                addState = addState.toVisual(),
                addProgress = (addState as? AddState.Downloading)?.progress ?: 0f
            )
        }
        val noResults = c.searchOpen && query.isNotEmpty() && packsUi.isEmpty() && !offline && !loading
        return HomeUiState(
            loading = loading,
            offline = offline,
            searchOpen = c.searchOpen,
            query = c.query,
            chips = chips,
            activeChipId = chip,
            packs = packsUi,
            noResults = noResults,
            noResultsTitle = "No packs for “$query”",
            whatsAppMissingPackId = c.whatsAppMissingPackId,
            pendingWhatsAppAdd = c.pendingWhatsAppAdd
        )
    }

    /** Prototype `homePacks()`, verbatim: themes baseline, search, chips, trending sort. */
    private fun filterPacks(
        catalog: List<StickerPack>,
        data: HomeData,
        searchOpen: Boolean,
        query: String,
        chip: String
    ): List<StickerPack> {
        var list = catalog.filter { data.themes.isEmpty() || it.category in data.themes }
        if (searchOpen) {
            if (query.isNotEmpty()) {
                val q = query.lowercase()
                list = catalog.filter { pack ->
                    pack.name.lowercase().contains(q) ||
                        themeLabel(pack.category, data.categories).lowercase().contains(q)
                }
            }
        } else {
            when (chip) {
                CHIP_TRENDING -> Unit
                CHIP_SAVED -> {
                    val saved = catalog.filter { it.id in data.favorites }
                    if (saved.isNotEmpty()) list = saved
                }
                CHIP_ANIMATED -> list = catalog.filter { it.animated }
                else -> list = list.filter { it.category == chip }
            }
        }
        return list.sortedByDescending { it.downloads }
    }

    private fun themeLabel(categoryId: String, categories: List<Category>): String =
        categories.firstOrNull { it.id == categoryId }?.name ?: categoryId

    private fun effectiveChip(
        chipId: String,
        favCount: Int,
        selectedThemeIds: Set<String>
    ): String = when {
        chipId == CHIP_SAVED && favCount == 0 -> CHIP_TRENDING
        chipId != CHIP_TRENDING && chipId != CHIP_SAVED && chipId != CHIP_ANIMATED &&
            chipId !in selectedThemeIds -> CHIP_TRENDING
        else -> chipId
    }

    /** Prototype `fmt`: 96400 -> "96.4K", 1000 -> "1K", 640 -> "640". */
    private fun formatAdds(downloads: Long): String {
        val number = if (downloads >= 1000) {
            String.format(Locale.US, "%.1f", downloads / 1000.0).removeSuffix(".0") + "K"
        } else {
            downloads.toString()
        }
        return "$number adds"
    }

    private fun AddState.toVisual(): AddVisualState = when (this) {
        AddState.Idle -> AddVisualState.Idle
        is AddState.Downloading -> AddVisualState.Downloading
        AddState.Sent -> AddVisualState.Sent
        AddState.Added -> AddVisualState.Added
        is AddState.Failed -> AddVisualState.Failed
    }
}
