package com.piptechnologies.stickermaker.feature.namepack

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.LocaleList
import android.os.SystemClock
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piptechnologies.stickermaker.R
import com.piptechnologies.stickermaker.core.data.prefs.PrefsRepository
import com.piptechnologies.stickermaker.core.data.repo.MyPacksRepository
import com.piptechnologies.stickermaker.core.design.components.AddVisualState
import com.piptechnologies.stickermaker.core.telemetry.AppAnalytics
import com.piptechnologies.stickermaker.core.telemetry.CrashReporting
import com.piptechnologies.stickermaker.core.telemetry.redacted
import com.piptechnologies.stickermaker.core.ui.PendingToasts
import com.piptechnologies.stickermaker.core.ui.UiText
import com.piptechnologies.stickermaker.core.ui.inAppLanguage
import com.piptechnologies.stickermaker.feature.language.AppLanguages
import com.piptechnologies.stickermaker.feature.language.effectiveTag
import com.piptechnologies.stickermaker.feature.namepack.engine.Character
import com.piptechnologies.stickermaker.feature.namepack.engine.LetteredSticker
import com.piptechnologies.stickermaker.feature.namepack.engine.NamePackAssets
import com.piptechnologies.stickermaker.feature.namepack.engine.NamePackBuilder
import com.piptechnologies.stickermaker.feature.namepack.engine.NamePackRequest
import com.piptechnologies.stickermaker.feature.namepack.engine.NamePackSaver
import com.piptechnologies.stickermaker.feature.namepack.engine.Relation
import com.piptechnologies.stickermaker.feature.namepack.engine.Slot
import com.piptechnologies.stickermaker.feature.namepack.engine.Tone
import com.piptechnologies.stickermaker.feature.namepack.engine.TrayImage
import com.piptechnologies.stickermaker.feature.rating.RatingPromptController
import com.piptechnologies.stickermaker.whatsapp.AddStickerPackFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** One lettered sticker as the reveal grid shows it. */
data class RevealTile(val slot: Slot, val image: ImageBitmap, val text: String)

data class NamePackUiState(
    val flow: NamePackState = NamePackState(),
    val preview: ImageBitmap? = null,
    val previewText: String = "",
    val progress: Float = 0f,
    val tiles: List<RevealTile> = emptyList(),
    val tray: ImageBitmap? = null,
    val packYou: String = "",
    val packLove: String = "",
    val packName: String = "",
    val addState: AddVisualState = AddVisualState.Idle,
    val relettering: Boolean = false,
    val tileArt: Map<Character, ImageBitmap> = emptyMap(),
    val waitArt: ImageBitmap? = null,
    /** Whose waiting art [waitArt] is: Mango's stands in until the other characters' loops land. */
    val waitArtCharacter: Character = Character.MANGO,
)

sealed interface NamePackEvent {
    /** WhatsApp's add sheet for the saved pack: resolved here, launched by the screen as it arrives. */
    data class LaunchAdd(val intent: Intent) : NamePackEvent
    data object ShowNoWhatsApp : NamePackEvent
    data class Toast(val message: UiText) : NamePackEvent
    /** The flow is over: go Home. */
    data object Finished : NamePackEvent
    /** Back from the first step: return to the intro. */
    data object Leave : NamePackEvent
}

/**
 * Drives the Custom Stickers flow (spec: docs/specs/2026-09-29-custom-stickers-design.md):
 * the pure [NamePackReducer] decides, this class renders previews, builds and re-letters the
 * pack, saves it, and ends first run. Flow state survives a language switch in the
 * SavedStateHandle; rendered bitmaps are rebuilt.
 */
@HiltViewModel
class NamePackViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val savedState: SavedStateHandle,
    private val prefs: PrefsRepository,
    myPacks: MyPacksRepository,
    private val pendingToasts: PendingToasts,
) : ViewModel() {

    private data class Lang(val tag: String, val rtl: Boolean)

    /** A lettered pack and its name ("{you} ❤ {love}" or "For {love}"), fixed in the pack's own language. */
    private class Built(val request: NamePackRequest, val stickers: List<LetteredSticker>, val tray: TrayImage, val name: String)

    private val assets = NamePackAssets(appContext)
    private val builder = NamePackBuilder(assets)
    private val saver = NamePackSaver(
        appContext.filesDir,
        myPacks,
        appContext.getString(R.string.config_support_email),
        appContext.getString(R.string.config_privacy_policy_url)
    )

    private val ui = MutableStateFlow(NamePackUiState(flow = namePackStateFrom { savedState.get<String>(it) }))
    val uiState: StateFlow<NamePackUiState> = ui.asStateFlow()

    private val _events = Channel<NamePackEvent>(Channel.BUFFERED)
    val events: Flow<NamePackEvent> = _events.receiveAsFlow()

    private val lang = MutableStateFlow(langOf(AppLanguages.effectiveTag()))
    private var buildJob: Job? = null
    private var built: Built? = null
    private var savedContent: String? = null
    private val saveLock = Mutex()
    /** The Add or "Not now" save in flight; taps on either wait it out. */
    private var saveJob: Job? = null
    private var finishing = false

    init {
        viewModelScope.launch {
            ui.map { it.flow }.distinctUntilChanged().collect { state ->
                state.toSaved().forEach { (key, value) -> savedState[key] = value }
            }
        }
        viewModelScope.launch {
            val art = withContext(Dispatchers.Default) {
                attempt("name_pack_assets") { Character.entries.associateWith { assets.tileArt(it).asImageBitmap() } }
            } ?: return@launch
            ui.update { it.copy(tileArt = art) }
        }
        viewModelScope.launch {
            combine(ui.map { it.flow }.distinctUntilChanged(), lang) { state, l -> previewOf(state, l) }
                .distinctUntilChanged()
                .collectLatest { job -> if (job != null) renderPreview(job) }
        }
        if (ui.value.flow.step == NameStep.BUILDING) startBuild()
    }

    // ------------------------------------------------------------ events //

    /** The screen reports the app language after every (re)composition start; a change re-letters. */
    fun onLanguage(tag: String) {
        val next = langOf(tag)
        if (lang.value == next) return
        lang.value = next
        when (ui.value.flow.step) {
            NameStep.BUILDING -> startBuild()
            NameStep.REVEAL -> reletter()
            else -> Unit
        }
    }

    fun onYouChange(raw: String) = updateFlow { NamePackReducer.typeYou(it, raw) }

    fun onLoveChange(raw: String) = updateFlow { NamePackReducer.typeLove(it, raw) }

    fun onRelation(relation: Relation) = updateFlow { NamePackReducer.pickRelation(it, relation) }

    fun onCharacter(character: Character) {
        updateFlow { NamePackReducer.pickCharacter(it, character) }
        if (ui.value.flow.step == NameStep.REVEAL) reletter()
    }

    fun onTone(tone: Tone) {
        val before = ui.value.flow.effectiveTone
        updateFlow { NamePackReducer.pickTone(it, tone) }
        if (ui.value.flow.step == NameStep.REVEAL && ui.value.flow.effectiveTone != before) reletter()
    }

    fun onContinue() = updateFlow(NamePackReducer::continueFromYou)

    fun onSkipYou() = updateFlow(NamePackReducer::skipYou)

    fun onMake() {
        val (next, start) = NamePackReducer.make(ui.value.flow)
        updateFlow { next }
        if (start) startBuild()
    }

    /** Skip on their-name: Home with no pack. */
    fun onSkipLove() {
        if (ui.value.flow.step != NameStep.LOVE) return
        buildJob?.cancel()
        finish(outcome = "skipped", toast = null)
    }

    fun onBack() {
        val state = ui.value.flow
        // Building cancels its build; the reveal cancels a re-letter so it cannot land on their-name.
        if (state.step == NameStep.BUILDING || state.step == NameStep.REVEAL) buildJob?.cancel()
        val next = NamePackReducer.back(state)
        if (next == null) {
            viewModelScope.launch { _events.send(NamePackEvent.Leave) }
        } else {
            updateFlow { next }
        }
    }

    fun onAdd() {
        // Once the flow is finishing, WhatsApp's answer would arrive after the screen is gone.
        if (finishing || ui.value.flow.step != NameStep.REVEAL || saveJob?.isActive == true) return
        if (ui.value.addState == AddVisualState.Sent || ui.value.addState == AddVisualState.Added) return
        if (!AddStickerPackFlow.isWhatsAppInstalled(appContext)) {
            // The pack still lands in My Packs, then the install sheet explains (design, Reveal). The
            // sheet promises nothing about saving, and Add or "Not now" retry a save that failed here.
            saveJob = viewModelScope.launch {
                val current = settled() ?: return@launch
                attempt("name_pack_save") { save(current) }
                _events.send(NamePackEvent.ShowNoWhatsApp)
            }
            return
        }
        ui.update { it.copy(addState = AddVisualState.Sent) }
        saveJob = viewModelScope.launch {
            val current = settled()
            if (current == null) {
                ui.update { it.copy(addState = AddVisualState.Idle) }
                return@launch
            }
            val name = try {
                save(current)
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                CrashReporting.record(e.redacted(), "name_pack_save")
                ui.update { it.copy(addState = AddVisualState.Failed) }
                catchUpIfStale()
                return@launch
            }
            // The pack is saved: whatever happens below is not a save failure. Resolved here, not on the
            // screen, so the screen launches the event as it arrives and a recreation cannot lose it.
            // Two queries into WhatsApp's processes (they can start it): off the main thread.
            val id = current.request.packId
            val (intent, installed) = withContext(Dispatchers.IO) {
                try {
                    val best = AddStickerPackFlow.createBestIntent(appContext, id, name)
                    best to (best != null || AddStickerPackFlow.isWhatsAppInstalled(appContext))
                } catch (ce: CancellationException) {
                    throw ce
                } catch (e: Exception) {
                    // WhatsApp's provider failed: the plain add intent lets WhatsApp decide (with no
                    // WhatsApp at all, the launch fails on the screen and shows the install sheet).
                    CrashReporting.record(e.redacted(), "name_pack_add_intent")
                    AddStickerPackFlow.createIntentToAddStickerPack(id, name) to true
                }
            }
            when {
                intent != null -> _events.send(NamePackEvent.LaunchAdd(intent))
                // Every installed WhatsApp already has it; the new image version refreshes it.
                installed -> onWhatsAppResult(added = true)
                // WhatsApp went away since Add was tapped: the install sheet, as Home shows it.
                else -> {
                    onWhatsAppResult(added = false)
                    _events.send(NamePackEvent.ShowNoWhatsApp)
                }
            }
        }
    }

    fun onNotNow() {
        if (finishing || ui.value.flow.step != NameStep.REVEAL || saveJob?.isActive == true) return
        saveJob = viewModelScope.launch {
            val current = settled()
            if (current != null && attempt("name_pack_save") { save(current) } == null) {
                // Not saved: say so and stay on the reveal, so "Not now" or Add can try again.
                _events.send(NamePackEvent.Toast(UiText.res(R.string.namepack_add_failed)))
                return@launch
            }
            finish(outcome = if (current != null) "saved" else "skipped", toast = null)
        }
    }

    /** WhatsApp's verdict on [NamePackEvent.LaunchAdd] (a null intent counts as added). */
    fun onWhatsAppResult(added: Boolean, rejected: Boolean = false) {
        // After process death the rendered pack is gone, but the names and language were restored,
        // so the id is too: a confirmed add still counts, arms the rating prompt and ends the flow.
        val id = built?.request?.packId ?: requestFor(ui.value.flow).packId
        if (added) {
            AppAnalytics.logPackAdded(id)
            RatingPromptController.onPackAdded(appContext)
            ui.update { it.copy(addState = AddVisualState.Added) }
            finish(outcome = "added", toast = UiText.res(R.string.toast_added_to_whatsapp))
        } else {
            AppAnalytics.logPackAddCancelled(id, rejected)
            ui.update { it.copy(addState = AddVisualState.Idle) }
            catchUpIfStale()
        }
    }

    // ----------------------------------------------------------- building //

    private fun startBuild() {
        buildJob?.cancel()
        val request = requestFor(ui.value.flow)
        ui.update { it.copy(progress = 0f) }
        buildJob = viewModelScope.launch {
            try {
                ensureWaitArt(request.character)
                val started = SystemClock.uptimeMillis()
                val done = AtomicInteger(0)
                // runCatching keeps a render failure from cancelling this coroutine (and crashing
                // through viewModelScope): the loop sees it and getOrThrow() reaches the catch below.
                val building = async { runCatching { builder.build(request) { n -> done.accumulateAndGet(n) { a, b -> maxOf(a, b) } } } }
                while (true) {
                    val shown = buildProgress(
                        done.get(), Slot.entries.size, building.isCompleted,
                        SystemClock.uptimeMillis() - started, MIN_BUILD_MS
                    )
                    ui.update { it.copy(progress = shown) }
                    if (building.isCompleted && (shown >= 1f || building.await().isFailure)) break
                    delay(FRAME_MS)
                }
                val result = building.await().getOrThrow()
                val tray = withContext(Dispatchers.Default) { builder.tray(request) }
                delay(HOLD_MS)
                applyBuilt(Built(request, result, tray, packName(request)))
                updateFlow(NamePackReducer::built)
                AppAnalytics.logNamePackBuilt(
                    relation = request.relation.id,
                    tone = request.effectiveTone.id,
                    character = request.character.id,
                    hasYourName = request.you.isNotEmpty(),
                    language = request.lang
                )
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                CrashReporting.record(e.redacted(), "name_pack_build")
                _events.send(NamePackEvent.Toast(UiText.res(R.string.namepack_build_failed)))
                updateFlow(NamePackReducer::buildFailed)
            }
        }
    }

    /**
     * Tone, character or language changed on the reveal: re-letter in place (cached ones are
     * instant). Not while an add is in flight; [catchUpIfStale] runs once it settles.
     */
    private fun reletter() {
        if (ui.value.addState == AddVisualState.Sent) return
        buildJob?.cancel()
        val request = requestFor(ui.value.flow)
        ui.update { it.copy(relettering = true) }
        buildJob = viewModelScope.launch {
            try {
                val stickers = builder.build(request)
                val tray = withContext(Dispatchers.Default) { builder.tray(request) }
                applyBuilt(Built(request, stickers, tray, packName(request)))
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                CrashReporting.record(e.redacted(), "name_pack_build")
                // Keep the tone and character controls true to the stickers still on screen.
                val shown = built?.request
                ui.update { state ->
                    state.copy(
                        relettering = false,
                        flow = if (shown == null) state.flow else state.flow.copy(tone = shown.tone, character = shown.character)
                    )
                }
                _events.send(NamePackEvent.Toast(UiText.res(R.string.namepack_build_failed)))
            }
        }
    }

    private fun applyBuilt(result: Built) {
        built = result
        ui.update {
            it.copy(
                tiles = result.stickers.map { s -> RevealTile(s.slot, s.preview.asImageBitmap(), s.text) },
                tray = result.tray.bitmap.asImageBitmap(),
                packYou = result.request.you,
                packLove = result.request.love,
                packName = result.name,
                // An add in flight keeps its state: the re-letter it waited for must not re-enable Add.
                addState = if (it.addState == AddVisualState.Added || it.addState == AddVisualState.Sent) it.addState else AddVisualState.Idle,
                relettering = false
            )
        }
    }

    private suspend fun ensureWaitArt(character: Character) {
        val art = withContext(Dispatchers.Default) { assets.waitArt(character) }
        ui.update { it.copy(waitArt = art.bitmap.asImageBitmap(), waitArtCharacter = art.character) }
    }

    // ------------------------------------------------------------- saving //

    /** Writes the pack once per content (an overlapping call waits, then finds it written); returns its name. */
    private suspend fun save(current: Built): String = saveLock.withLock {
        if (savedContent != current.request.contentKey) {
            saver.save(current.request.packId, current.name, current.stickers, current.tray.png)
            savedContent = current.request.contentKey
        }
        current.name
    }

    /** The pack the screen shows, once re-letters in flight have landed (or failed and restored the controls). */
    private suspend fun settled(): Built? {
        // A tap during the wait can start another re-letter: wait for that one too.
        while (true) {
            val job = buildJob ?: break
            job.join()
            if (job === buildJob) break
        }
        return built
    }

    /** After an add settles: re-letter if the language (or a control) moved while it was in flight. */
    private fun catchUpIfStale() {
        val shown = built?.request ?: return
        if (ui.value.flow.step == NameStep.REVEAL && shown != requestFor(ui.value.flow)) reletter()
    }

    /** The pack's name in its own language, whatever the app shows later. */
    private fun packName(request: NamePackRequest): String =
        if (request.you.isNotEmpty()) "${request.you} ❤ ${request.love}"
        else inLanguage(request.lang).getString(R.string.namepack_pack_for, request.love)

    private fun finish(outcome: String, toast: UiText?) {
        if (finishing) return
        finishing = true
        viewModelScope.launch {
            // A failed write (a full disk) must not crash the way out: Home still opens, and the
            // intro shows again next launch. Completion is logged only once the flag is written.
            attempt("name_pack_finish") {
                if (!prefs.onboarded.first()) {
                    prefs.setOnboarded(true)
                    AppAnalytics.logOnboardingComplete(outcome)
                }
            }
            toast?.let(pendingToasts::post)
            _events.send(NamePackEvent.Finished)
        }
    }

    // ------------------------------------------------------------ preview //

    /** What a preview letters: [text], or when null [slot]'s phrase for [request] (looked up off the main thread). */
    private data class PreviewJob(val request: NamePackRequest, val slot: Slot, val text: String?)

    /** Your-name letters the name itself on name_only; their-name letters love_you with the sample name while empty. */
    private fun previewOf(state: NamePackState, l: Lang): PreviewJob? {
        val base = NamePackRequest(l.tag, l.rtl, state.you.value, state.love.value, state.relation, Tone.SWEET, state.character)
        return when (state.step) {
            NameStep.YOU -> PreviewJob(
                base, Slot.NAME_ONLY,
                state.you.value.ifEmpty { words().getString(R.string.namepack_you_placeholder) }
            )
            NameStep.LOVE -> {
                val love = state.love.value.ifEmpty { words().getString(R.string.namepack_sample_name) }
                PreviewJob(base.copy(love = love), Slot.LOVE_YOU, text = null)
            }
            else -> null
        }
    }

    private suspend fun renderPreview(job: PreviewJob) {
        val (bitmap, text) = withContext(Dispatchers.Default) {
            attempt("name_pack_preview") {
                // The phrase book parses phrases.json on first use: not on the main thread.
                val text = job.text ?: builder.text(job.request, job.slot)
                builder.preview(job.request, job.slot, text) to text
            }
        } ?: return
        ui.update { it.copy(preview = bitmap.asImageBitmap(), previewText = text) }
    }

    // ------------------------------------------------------------ helpers //

    private fun updateFlow(transform: (NamePackState) -> NamePackState) = ui.update { it.copy(flow = transform(it.flow)) }

    /** Runs [block]; a failure is recorded under [where] and gives null. Cancellation passes through. */
    private suspend fun <T> attempt(where: String, block: suspend () -> T): T? =
        try {
            block()
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            CrashReporting.record(e.redacted(), where)
            null
        }

    private fun requestFor(state: NamePackState): NamePackRequest {
        val l = lang.value
        return NamePackRequest(l.tag, l.rtl, state.you.value, state.love.value, state.relation, state.tone, state.character)
    }

    private fun langOf(tag: String) = Lang(tag, AppLanguages.byTag(tag)?.rtl == true)

    /** Strings in the app language, also below API 33 where the application context is not localized. */
    private fun words(): Context = appContext.inAppLanguage()

    /** Resources in [tag] (an AppLanguages tag), whatever the app language is now. */
    private fun inLanguage(tag: String): Context {
        val config = Configuration(appContext.resources.configuration)
        config.setLocales(LocaleList.forLanguageTags(tag))
        return appContext.createConfigurationContext(config)
    }

    private companion object {
        const val MIN_BUILD_MS = 1_700L
        const val HOLD_MS = 420L
        const val FRAME_MS = 16L
    }
}
