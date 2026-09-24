package com.piptechnologies.stickermaker.feature.mypacks

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piptechnologies.stickermaker.core.data.di.IoDispatcher
import com.piptechnologies.stickermaker.core.data.prefs.PrefsRepository
import com.piptechnologies.stickermaker.core.data.repo.CatalogRepository
import com.piptechnologies.stickermaker.core.data.repo.MyPacksRepository
import com.piptechnologies.stickermaker.core.model.AddState
import com.piptechnologies.stickermaker.whatsapp.AddStickerPackFlow
import com.piptechnologies.stickermaker.whatsapp.WhitelistCheck
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---- Copy: exact strings from design/Prototype.dc.html ---------------------

internal const val TITLE_MY_PACKS = "My Packs"
internal const val SECTION_IN_WHATSAPP = "In WhatsApp"
internal const val SECTION_MADE_BY_YOU = "Made by you"
internal const val META_YOURS = "yours"

internal const val MENU_READD = "Re-add to WhatsApp"
internal const val MENU_ADD = "Add to WhatsApp"
internal const val MENU_REMOVE = "Remove from this app"
internal const val MENU_DELETE = "Delete pack"

internal const val REMOVE_BODY =
    "Deletes the local copy. WhatsApp keeps the pack until you remove it there."
internal const val REMOVE_CONFIRM = "Remove"
internal const val DELETE_BODY =
    "Deletes the pack and its stickers from this phone. This can't be undone. " +
        "If you added it, WhatsApp keeps its copy."
internal const val DELETE_CONFIRM = "Delete"
internal const val CONFIRM_KEEP = "Keep"

internal const val TOAST_REMOVED = "Removed from this app"
internal const val TOAST_DELETED = "Pack deleted"
internal const val TOAST_ADDED = "Added to WhatsApp"
internal const val TOAST_ALREADY = "Already in WhatsApp"

internal const val EMPTY_TITLE = "No packs yet"
internal const val EMPTY_BODY =
    "Packs you add to WhatsApp show up here, along with the ones you make."
internal const val EMPTY_PRIMARY = "Browse packs"
internal const val EMPTY_GHOST = "Make your own"

internal const val INFO_REMOVAL =
    "Removing a pack here deletes this app's copy. WhatsApp keeps its own until you remove it there."

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

/** One card in either section. */
data class MyPackRow(
    val id: String,
    val name: String,
    val animated: Boolean,
    val stickerCount: Int,
    /** "96.4K adds" for catalog packs (empty offline), "yours" for own packs. */
    val metaLabel: String,
    val own: Boolean,
    val whitelisted: Boolean,
    val addState: AddState,
    val thumbFiles: List<File>
)

/** The two confirmation sheets the ⋯ menu can open. */
sealed interface MyPacksConfirm {
    val packId: String
    val packName: String

    data class RemoveInstalled(override val packId: String, override val packName: String) : MyPacksConfirm
    data class DeleteOwn(override val packId: String, override val packName: String) : MyPacksConfirm
}

data class MyPacksUiState(
    val loading: Boolean = true,
    val installed: List<MyPackRow> = emptyList(),
    val own: List<MyPackRow> = emptyList(),
    /** Hearted-pack count for the toolbar heart's rose badge. */
    val savedCount: Int = 0,
    val menuFor: MyPackRow? = null,
    val confirm: MyPacksConfirm? = null,
    val showNoWhatsApp: Boolean = false
) {
    val empty: Boolean get() = !loading && installed.isEmpty() && own.isEmpty()
}

sealed interface MyPacksEvent {
    data class LaunchAddIntent(val intent: Intent) : MyPacksEvent
    data class Toast(val message: String, val withCheck: Boolean) : MyPacksEvent
}

private data class MyPacksRows(
    val installed: List<MyPackRow>,
    val own: List<MyPackRow>,
    val savedCount: Int
)

/**
 * My Packs: installed catalog packs ("In WhatsApp") and packs made in the app
 * ("Made by you"), the ⋯ card menu with re-add / remove / delete, and the
 * WhatsApp whitelist refresh on every resume.
 */
@HiltViewModel
class MyPacksViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val myPacksRepository: MyPacksRepository,
    private val catalogRepository: CatalogRepository,
    prefsRepository: PrefsRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    /** Per-pack add sessions layered over the persisted whitelist state. */
    private val sessions = MutableStateFlow<Map<String, AddState>>(emptyMap())
    private val menuForId = MutableStateFlow<String?>(null)
    private val confirm = MutableStateFlow<MyPacksConfirm?>(null)
    private val showNoWhatsApp = MutableStateFlow(false)

    private val _events = Channel<MyPacksEvent>(Channel.BUFFERED)
    val events: Flow<MyPacksEvent> = _events.receiveAsFlow()

    /** The pack whose ENABLE_STICKER_PACK intent is out with WhatsApp. */
    private var pending: PendingAdd? = null

    private data class PendingAdd(val id: String, val own: Boolean)

    private val rows: Flow<MyPacksRows> = combine(
        myPacksRepository.observeInstalled(),
        myPacksRepository.observeOwn(),
        catalogRepository.observePacks()
            .catch { emit(emptyList()) }
            .onStart { emit(emptyList()) },
        prefsRepository.favoritePackIds,
        sessions
    ) { installed, own, catalog, favorites, sessions ->
        val catalogById = catalog.associateBy { it.id }
        val installedRows = installed.map { pack ->
            MyPackRow(
                id = pack.id,
                name = pack.name,
                animated = pack.animated,
                stickerCount = pack.stickerFiles.size,
                metaLabel = catalogById[pack.id]?.let { "${formatAdds(it.downloads)} adds" }.orEmpty(),
                own = false,
                whitelisted = pack.whitelisted,
                addState = sessions[pack.id]
                    ?: if (pack.whitelisted) AddState.Added else AddState.Idle,
                thumbFiles = pack.stickerFiles.take(6).map { File(pack.stickerFilePath(it)) }
            )
        }
        val ownRows = own.map { pack ->
            MyPackRow(
                id = pack.id,
                name = pack.name,
                animated = pack.animated,
                stickerCount = pack.stickerFiles.size,
                metaLabel = META_YOURS,
                own = true,
                whitelisted = pack.whitelisted,
                addState = sessions[pack.id]
                    ?: if (pack.whitelisted) AddState.Added else AddState.Idle,
                thumbFiles = pack.stickerFiles.take(6).map { File(pack.stickerFilePath(it)) }
            )
        }
        val knownIds = catalog.map { it.id }.toSet() + own.map { it.id }
        MyPacksRows(
            installed = installedRows,
            own = ownRows,
            savedCount = if (catalog.isEmpty()) favorites.size
            else favorites.count { it in knownIds }
        )
    }

    val uiState: StateFlow<MyPacksUiState> = combine(
        rows, menuForId, confirm, showNoWhatsApp
    ) { rows, menuId, confirm, noWhatsApp ->
        MyPacksUiState(
            loading = false,
            installed = rows.installed,
            own = rows.own,
            savedCount = rows.savedCount,
            menuFor = menuId?.let { id -> (rows.installed + rows.own).firstOrNull { it.id == id } },
            confirm = confirm,
            showNoWhatsApp = noWhatsApp
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MyPacksUiState())

    // ---- Whitelist refresh (screen calls this on every ON_RESUME) ----------

    fun refreshWhitelist() {
        viewModelScope.launch {
            runCatching {
                myPacksRepository.refreshWhitelist { WhitelistCheck.isWhitelisted(appContext, it) }
            }
        }
    }

    // ---- Card pill + ⋯ menu ------------------------------------------------

    fun onPillClicked(id: String) {
        val row = findRow(id) ?: return
        when (row.addState) {
            is AddState.Downloading, AddState.Sent -> Unit
            AddState.Added -> toast(TOAST_ALREADY)
            AddState.Idle, is AddState.Failed -> resend(row)
        }
    }

    fun openMenu(id: String) {
        menuForId.value = id
    }

    fun closeMenu() {
        menuForId.value = null
    }

    /** "Re-add to WhatsApp" / "Add to WhatsApp" from the ⋯ menu. */
    fun onMenuAddToWhatsApp() {
        val row = uiState.value.menuFor ?: return
        menuForId.value = null
        resend(row)
    }

    fun onMenuRemove() {
        val row = uiState.value.menuFor ?: return
        menuForId.value = null
        confirm.value = MyPacksConfirm.RemoveInstalled(row.id, row.name)
    }

    fun onMenuDelete() {
        val row = uiState.value.menuFor ?: return
        menuForId.value = null
        confirm.value = MyPacksConfirm.DeleteOwn(row.id, row.name)
    }

    fun dismissConfirm() {
        confirm.value = null
    }

    fun confirmRemove() {
        val target = confirm.value as? MyPacksConfirm.RemoveInstalled ?: return
        confirm.value = null
        viewModelScope.launch {
            runCatching { catalogRepository.removePack(target.packId) }
            toast(TOAST_REMOVED)
        }
    }

    fun confirmDelete() {
        val target = confirm.value as? MyPacksConfirm.DeleteOwn ?: return
        confirm.value = null
        viewModelScope.launch {
            runCatching { myPacksRepository.deleteOwnPack(target.packId) }
            toast(TOAST_DELETED)
        }
    }

    fun dismissNoWhatsApp() {
        showNoWhatsApp.value = false
    }

    // ---- Hand-off to WhatsApp (files are already local; no download) -------

    private fun resend(row: MyPackRow) {
        if (!AddStickerPackFlow.isWhatsAppInstalled(appContext)) {
            showNoWhatsApp.value = true
            return
        }
        sessions.update { it + (row.id to AddState.Sent) }
        pending = PendingAdd(row.id, row.own)
        viewModelScope.launch(ioDispatcher) {
            val intent = AddStickerPackFlow.createBestIntent(appContext, row.id, row.name)
            if (intent != null) {
                _events.send(MyPacksEvent.LaunchAddIntent(intent))
            } else {
                // Every installed WhatsApp already has the pack.
                pending = null
                persistWhitelisted(row.id, row.own, whitelisted = true)
                sessions.update { it - row.id }
                _events.send(MyPacksEvent.Toast(TOAST_ALREADY, false))
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
                    sessions.update { it - current.id }
                    toast(TOAST_ADDED, withCheck = true)
                }
                is AddStickerPackFlow.AddResult.Cancelled ->
                    // The pack was already installed here; cancelling only means
                    // WhatsApp did not take it this time. Pill falls back to
                    // whitelist truth.
                    sessions.update { it - current.id }
            }
        }
    }

    /** The screen could not launch the intent after all (uninstall race). */
    fun onAddLaunchFailed() {
        pending?.let { p -> sessions.update { it - p.id } }
        pending = null
        showNoWhatsApp.value = true
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

    private fun findRow(id: String): MyPackRow? =
        uiState.value.let { state -> (state.installed + state.own).firstOrNull { it.id == id } }

    private fun toast(message: String, withCheck: Boolean = false) {
        _events.trySend(MyPacksEvent.Toast(message, withCheck))
    }
}
