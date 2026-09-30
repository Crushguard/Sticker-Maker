package com.piptechnologies.stickermaker.feature.home

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piptechnologies.stickermaker.R
import com.piptechnologies.stickermaker.core.data.di.IoDispatcher
import com.piptechnologies.stickermaker.core.data.prefs.PrefsRepository
import com.piptechnologies.stickermaker.core.data.repo.CatalogRepository
import com.piptechnologies.stickermaker.core.data.catalog.inLanguageOrder
import com.piptechnologies.stickermaker.core.design.components.AddVisualState
import com.piptechnologies.stickermaker.core.design.components.PackCover
import com.piptechnologies.stickermaker.core.model.AddState
import com.piptechnologies.stickermaker.core.model.Category
import com.piptechnologies.stickermaker.core.model.FallbackCategories
import com.piptechnologies.stickermaker.core.model.StickerPack
import com.piptechnologies.stickermaker.core.model.inCategory
import com.piptechnologies.stickermaker.core.model.withPacks
import com.piptechnologies.stickermaker.core.telemetry.AppAnalytics
import com.piptechnologies.stickermaker.core.ui.PendingToasts
import com.piptechnologies.stickermaker.core.ui.UiText
import com.piptechnologies.stickermaker.core.ui.addsLabel
import com.piptechnologies.stickermaker.core.ui.inAppLanguage
import com.piptechnologies.stickermaker.core.ui.nameText
import com.piptechnologies.stickermaker.core.ui.themeNameRes
import com.piptechnologies.stickermaker.feature.rating.RatingPromptController
import com.piptechnologies.stickermaker.whatsapp.AddStickerPackFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Fixed chips ahead of the theme chips (Prototype `chips`).
const val CHIP_TRENDING = "trending"
const val CHIP_SAVED = "saved"
const val CHIP_ANIMATED = "animated"


/** One chip in the Home filter row; [showHeart] marks the Saved chip. */
data class HomeChipUi(
    val id: String,
    val label: UiText,
    val showHeart: Boolean = false
)

/** One catalog pack as the Home card renders it. */
data class HomePackUi(
    val id: String,
    val name: String,
    val stickerCount: Int,
    val downloadsLabel: UiText,
    val animated: Boolean,
    val hue: Int,
    /** One strip for the card's circles; tiles 0 = no cover (hue placeholders). */
    val cover: PackCover,
    val favorite: Boolean,
    val addState: AddVisualState,
    val addProgress: Float
)

/** A download that finished and its ADD_PACK [intent], resolved off the main thread; the screen fires it. */
data class PendingWhatsAppAdd(
    val packId: String,
    val intent: Intent
)

/** One dark toast queued by the ViewModel; [withCheck] leads with a green check. */
data class HomeToast(
    val message: UiText,
    val withCheck: Boolean = false
)

/**
 * State for Home.
 *
 * @property loading true until the first catalog emission.
 * @property offline the catalog emitted empty (Firestore error / no cache),
 * so Home shows the full offline state with Retry.
 * @property activeChipId the effective chip: falls back to Trending when the
 * Saved chip lost its last heart or a theme chip's category left the catalog.
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
    val noResultsTitle: UiText = UiText.Raw(""),
    val whatsAppMissingPackId: String? = null,
    val pendingWhatsAppAdd: PendingWhatsAppAdd? = null
)

/**
 * Browse state and the card-pill add machine (Prototype `is.home` +
 * `startAdd`): filter chips over every theme, local search across
 * titles and theme names, favorites hearts, and per-card
 * idle -> downloading -> sent -> added, with failed -> retry.
 *
 * The repository stops at [AddState.Sent]; this resolves the ADD_PACK
 * intent, the screen fires it and reports WhatsApp's verdict back through
 * [onWhatsAppResult].
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val prefsRepository: PrefsRepository,
    @ApplicationContext private val appContext: Context,
    private val pendingToasts: PendingToasts,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
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
        val categories: List<Category>
    )

    private val controls = MutableStateFlow(Controls())
    private val packsMirror = MutableStateFlow<List<StickerPack>?>(null)
    private val installedMirror = MutableStateFlow<Set<String>>(emptySet())
    private val favoritesMirror = MutableStateFlow<Set<String>>(emptySet())
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
            // One subscription for the screen's life: a Retry's outcome is the next list that arrives.
            catalogRepository.observePacks().collect { packs ->
                packsMirror.value = packs
                if (pendingRetryToast) {
                    pendingRetryToast = false
                    if (packs.isEmpty()) _toasts.tryEmit(HomeToast(UiText.res(R.string.toast_still_offline)))
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
            catalogRepository.observeCategories().collect { remote ->
                categoriesMirror.value = remote.ifEmpty { FallbackCategories }
            }
        }
    }

    private val dataFlow = combine(
        packsMirror, installedMirror, favoritesMirror, categoriesMirror
    ) { packs, installed, favorites, categories ->
        HomeData(packs, installed, favorites, categories)
    }

    // Built off the main thread: every keystroke and download-progress tick rebuilds the state, search included.
    val uiState: StateFlow<HomeUiState> =
        combine(dataFlow, controls) { data, c -> buildState(data, c) }
            .flowOn(ioDispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** The search index of the catalog and categories last searched: rebuilt only when either changes. */
    @Volatile private var searchIndex: Triple<List<StickerPack>, List<Category>, HomeSearchIndex>? = null

    private fun searchIndexFor(packs: List<StickerPack>, categories: List<Category>): HomeSearchIndex {
        searchIndex?.let { (p, c, index) -> if (p === packs && c === categories) return index }
        return HomeSearchIndex(packs, categories).also { searchIndex = Triple(packs, categories, it) }
    }

    // ------------------------------------------------------------- events //

    fun onSelectChip(id: String) = controls.update { it.copy(chipId = id) }

    fun onOpenSearch() = controls.update { it.copy(searchOpen = true) }

    fun onCloseSearch() = controls.update { it.copy(searchOpen = false, query = "") }

    fun onQueryChange(query: String) = controls.update { it.copy(query = query) }

    /** Offline Retry: downloads the catalog again, or says it is still offline. */
    fun onRetry() {
        pendingRetryToast = true
        catalogRepository.retryCatalog()
    }

    fun onToggleFavorite(packId: String) {
        val turningOn = packId !in favoritesMirror.value
        viewModelScope.launch {
            prefsRepository.toggleFavorite(packId)
            if (turningOn) _toasts.tryEmit(HomeToast(UiText.res(R.string.toast_saved_heart)))
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
                _toasts.tryEmit(HomeToast(UiText.res(R.string.toast_already_in_whatsapp)))
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
    private fun onAlreadyInWhatsApp(packId: String) {
        controls.update { it.copy(pendingWhatsAppAdd = null) }
        setAddState(packId, AddState.Added)
        viewModelScope.launch { catalogRepository.setWhitelisted(packId, true) }
        _toasts.tryEmit(HomeToast(UiText.res(R.string.toast_added_to_whatsapp), withCheck = true))
    }

    /** WhatsApp's activity result for the pack launched last. */
    fun onWhatsAppResult(result: AddStickerPackFlow.AddResult) {
        val packId = awaitingResultFor ?: return
        awaitingResultFor = null
        when (result) {
            AddStickerPackFlow.AddResult.Added -> {
                AppAnalytics.logPackAdded(packId)
                RatingPromptController.onPackAdded(appContext)
                setAddState(packId, AddState.Added)
                viewModelScope.launch { catalogRepository.setWhitelisted(packId, true) }
                _toasts.tryEmit(HomeToast(UiText.res(R.string.toast_added_to_whatsapp), withCheck = true))
            }
            is AddStickerPackFlow.AddResult.Cancelled -> {
                AppAnalytics.logPackAddCancelled(packId, rejected = result.validationError != null)
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
        _toasts.tryEmit(HomeToast(UiText.res(R.string.toast_opening_play_store)))
    }

    /** A toast another screen left for Home (the name flow's "Added to WhatsApp"), handed out once. */
    fun takePendingToast(): UiText? = pendingToasts.take()

    // -------------------------------------------------------------- internals //

    private fun startDownload(packId: String) {
        val pack = packsMirror.value?.firstOrNull { it.id == packId } ?: return
        addJobs.remove(packId)?.cancel()
        addJobs[packId] = viewModelScope.launch {
            catalogRepository.addPack(pack).collect { state ->
                setAddState(packId, state)
                if (state is AddState.Sent) handOff(packId, pack.name)
            }
        }
    }

    /** The download is in: ask WhatsApp, off the main thread, what Add launches. */
    private suspend fun handOff(packId: String, packName: String) {
        val target = AddStickerPackFlow.resolveAddTarget(
            appContext, packId, packName, "home_add_intent", ioDispatcher
        )
        when (target) {
            is AddStickerPackFlow.AddTarget.Launch ->
                controls.update { it.copy(pendingWhatsAppAdd = PendingWhatsAppAdd(packId, target.intent)) }
            AddStickerPackFlow.AddTarget.AlreadyAdded -> onAlreadyInWhatsApp(packId)
            AddStickerPackFlow.AddTarget.NoWhatsApp -> onWhatsAppMissingAtLaunch(packId)
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
        // Categories without live packs (none of their own, none through alsoIn) get no chip.
        val categories = data.categories.withPacks()
        val chips = buildList {
            add(HomeChipUi(CHIP_TRENDING, UiText.res(R.string.home_chip_trending)))
            if (favCount > 0) {
                add(HomeChipUi(CHIP_SAVED, UiText.res(R.string.home_chip_saved, favCount), showHeart = true))
            }
            add(HomeChipUi(CHIP_ANIMATED, UiText.res(R.string.home_chip_animated)))
            categories.forEach { add(HomeChipUi(it.id, it.nameText())) }
        }
        val chip = effectiveChip(c.chipId, favCount, categories.mapTo(mutableSetOf()) { it.id })
        val query = c.query.trim()
        val list = filterPacks(catalog.orEmpty(), data, c.searchOpen, query, chip)
        val packsUi = list.map { pack ->
            val addState = effectiveAddState(pack.id, c.addStates, data.installed)
            HomePackUi(
                id = pack.id,
                name = pack.name,
                stickerCount = pack.stickerCount,
                downloadsLabel = addsLabel(pack.downloads) ?: UiText.Raw(""),
                animated = pack.animated,
                hue = pack.hue,
                cover = PackCover(pack.coverSmallUrl, pack.coverLargeUrl, pack.coverTiles),
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
            noResultsTitle = UiText.res(R.string.home_no_results_title, query),
            whatsAppMissingPackId = c.whatsAppMissingPackId,
            pendingWhatsAppAdd = c.pendingWhatsAppAdd
        )
    }

    /**
     * Prototype `homePacks()`: search and chips. The order is the catalog's rank (pinned, popular, newest)
     * grouped by what the reader can read (see inLanguageOrder); a pack also shows under the categories it
     * lists in alsoIn.
     */
    private fun filterPacks(
        catalog: List<StickerPack>,
        data: HomeData,
        searchOpen: Boolean,
        query: String,
        chip: String
    ): List<StickerPack> {
        val ordered = catalog.inLanguageOrder(appLanguageTag())
        var list = ordered
        if (searchOpen) {
            if (query.isNotEmpty()) list = searchIndexFor(catalog, data.categories).filter(ordered, query)
        } else {
            when (chip) {
                CHIP_TRENDING -> Unit
                CHIP_SAVED -> {
                    val saved = ordered.filter { it.id in data.favorites }
                    if (saved.isNotEmpty()) list = saved
                }
                CHIP_ANIMATED -> list = ordered.filter { it.animated }
                else -> list = list.filter { it.inCategory(chip) }
            }
        }
        return list
    }

    /** The app language as a BCP 47 tag ("pt-BR"), or the device's when following the system. */
    private fun appLanguageTag(): String =
        appContext.inAppLanguage().resources.configuration.locales[0].toLanguageTag()

    private fun effectiveChip(
        chipId: String,
        favCount: Int,
        categoryIds: Set<String>
    ): String = when {
        chipId == CHIP_SAVED && favCount == 0 -> CHIP_TRENDING
        chipId != CHIP_TRENDING && chipId != CHIP_SAVED && chipId != CHIP_ANIMATED &&
            chipId !in categoryIds -> CHIP_TRENDING
        else -> chipId
    }


    private fun AddState.toVisual(): AddVisualState = when (this) {
        AddState.Idle -> AddVisualState.Idle
        is AddState.Downloading -> AddVisualState.Downloading
        AddState.Sent -> AddVisualState.Sent
        AddState.Added -> AddVisualState.Added
        is AddState.Failed -> AddVisualState.Failed
    }
}
