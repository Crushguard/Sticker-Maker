package com.piptechnologies.stickermaker.feature.create

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import android.net.Uri
import android.provider.Settings
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.Segmenter
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import com.piptechnologies.stickermaker.R
import com.piptechnologies.stickermaker.core.data.di.IoDispatcher
import com.piptechnologies.stickermaker.core.data.files.OwnPackFiles
import com.piptechnologies.stickermaker.core.data.prefs.PrefsRepository
import com.piptechnologies.stickermaker.core.data.repo.MyPacksRepository
import com.piptechnologies.stickermaker.core.design.components.AddVisualState
import com.piptechnologies.stickermaker.core.telemetry.AppAnalytics
import com.piptechnologies.stickermaker.core.telemetry.CrashReporting
import com.piptechnologies.stickermaker.core.ui.UiText
import com.piptechnologies.stickermaker.core.ui.inAppLanguage
import com.piptechnologies.stickermaker.feature.create.decor.DecorData
import com.piptechnologies.stickermaker.feature.create.decor.DecorEditor
import com.piptechnologies.stickermaker.feature.create.decor.DecorFonts
import com.piptechnologies.stickermaker.feature.create.decor.DecorSpec
import com.piptechnologies.stickermaker.feature.create.decor.DecorState
import com.piptechnologies.stickermaker.feature.create.decor.EmojiCatalog
import com.piptechnologies.stickermaker.feature.create.decor.FontMood
import com.piptechnologies.stickermaker.feature.create.decor.LayerContent
import com.piptechnologies.stickermaker.feature.create.decor.MarkerSize
import com.piptechnologies.stickermaker.feature.create.decor.OutlineThickness
import com.piptechnologies.stickermaker.feature.create.decor.SceneRenderer
import com.piptechnologies.stickermaker.feature.create.decor.Size2
import com.piptechnologies.stickermaker.feature.create.decor.SkinTone
import com.piptechnologies.stickermaker.feature.create.decor.TextLayerPainter
import com.piptechnologies.stickermaker.feature.create.decor.TextStyleId
import com.piptechnologies.stickermaker.feature.create.decor.UndoResult
import com.piptechnologies.stickermaker.feature.language.AppLanguages
import com.piptechnologies.stickermaker.feature.language.effectiveTag
import com.piptechnologies.stickermaker.feature.rating.RatingPromptController
import com.piptechnologies.stickermaker.whatsapp.AddStickerPackFlow
import com.piptechnologies.stickermaker.whatsapp.AnimatedWebpMuxer
import com.piptechnologies.stickermaker.whatsapp.StickerContentProvider
import com.piptechnologies.stickermaker.whatsapp.StickerPackValidator
import com.piptechnologies.stickermaker.whatsapp.ValidatablePack
import com.piptechnologies.stickermaker.whatsapp.ValidatableSticker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/** The design assigns no per-sticker emoji, so every sticker gets this default pair. */
private val DEFAULT_EMOJIS = listOf("❤️", "😊")

/** A burst of decor edits settles this long before the rail thumbnail is rendered again. */
private const val THUMB_DEBOUNCE_MS = 150L

/** Coil's model for a bundled emoji (the Default skin tone). */
private const val EMOJI_ASSET_URL = "file:///android_asset/emoji/"

/**
 * The one Create session shared by Import, Cut out and Pack details, exactly
 * like the prototype's single `create` state object: picked media, per-sticker
 * masks and strokes, per-sticker decor (a [DecorEditor] each: layers, outline,
 * preset and the undo stack), active tool, pack name, tray choice and the
 * export → add-to-WhatsApp state machine.
 *
 * All bitmap and segmentation work runs off the main thread; session fields
 * are main-confined and published through [state]. Every public function is
 * called on the main thread; canvas coordinates are 512 canvas px.
 */
@HiltViewModel
class CreatePackViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val myPacksRepository: MyPacksRepository,
    private val prefs: PrefsRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val _state = MutableStateFlow(CreateUiState())
    val state: StateFlow<CreateUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<CreateEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<CreateEvent> = _events.asSharedFlow()

    // ------------------------------------------------------- internal model

    private inner class MediaItem(
        val id: String,
        val uri: Uri?,
        val cameraFile: File?,
        val isVideo: Boolean,
        val durationMs: Long
    ) {
        var selected: Boolean = true
        var cut: CutStatus = CutStatus.None
        var source: Bitmap? = null
        var autoMask: Bitmap? = null
        var mask: Bitmap? = null
        /** Bumped on every change to [mask], so the bitmaps made from it know when they are stale. */
        var maskVersion: Int = 0
        /** Bumped when [mask] is replaced; an undo rebuild that lost the race is dropped. */
        var rebuildSeq: Int = 0
        /** [source] masked by [mask]: the scene's subject. A Brush or Erase stroke patches it in place. */
        var subject: Bitmap? = null
        var subjectVersion: Int = -1
        /** [mask] dilated by the outline radius, in white: the subject's share of the die-cut outline. */
        var silhouette: Bitmap? = null
        var silhouetteVersion: Int = -1
        var silhouetteThickness: OutlineThickness? = null
        val strokes = mutableListOf<MaskStroke>()
        var frameThumb: ImageBitmap? = null
        var stickerThumb: ImageBitmap? = null

        /** Layers, outline, preset and the one undo stack of this sticker (spec §3-§4). */
        val editor = DecorEditor(
            sizeOf = ::baseSizeOf,
            subjectBox = { mask?.let(StickerRenderer::maskBounds) },
            newId = { ++layerSeq }
        )

        val durationLabel: String?
            get() = if (isVideo) CreateMedia.formatDurationLabel(durationMs) else null

        fun toUi() = CreateItemUi(
            id = id,
            isVideo = isVideo,
            selected = selected,
            durationLabel = durationLabel,
            // Coil has no video decoder wired, so clips render through frameThumb instead.
            pickerModel = if (isVideo) null else (cameraFile ?: uri),
            frameThumb = frameThumb,
            stickerThumb = stickerThumb,
            cut = cut,
            animated = editor.state.animated
        )

        fun recycleBitmaps() {
            source?.recycle()
            autoMask?.recycle()
            mask?.recycle()
            source = null
            autoMask = null
            mask = null
            // A render still in flight may hold these; the garbage collector frees them.
            subject = null
            silhouette = null
        }
    }

    // Main-confined session fields.
    private val items = mutableListOf<MediaItem>()
    private var idSeq = 0
    private var layerSeq = 0L
    private var shots = 0
    private var source = ImportSource.Photos
    private var activeIndex = 0
    private var tool = EditorTool.Auto
    private var zoomed = false
    private var brush = 2
    private var addTab = AddTab.Text
    private var emojiTab = EmojiCatalog.CATEGORIES.first()
    private var recents: List<String> = emptyList()
    private var skinPopover: SkinPopoverUi? = null
    private var skinSeq = 0
    private var skinToastSeq = -1
    private var drawColour = DecorSpec.ROSE
    private var drawSize = MarkerSize.M
    private var playing = false
    private var liveLayerId: Long? = null
    private var gestureStart: DecorState? = null
    /** The layer-limit toast already showed in this text session (typing keeps being refused). */
    private var limitToastShown = false
    private var trayIndex = 0
    private var packName = ""
    private var tick = 0
    private var exportState: AddVisualState = AddVisualState.Idle
    private var exportProgress = 0f
    private var savedPackId: String? = null
    private var savedPackName: String = ""
    private var finished = false

    private var segmenterOrNull: Segmenter? = null
    private var cutoutJob: Job? = null
    private var exportJob: Job? = null
    private var activeStroke: MaskStroke? = null
    private val derivedJobs = mutableMapOf<String, Job>()

    // ---------------------------------------------------------------- decor

    private var decor: CreateDecor? = null

    /** Whether the app language reads right to left; text layers read it as they render, on any thread. */
    @Volatile
    private var rtlLanguage = isRtl(AppLanguages.effectiveTag())

    /** The decor engine, loaded once on IO; null when loading failed. */
    private val decorLoad: Deferred<CreateDecor?> = viewModelScope.async {
        val loaded = withContext(ioDispatcher) {
            try {
                CreateDecor.load(appContext) { rtlLanguage }
            } catch (e: Exception) {
                CrashReporting.record(e, "create_decor")
                null
            }
        }
        decor = loaded
        tick++
        push()
        loaded
    }

    /** The editor's catalogs: emoji, decoration pieces, text styles, phrases and motion presets. Null until loaded. */
    val data: DecorData? get() = decor?.data

    /** Draws a sticker's scene, on the live canvas and in the export. Null until [data] is loaded. */
    val renderer: SceneRenderer? get() = decor?.renderer

    /** Letters text layers (the Add sheet's "Aa" style samples too); thread-safe. Null until [data] is loaded. */
    val textPainter: TextLayerPainter? get() = decor?.painter

    /** The lettering fonts (the Add sheet's font chips show their own face). Null until [data] is loaded. */
    val fonts: DecorFonts? get() = decor?.fonts

    init {
        viewModelScope.launch {
            prefs.emojiRecents.collect {
                recents = it
                push()
            }
        }
    }

    // ---------------------------------------------------------------- state

    private fun selectedItems(): List<MediaItem> = items.filter { it.selected }

    private fun activeItem(): MediaItem? = selectedItems().getOrNull(activeIndex)

    /** The active sticker once its cut-out is done: the canvas edits nothing before that. */
    private fun editableItem(): MediaItem? = activeItem()?.takeIf { it.cut == CutStatus.Done }

    private fun push() {
        val selected = selectedItems()
        val active = selected.getOrNull(activeIndex)
        _state.value = CreateUiState(
            source = source,
            items = items.map { it.toUi() },
            selectedCount = selected.size,
            shots = shots,
            activeIndex = activeIndex,
            tool = tool,
            zoomed = zoomed,
            brush = brush,
            activeCut = active?.cut ?: CutStatus.None,
            activeDurationLabel = active?.durationLabel,
            activeIsVideo = active?.isVideo ?: false,
            anyPending = selected.any { it.cut == CutStatus.Pending },
            editorTick = tick,
            dataReady = decor != null,
            layerToolsEnabled = layerToolsEnabled(active?.cut),
            liveLayerId = liveLayerId,
            playing = playing,
            addTab = addTab,
            emojiTab = emojiTab,
            recents = recents,
            skinPopover = skinPopover,
            drawColour = drawColour,
            drawSize = drawSize,
            trayIndex = trayIndex.coerceIn(0, max(0, selected.size - 1)),
            packName = packName,
            animatedPack = selected.any { it.isVideo || it.editor.state.animated },
            exportState = exportState,
            exportProgress = exportProgress
        ).withDecor(active?.editor)
    }

    /** A layer content's size at scale 1. Layers are only added once the decor is loaded. */
    private fun baseSizeOf(content: LayerContent): Size2 = decor?.baseSize(content) ?: Size2(1f, 1f)

    private fun toast(message: UiText, check: Boolean = false) {
        _events.tryEmit(CreateEvent.ShowToast(message, check))
    }

    private fun toastLayerLimit() = toast(UiText.res(R.string.create_toast_layer_limit, DecorSpec.MAX_LAYERS))

    // --------------------------------------------------------------- import

    fun selectSource(newSource: ImportSource) {
        source = newSource
        push()
    }

    fun addPickedImages(uris: List<Uri>) = addPicked(uris, isVideo = false)

    fun addPickedVideos(uris: List<Uri>) = addPicked(uris, isVideo = true)

    private fun addPicked(uris: List<Uri>, isVideo: Boolean) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            var clamped = false
            val fresh = mutableListOf<MediaItem>()
            for (uri in uris) {
                if (items.any { it.uri == uri }) continue
                if (selectedItems().size + fresh.size >= CreateSpec.MAX_STICKERS) {
                    clamped = true
                    break
                }
                val duration = if (isVideo) {
                    withContext(ioDispatcher) { CreateMedia.videoDurationMs(appContext, uri) } ?: 0L
                } else {
                    0L
                }
                fresh += MediaItem(nextId(), uri, cameraFile = null, isVideo = isVideo, durationMs = duration)
            }
            items += fresh
            push()
            if (clamped) toast(UiText.res(R.string.create_toast_max, CreateSpec.MAX_STICKERS))
            fresh.filter { it.isVideo }.forEach { item ->
                launch {
                    val uri = item.uri ?: return@launch
                    val thumb = withContext(ioDispatcher) { CreateMedia.videoThumb(appContext, uri) }
                    if (thumb != null) {
                        item.frameThumb = thumb.asImageBitmap()
                        push()
                    }
                }
            }
        }
    }

    /** A shot from the system camera; landed as a preview-resolution bitmap. */
    fun addCameraShot(bitmap: Bitmap) {
        viewModelScope.launch {
            if (selectedItems().size >= CreateSpec.MAX_STICKERS) {
                bitmap.recycle()
                toast(UiText.res(R.string.create_toast_max, CreateSpec.MAX_STICKERS))
                return@launch
            }
            val square = withContext(Dispatchers.Default) {
                CreateMedia.centerCropSquare(bitmap, CreateSpec.CANVAS_SIZE)
            }
            if (square !== bitmap) bitmap.recycle()
            val file = withContext(ioDispatcher) {
                try {
                    val dir = File(appContext.cacheDir, "create").apply { mkdirs() }
                    val out = File(dir, "shot-${System.currentTimeMillis()}-$idSeq.png")
                    FileOutputStream(out).use { square.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    out
                } catch (e: Exception) {
                    null
                }
            }
            val item = MediaItem(nextId(), uri = null, cameraFile = file, isVideo = false, durationMs = 0L)
            item.source = square
            items += item
            shots += 1
            push()
            toast(UiText.res(R.string.create_toast_shot_added))
        }
    }

    fun togglePicked(id: String) {
        val item = items.find { it.id == id } ?: return
        if (!item.selected && selectedItems().size >= CreateSpec.MAX_STICKERS) {
            toast(UiText.res(R.string.create_toast_max, CreateSpec.MAX_STICKERS))
            return
        }
        item.selected = !item.selected
        activeIndex = activeIndex.coerceIn(0, max(0, selectedItems().size - 1))
        trayIndex = trayIndex.coerceIn(0, max(0, selectedItems().size - 1))
        keepLayerToolsOnDoneCut()
        push()
    }

    /**
     * Next on Import: queue the on-device cut-out for every selected sticker,
     * sequentially, so the editor opens with the per-sticker spinners running.
     */
    fun beginCutouts() {
        refreshLanguage()
        val targets = selectedItems().filter { it.cut == CutStatus.None }
        targets.forEach { it.cut = CutStatus.Pending }
        // The editor may reopen on a sticker still being cut, with a layer tool left on.
        keepLayerToolsOnDoneCut()
        push()
        if (targets.isEmpty()) return
        val previous = cutoutJob
        cutoutJob = viewModelScope.launch {
            previous?.join()
            for (item in targets) {
                runCutout(item, resetStrokes = false)
            }
        }
    }

    /** Import screen re-entry after a finished export starts a fresh pack. */
    fun startFreshSessionIfFinished() {
        if (finished) resetSession()
    }

    // ------------------------------------------------------- editor: basics

    /**
     * The rail: switches to sticker [index]. What the old one had open is settled and nothing stays
     * selected. On a sticker still being cut out, Add, Draw and Animate give way to Auto (no re-run).
     */
    fun selectSticker(index: Int) {
        activeItem()?.let { leaving ->
            val before = stateBefore(leaving)
            settle()
            endTextSession(leaving)
            leaving.editor.select(null)
            if (leaving.editor.state != before) refreshDerived(leaving, THUMB_DEBOUNCE_MS)
        }
        activeIndex = index.coerceIn(0, max(0, selectedItems().size - 1))
        activeItem()?.editor?.select(null)
        keepLayerToolsOnDoneCut()
        playing = false
        tick++
        push()
    }

    /**
     * A tool bar tap (spec §8). Leaving Draw turns its strokes into a layer; Add while the sheet is
     * open closes it; Auto, Brush, Erase and Draw deselect; Auto re-runs the cut-out, which resets the
     * Brush and Erase strokes (layers, outline and preset stay). Add, Draw and Animate are refused
     * while the active sticker's cut-out isn't done ([CreateUiState.layerToolsEnabled]).
     */
    fun selectTool(newTool: EditorTool) {
        if (newTool == EditorTool.Add && tool == EditorTool.Add) {
            closeAddSheet()
            return
        }
        val item = activeItem()
        if (!toolAllowed(newTool, item?.cut)) return
        val before = item?.let(::stateBefore)
        applyTool(newTool)
        if (newTool != EditorTool.Add && newTool != EditorTool.Animate) item?.editor?.select(null)
        afterEdit(item, before)
        if (newTool == EditorTool.Auto && item != null && item.cut != CutStatus.Pending) {
            // Auto re-runs the cut-out on the current sticker and resets its strokes.
            item.cut = CutStatus.Pending
            push()
            viewModelScope.launch { runCutout(item, resetStrokes = true) }
        }
    }

    /**
     * Switches the tool after settling what the old one left open (the UI drops a gesture in
     * flight when the tool changes): leaving Draw turns its strokes into a layer, leaving Add ends
     * the text session and closes the skin popover. Doesn't publish.
     */
    private fun applyTool(newTool: EditorTool) {
        finishMaskStroke()
        endOpenGesture()
        activeItem()?.let { item ->
            if (tool == EditorTool.Draw && newTool != EditorTool.Draw) flushDrawing(item)
            if (tool == EditorTool.Add && newTool != EditorTool.Add) endTextSession(item)
        }
        if (newTool != EditorTool.Add) skinPopover = null
        tool = newTool
    }

    /**
     * Keeps Add, Draw and Animate on a finished cut-out: when the active sticker has none (a rail switch,
     * Import changes), the tool falls back to Auto without re-running the cut-out. Doesn't publish.
     */
    private fun keepLayerToolsOnDoneCut() {
        if (!toolAllowed(tool, activeItem()?.cut)) applyTool(EditorTool.Auto)
    }

    fun toggleZoom() {
        zoomed = !zoomed
        push()
    }

    fun setBrush(size: Int) {
        brush = size.coerceIn(1, 3)
        push()
    }

    /** Before Next: live Draw strokes become a layer, a stroke or gesture left open ends, the text session closes. */
    fun flushEdits() {
        val item = activeItem() ?: return
        val before = stateBefore(item)
        settle()
        endTextSession(item)
        afterEdit(item, before)
    }

    /**
     * Ends what a finger or a tool left open on the active sticker: its Brush or Erase stroke is
     * recorded, a layer gesture ends, and live Draw strokes become one Drawing layer.
     */
    private fun settle() {
        finishMaskStroke()
        endOpenGesture()
        activeItem()?.let(::flushDrawing)
    }

    /**
     * Turns [item]'s live Draw strokes into one Drawing layer (spec §4). Every add flushes first:
     * the flush itself doesn't check the 8-layer limit, the add after it does.
     */
    private fun flushDrawing(item: MediaItem) {
        item.editor.flushLiveStrokes()
    }

    /** Ends [item]'s text session (an emptied text layer goes); the next refused keystroke may toast again. */
    private fun endTextSession(item: MediaItem) {
        item.editor.endTextSession()
        limitToastShown = false
    }

    /**
     * The active [item]'s decor before an edit that may end its open gesture: the gesture's start, so
     * the change the gesture made still counts toward the thumbnail.
     */
    private fun stateBefore(item: MediaItem): DecorState = gestureStart ?: item.editor.state

    /** Brush, Erase and Draw take the canvas for strokes: layers ignore taps and gestures then (spec §6, §8). */
    private fun strokeTool(): Boolean = tool == EditorTool.Brush || tool == EditorTool.Erase || tool == EditorTool.Draw

    /** Publishes an edit of [item]: the canvas redraws and, when its decor changed since [before], its thumbnail. */
    private fun afterEdit(item: MediaItem?, before: DecorState?) {
        tick++
        push()
        if (item != null && item.editor.state != before) refreshDerived(item, THUMB_DEBOUNCE_MS)
    }

    /**
     * Runs [edit] on [item]'s editor, then publishes it: layer tools pass [editableItem] (nothing
     * happens before the cut-out is done), the outline setters the active sticker.
     */
    private inline fun editDecor(item: MediaItem?, edit: (DecorEditor) -> Unit) {
        if (item == null) return
        val before = item.editor.state
        edit(item.editor)
        afterEdit(item, before)
    }

    // ------------------------------------------------------ editor: canvas

    /**
     * A tap on the canvas (canvas px). Nothing in Brush, Erase and Draw; in Animate with reduced motion
     * it plays one loop; otherwise it selects the top-most layer there, or deselects on empty space.
     */
    fun canvasTap(x: Float, y: Float) {
        val item = editableItem() ?: return
        if (strokeTool()) return
        if (tool == EditorTool.Animate && reduceMotion()) {
            togglePlaying()
            return
        }
        val before = item.editor.state
        item.editor.select(item.editor.hitTest(x, y))
        endTextSession(item)
        afterEdit(item, before)
    }

    /** A double tap on a text layer edits it. */
    fun canvasDoubleTap(x: Float, y: Float) {
        val item = editableItem() ?: return
        if (strokeTool()) return
        val id = item.editor.hitTest(x, y) ?: return
        if (item.editor.state.layer(id)?.content is LayerContent.Text) editTextLayer(id)
    }

    /**
     * A finger lands on the canvas (canvas px): true when it is on a layer, which the gesture then
     * moves, resizes and turns (one undo step). False on empty space: the UI pans the view instead.
     */
    fun beginLayerGesture(x: Float, y: Float): Boolean {
        val item = editableItem() ?: return false
        if (strokeTool()) return false
        val id = item.editor.hitTest(x, y) ?: return false
        startGesture(item, id)
        return true
    }

    /** Moves the gesture's layer by a canvas-px delta (centre snapping included). */
    fun layerDrag(dx: Float, dy: Float) {
        val item = activeItem() ?: return
        val id = liveLayerId ?: return
        item.editor.drag(id, dx, dy)
        tick++
        push()
    }

    /** Two-finger pinch and twist on the gesture's layer: [zoom] multiplies its scale, [rotationDeg] adds up. */
    fun layerPinch(zoom: Float, rotationDeg: Float) {
        val item = activeItem() ?: return
        val id = liveLayerId ?: return
        item.editor.pinch(id, zoom, rotationDeg)
        tick++
        push()
    }

    /** Ends a layer or handle gesture; the canvas then draws the layer at its exact size. */
    fun endLayerGesture() {
        val item = activeItem() ?: return
        if (liveLayerId == null) return
        val before = gestureStart
        endOpenGesture()
        afterEdit(item, before)
    }

    /** The resize-and-rotate handle of layer [id] is grabbed. */
    fun beginHandleGesture(id: Long) {
        val item = editableItem() ?: return
        if (item.editor.state.layer(id) == null) return
        startGesture(item, id)
    }

    /** The handle drag: the layer's absolute [scale] and [rotation] (degrees). */
    fun handleGesture(scale: Float, rotation: Float) {
        val item = activeItem() ?: return
        val id = liveLayerId ?: return
        item.editor.setScaleRotation(id, scale, rotation)
        tick++
        push()
    }

    private fun startGesture(item: MediaItem, id: Long) {
        endOpenGesture()
        gestureStart = item.editor.state
        item.editor.beginGesture(id)
        liveLayerId = id
        tick++
        push()
    }

    /** Ends the active sticker's layer gesture, if one is open. */
    private fun endOpenGesture() {
        if (liveLayerId == null) return
        activeItem()?.editor?.endGesture()
        liveLayerId = null
        gestureStart = null
    }

    // ------------------------------------------------------ editor: layers

    fun deleteLayer(id: Long) = editDecor(editableItem()) { it.delete(id) }

    /** A copy of the selected layer, offset and selected; the 9th layer is refused with a toast. */
    fun duplicateSelected() {
        val item = editableItem() ?: return
        val id = item.editor.selectedId ?: return
        val before = item.editor.state
        flushDrawing(item)
        if (item.editor.state.layer(id) != null && item.editor.duplicate(id) == null) toastLayerLimit()
        afterEdit(item, before)
    }

    fun flipSelected() = editDecor(editableItem()) { editor -> editor.selectedId?.let(editor::flip) }

    fun toggleBehindSelected() = editDecor(editableItem()) { editor -> editor.selectedId?.let(editor::toggleBehind) }

    /** The edit handle or a double tap: opens Add › Text on text layer [id]. */
    fun editTextLayer(id: Long) {
        val item = editableItem() ?: return
        if (item.editor.state.layer(id)?.content !is LayerContent.Text) return
        val before = stateBefore(item)
        applyTool(EditorTool.Add)
        addTab = AddTab.Text
        item.editor.editText(id)
        limitToastShown = false
        afterEdit(item, before)
    }

    // --------------------------------------------------------- add sheet

    fun setAddTab(tab: AddTab) {
        addTab = tab
        skinPopover = null
        push()
    }

    /** The Emoji tab's category chip: a category id or `recent`. */
    fun setEmojiTab(id: String) {
        emojiTab = id
        skinPopover = null
        push()
    }

    /** Closes the Add sheet (Add again, back, the handle): an emptied text layer goes and the tool returns to Auto. */
    fun closeAddSheet() {
        if (tool != EditorTool.Add) return
        val item = activeItem()
        val before = item?.let(::stateBefore)
        applyTool(EditorTool.Auto)
        afterEdit(item, before)
    }

    /**
     * The Text field changed: it edits the selected text layer, or its first character (or a quick
     * phrase) adds a text layer in the pending style, colour and font. The 9th layer is refused, with a
     * toast once per text session (every further keystroke is refused too).
     */
    fun setText(text: String) {
        val item = editableItem() ?: return
        if (decor == null) return
        val editor = item.editor
        val before = editor.state
        val editsLayer = before.layer(editor.selectedId)?.content is LayerContent.Text
        if (!editsLayer && text.isNotEmpty()) flushDrawing(item)
        if (!editor.setText(text) && !limitToastShown) {
            limitToastShown = true
            toastLayerLimit()
        }
        afterEdit(item, before)
    }

    fun setTextStyle(style: TextStyleId) = editDecor(editableItem()) { it.setTextStyle(style) }

    fun setTextColour(colour: Int) = editDecor(editableItem()) { it.setTextColour(colour) }

    fun setTextFont(font: FontMood) = editDecor(editableItem()) { it.setTextFont(font) }

    /**
     * An emoji pick: adds it at the centre in [tone], closes the sheet and records it in Recent. A tone
     * that isn't on disk does nothing (it would draw, and be cached, as the Default art).
     */
    fun addEmoji(file: String, tone: SkinTone = SkinTone.Default) {
        val item = editableItem() ?: return
        val decor = decor ?: return
        val emoji = decor.data.emoji.byFile(file) ?: return
        if (tone != SkinTone.Default && decor.tones.cached(emoji, tone) == null) return
        val before = stateBefore(item)
        flushDrawing(item)
        if (item.editor.add(LayerContent.Emoji(file, emoji.glyph, tone)) != null) {
            viewModelScope.launch {
                try {
                    prefs.pushEmojiRecent(file)
                } catch (e: IOException) {
                    // Recent is a convenience; the pick itself already landed.
                }
            }
        } else {
            toastLayerLimit()
        }
        applyTool(EditorTool.Auto)
        afterEdit(item, before)
    }

    /**
     * A long-press on an emoji with skin tones: the popover shows the bundled Default and the five
     * other tones, each downloading unless it's on disk already. The first failure shows a toast.
     */
    fun openSkinTones(file: String) {
        val decor = decor ?: return
        val emoji = decor.data.emoji.byFile(file)?.takeIf { it.skinTones } ?: return
        val tones = decor.tones
        val seq = ++skinSeq
        val cells = SkinTone.entries.map { tone ->
            when {
                tone == SkinTone.Default -> ToneCellUi(tone, ToneState.Ready, EMOJI_ASSET_URL + file)
                else -> tones.cached(emoji, tone)?.let { ToneCellUi(tone, ToneState.Ready, it) }
                    ?: ToneCellUi(tone, ToneState.Loading, null)
            }
        }
        skinPopover = SkinPopoverUi(file, cells)
        push()
        cells.filter { it.state == ToneState.Loading }.forEach { cell ->
            viewModelScope.launch {
                val downloaded = withContext(ioDispatcher) { tones.ensure(emoji, cell.tone) }
                toneLoaded(seq, cell.tone, downloaded)
            }
        }
    }

    /** A download for popover [seq] ended; a later popover (or none) ignores it. */
    private fun toneLoaded(seq: Int, tone: SkinTone, downloaded: File?) {
        val popover = skinPopover?.takeIf { seq == skinSeq } ?: return
        val cell = if (downloaded != null) {
            ToneCellUi(tone, ToneState.Ready, downloaded)
        } else {
            ToneCellUi(tone, ToneState.Failed, null)
        }
        skinPopover = popover.copy(cells = popover.cells.map { if (it.tone == tone) cell else it })
        if (downloaded == null && skinToastSeq != seq) {
            skinToastSeq = seq
            toast(UiText.res(R.string.create_toast_skin_failed))
        }
        push()
    }

    fun closeSkinTones() {
        skinPopover = null
        push()
    }

    /** A decoration piece: added at its anchor at its default width, then the sheet closes. */
    fun addDecor(file: String) {
        val item = editableItem() ?: return
        val piece = data?.decor?.byFile(file) ?: return
        val before = stateBefore(item)
        flushDrawing(item)
        if (item.editor.add(LayerContent.Decor(file, piece.emojis), headTop = piece.headTop) == null) toastLayerLimit()
        applyTool(EditorTool.Auto)
        afterEdit(item, before)
    }

    // --------------------------------------------------------------- draw

    fun setDrawColour(colour: Int) {
        drawColour = colour
        push()
    }

    fun setDrawSize(size: MarkerSize) {
        drawSize = size
        push()
    }

    /**
     * A Draw stroke starts (canvas px, kept inside the canvas, so a flushed drawing never needs shrinking);
     * refused with a toast when the sticker already holds 8 layers.
     */
    fun beginMarker(x: Float, y: Float) {
        val item = editableItem() ?: return
        if (tool != EditorTool.Draw || decor == null || !x.isFinite() || !y.isFinite()) return
        if (!item.editor.beginStroke(onCanvas(x), onCanvas(y), drawColour, drawSize)) {
            toastLayerLimit()
            return
        }
        tick++
        push()
    }

    fun extendMarker(x: Float, y: Float) {
        val item = activeItem() ?: return
        if (!x.isFinite() || !y.isFinite()) return
        item.editor.extendStroke(onCanvas(x), onCanvas(y))
        tick++
        push()
    }

    fun endMarker() {
        val item = activeItem() ?: return
        item.editor.endStroke()
        push()
        refreshDerived(item, THUMB_DEBOUNCE_MS)
    }

    private fun onCanvas(v: Float): Float = v.coerceIn(0f, DecorSpec.CANVAS)

    // ------------------------------------------------------------ animate

    /** Picks motion preset [id] (spec §7); clips keep none. The loop restarts, so playing stops. */
    fun setPreset(id: String) {
        val item = editableItem() ?: return
        if (item.isVideo) return
        if (data?.motion?.presets?.any { it.id == id } != true) return
        val before = item.editor.state
        item.editor.setPreset(id)
        playing = false
        afterEdit(item, before)
    }

    /** With reduced motion, a tap plays one loop of the preset (the canvas calls [stopPlaying] after it). */
    fun togglePlaying() {
        playing = !playing
        push()
    }

    fun stopPlaying() {
        if (!playing) return
        playing = false
        push()
    }

    // ------------------------------------------------------------ outline

    fun setOutlineOn(on: Boolean) = editDecor(activeItem()) { it.setOutlineOn(on) }

    /** A new thickness re-dilates the subject's silhouette at once (not debounced). */
    fun setOutlineThickness(thickness: OutlineThickness) {
        val item = activeItem() ?: return
        val before = item.editor.state
        item.editor.setOutlineThickness(thickness)
        tick++
        push()
        if (item.editor.state != before) refreshDerived(item)
    }

    fun setOutlineColour(colour: Int) = editDecor(activeItem()) { it.setOutlineColour(colour) }

    // ------------------------------------------------------------- strokes

    fun beginStroke(x: Float, y: Float) {
        val item = activeItem() ?: return
        if (item.cut != CutStatus.Done) return
        if (tool != EditorTool.Brush && tool != EditorTool.Erase) return
        val mask = item.mask ?: return
        finishMaskStroke()
        // A derived render that finished mid-stroke would hand back a subject without this stroke.
        derivedJobs.remove(item.id)?.cancel()
        val stroke = MaskStroke(
            keep = tool == EditorTool.Brush,
            radiusPx = CreateSpec.brushRadiusPx(brush),
            points = mutableListOf(PointF(x, y))
        )
        activeStroke = stroke
        paintSegment(item, mask, stroke, stroke.points[0], stroke.points[0])
        tick++
        push()
    }

    fun extendStroke(x: Float, y: Float) {
        val item = activeItem() ?: return
        val stroke = activeStroke ?: return
        val mask = item.mask ?: return
        val last = stroke.points.last()
        val point = PointF(x, y)
        paintSegment(item, mask, stroke, last, point)
        stroke.points.add(point)
        tick++
        push()
    }

    fun endStroke() {
        finishMaskStroke()
        push()
    }

    /** A single tap with Brush/Erase paints one dab (works while zoomed in). */
    fun tapStroke(x: Float, y: Float) {
        beginStroke(x, y)
        endStroke()
    }

    /** One stroke segment into the mask, and at once into the subject the canvas draws. */
    private fun paintSegment(item: MediaItem, mask: Bitmap, stroke: MaskStroke, from: PointF, to: PointF) {
        StickerRenderer.drawStrokeSegment(mask, stroke.keep, stroke.radiusPx, from, to)
        item.maskVersion++
        val subject = item.subject
        val src = item.source
        if (subject != null && src != null) StickerRenderer.patchSubject(subject, src, mask, from, to, stroke.radiusPx)
    }

    /** Records the active sticker's Brush or Erase stroke in progress as one undo step; its outline follows. */
    private fun finishMaskStroke() {
        val stroke = activeStroke ?: return
        activeStroke = null
        val item = activeItem() ?: return
        item.strokes.add(stroke)
        item.editor.recordMaskStroke()
        refreshDerived(item)
    }

    /** One step back (spec §4): a mask stroke, a Draw stroke or any decor change; a toast when there is none. */
    fun undo() {
        val item = activeItem() ?: return
        finishMaskStroke()
        val before = item.editor.state
        when (item.editor.undo()) {
            UndoResult.Nothing -> toast(UiText.res(R.string.create_toast_nothing_to_undo))
            UndoResult.MaskStroke -> undoMaskStroke(item)
            UndoResult.Changed -> refreshDerived(item, THUMB_DEBOUNCE_MS)
        }
        // Undo also ends a gesture in progress.
        liveLayerId = null
        gestureStart = null
        afterEdit(item, before)
    }

    private fun undoMaskStroke(item: MediaItem) {
        if (item.strokes.isEmpty()) return
        item.strokes.removeAt(item.strokes.lastIndex)
        val auto = item.autoMask ?: return
        val strokes = item.strokes.toList()
        val seq = ++item.rebuildSeq
        viewModelScope.launch {
            val rebuilt = withContext(Dispatchers.Default) {
                StickerRenderer.rebuildMask(auto, strokes)
            }
            if (seq != item.rebuildSeq) return@launch
            item.mask = rebuilt
            item.maskVersion++
            tick++
            push()
            refreshDerived(item)
        }
    }

    /**
     * What the canvas draws for the active sticker, from its cached bitmaps: the subject, its
     * silhouette, the decor and the live Draw strokes. Null until the decor is loaded. The subject
     * is null until the cut-out is done; draw [activeSource] while it runs.
     */
    fun activeScene(): SceneRenderer.Scene? {
        if (decor == null) return null
        val item = activeItem() ?: return null
        return SceneRenderer.Scene(item.subject, item.silhouette, item.editor.state, item.editor.liveStrokes)
    }

    /** The active sticker's raw picture, which the canvas shows while its cut-out runs. Never mutate it. */
    fun activeSource(): Bitmap? = activeItem()?.source

    // --------------------------------------------------------- pack details

    fun selectTray(index: Int) {
        trayIndex = index.coerceIn(0, max(0, selectedItems().size - 1))
        push()
    }

    fun setPackName(name: String) {
        packName = name.take(CreateSpec.NAME_MAX_CHARS)
        push()
    }

    fun notifyAlreadyAdded() = toast(UiText.res(R.string.toast_already_in_whatsapp))

    fun addToWhatsApp() = startExport(toWhatsApp = true)

    fun saveToMyPacksOnly() = startExport(toWhatsApp = false)

    /** Result of the ENABLE_STICKER_PACK activity. */
    fun onWhatsAppResult(added: Boolean, rejected: Boolean = false) {
        savedPackId?.let { id ->
            if (added) AppAnalytics.logPackAdded(id) else AppAnalytics.logPackAddCancelled(id, rejected)
        }
        // Armed here, shown once the finished flow lands on My Packs.
        if (added) RatingPromptController.onPackAdded(appContext)
        if (added) {
            exportState = AddVisualState.Added
            push()
            viewModelScope.launch {
                _events.emit(CreateEvent.ShowToast(UiText.res(R.string.toast_added_to_whatsapp), check = true))
                delay(900)
                finished = true
                decor?.clearCache()
                _events.emit(CreateEvent.ExportComplete)
            }
        } else {
            // Backed out of WhatsApp's confirm — back to idle, like the prototype.
            exportState = AddVisualState.Idle
            push()
        }
    }

    private fun startExport(toWhatsApp: Boolean) {
        if (exportState.isBusy()) return
        val existing = savedPackId
        if (existing != null) {
            // Already exported this session (e.g. the WhatsApp confirm was cancelled).
            if (toWhatsApp) {
                exportState = AddVisualState.Sent
                push()
                exportJob = viewModelScope.launch { sendToWhatsApp(existing, savedPackName) }
            } else {
                finishSaveOnly()
            }
            return
        }
        val stickers = selectedItems()
        // Next already did this for the sticker left open; nothing may export half-drawn.
        stickers.forEach(::flushDrawing)
        exportState = AddVisualState.Downloading
        exportProgress = 0f
        push()
        // Resolved now, in the app language, and kept with the pack.
        val words = appContext.inAppLanguage()
        val finalName = packName.trim().ifEmpty { words.getString(R.string.create_untitled) }
        val publisher = words.getString(R.string.create_publisher)
        val trayAt = trayIndex.coerceIn(0, max(0, stickers.size - 1))
        exportJob = viewModelScope.launch {
            try {
                val (id, name) = exportPack(stickers, finalName, publisher, trayAt)
                AppAnalytics.logPackCreated(
                    stickers = stickers.size,
                    animated = stickers.any { it.isVideo },
                    toWhatsApp = toWhatsApp
                )
                savedPackId = id
                savedPackName = name
                exportProgress = 1f
                if (toWhatsApp) {
                    exportState = AddVisualState.Sent
                    push()
                    sendToWhatsApp(id, name)
                } else {
                    finishSaveOnly()
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                CrashReporting.record(e, "pack_export")
                exportState = AddVisualState.Failed
                push()
            }
        }
    }

    /** Asks WhatsApp, off the main thread, what the add launches; the screen fires it as it arrives. */
    private suspend fun sendToWhatsApp(id: String, name: String) {
        val target = AddStickerPackFlow.resolveAddTarget(
            appContext, id, name, "create_add_intent", ioDispatcher
        )
        when (target) {
            is AddStickerPackFlow.AddTarget.Launch -> _events.emit(CreateEvent.LaunchAddToWhatsApp(target.intent))
            // Nothing left to launch: the pack is already everywhere.
            AddStickerPackFlow.AddTarget.AlreadyAdded -> onWhatsAppResult(added = true)
            AddStickerPackFlow.AddTarget.NoWhatsApp -> {
                onWhatsAppResult(added = false)
                _events.emit(CreateEvent.ShowNoWhatsApp)
            }
        }
    }

    private fun finishSaveOnly() {
        exportState = AddVisualState.Idle
        push()
        viewModelScope.launch {
            _events.emit(CreateEvent.ShowToast(UiText.res(R.string.create_toast_saved)))
            delay(900)
            finished = true
            decor?.clearCache()
            _events.emit(CreateEvent.ExportComplete)
        }
    }

    // --------------------------------------------------------------- export

    /**
     * Renders, size-caps, validates, writes (tmp dir → rename) and registers
     * the pack. Returns the saved pack's id and name. Every sticker is its
     * scene (spec §5): outline, layers and subject, as the editor shows it.
     */
    private suspend fun exportPack(
        stickers: List<MediaItem>,
        finalName: String,
        publisher: String,
        trayAt: Int
    ): Pair<String, String> {
        val renderer = decorLoad.await()?.renderer ?: throw IOException("The sticker decor could not be loaded")
        // What each sticker shows, read on the main thread before the work moves off it.
        val decorStates = stickers.map { it.editor.state }
        return withContext(Dispatchers.Default) {
            check(stickers.size in CreateSpec.MIN_STICKERS..CreateSpec.MAX_STICKERS) {
                "sticker count out of range: ${stickers.size}"
            }
            // Only clips animate for now; motion presets join the export next.
            val packAnimated = stickers.any { it.isVideo }
            val encoded = ArrayList<ByteArray>(stickers.size)
            stickers.forEachIndexed { index, item ->
                encoded += if (packAnimated) {
                    encodeAnimatedSticker(item, decorStates[index], renderer)
                } else {
                    encodeStaticStickerOf(item, decorStates[index], renderer)
                }
                progress((index + 1).toFloat() / (stickers.size + 1))
            }
            val trayIndexInPack = if (trayAt in stickers.indices) trayAt else 0
            val trayBytes = encodeTray(stickers[trayIndexInPack], decorStates[trayIndexInPack], renderer)
            progress(1f)

            val dirId = "own-" + UUID.randomUUID().toString().replace("-", "").take(8)
            val fileNames = List(stickers.size) { String.format(Locale.ROOT, "%02d.webp", it + 1) }

            StickerPackValidator.verifyStickerPackValidity(
                ValidatablePack(
                    identifier = dirId,
                    name = finalName,
                    publisher = publisher,
                    trayImageFile = OwnPackFiles.TRAY_FILE,
                    trayBytes = trayBytes,
                    animatedStickerPack = packAnimated,
                    stickers = encoded.mapIndexed { index, bytes ->
                        ValidatableSticker(fileNames[index], bytes, DEFAULT_EMOJIS)
                    },
                    // What StickerContentProvider will hand WhatsApp for this pack.
                    publisherEmail = appContext.getString(R.string.config_support_email),
                    privacyPolicyWebsite = appContext.getString(R.string.config_privacy_policy_url),
                    androidPlayStoreLink = StickerContentProvider.ANDROID_PLAY_STORE_LINK
                )
            )

            val dir = withContext(ioDispatcher) {
                OwnPackFiles.write(appContext.filesDir, dirId, trayBytes, fileNames.zip(encoded))
            }
            val id = myPacksRepository.saveOwnPack(
                name = finalName,
                publisher = publisher,
                animated = packAnimated,
                stickers = fileNames.map { it to DEFAULT_EMOJIS },
                dir = dir.absolutePath,
                trayFile = OwnPackFiles.TRAY_FILE
            )
            id to finalName
        }
    }

    private suspend fun progress(fraction: Float) {
        withContext(Dispatchers.Main.immediate) {
            exportProgress = fraction.coerceIn(0f, 1f)
            push()
        }
    }

    /** The sticker's scene at rest in a new 512 bitmap: outline, layers and [source] cut out by [mask]. */
    private fun renderStillOf(renderer: SceneRenderer, source: Bitmap, mask: Bitmap, decorState: DecorState): Bitmap {
        val outline = decorState.outline
        val radius = if (outline.on) renderer.outlineRadius(outline.thickness) else null
        val (subject, silhouette) = subjectAndSilhouette(source, mask, radius)
        val still = renderer.renderStill(SceneRenderer.Scene(subject, silhouette, decorState))
        subject.recycle()
        silhouette?.recycle()
        return still
    }

    /** 512 export frame: the still scene fitted into the 460 content square. */
    private fun renderExportFrame(
        renderer: SceneRenderer,
        source: Bitmap,
        mask: Bitmap,
        decorState: DecorState
    ): Bitmap {
        val still = renderStillOf(renderer, source, mask, decorState)
        val export = StickerRenderer.renderExportCanvas(still)
        still.recycle()
        return export
    }

    private suspend fun encodeStaticStickerOf(
        item: MediaItem,
        decorState: DecorState,
        renderer: SceneRenderer
    ): ByteArray {
        ensureCutReady(item)
        val export = renderExportFrame(renderer, checkNotNull(item.source), checkNotNull(item.mask), decorState)
        val bytes = StickerRenderer.encodeStaticSticker(export)
        export.recycle()
        return bytes
    }

    /**
     * One sticker of an animated pack. Video items get the full per-frame
     * pipeline (auto mask on every frame + the shared manual strokes, and the
     * sticker's layers and outline on every frame); still pictures inside an
     * animated pack are muxed as two identical frames so WhatsApp's "all
     * stickers animate" rule holds.
     */
    private suspend fun encodeAnimatedSticker(
        item: MediaItem,
        decorState: DecorState,
        renderer: SceneRenderer
    ): ByteArray {
        if (!item.isVideo) {
            ensureCutReady(item)
            val export = renderExportFrame(renderer, checkNotNull(item.source), checkNotNull(item.mask), decorState)
            try {
                var best: ByteArray? = null
                for (quality in intArrayOf(80, 65, 50, 40, 30)) {
                    val frame = StickerRenderer.encodeWebp(export, quality)
                    val bytes = AnimatedWebpMuxer.mux(listOf(frame, frame), listOf(500, 500), 0)
                    if (best == null || bytes.size < best.size) best = bytes
                    if (bytes.size <= CreateSpec.ANIMATED_LIMIT_BYTES) return bytes
                }
                return checkNotNull(best)
            } finally {
                export.recycle()
            }
        }

        val uri = checkNotNull(item.uri) { "video item without uri" }
        val decoded = CreateMedia.decodeVideoFrames(appContext, uri)
            ?: throw IOException("Could not read the clip")
        val strokes = item.strokes.toList()
        val exports = ArrayList<Bitmap>(decoded.frames.size)
        try {
            for (frame in decoded.frames) {
                val auto = segment(frame)
                val mask = StickerRenderer.rebuildMask(auto, strokes)
                auto.recycle()
                exports += renderExportFrame(renderer, frame, mask, decorState)
                mask.recycle()
            }

            var best: ByteArray? = null
            val counts = intArrayOf(exports.size, 8, 6)
                .distinct()
                .filter { it in 2..exports.size }
            for (count in counts) {
                val subset = pickEvenly(exports, count)
                val perFrame = (decoded.totalDurationMs / count).coerceAtLeast(40)
                for (quality in intArrayOf(80, 65, 50, 40, 30)) {
                    val frames = subset.map { StickerRenderer.encodeWebp(it, quality) }
                    val bytes = AnimatedWebpMuxer.mux(frames, List(count) { perFrame }, 0)
                    if (best == null || bytes.size < best.size) best = bytes
                    if (bytes.size <= CreateSpec.ANIMATED_LIMIT_BYTES) return bytes
                }
            }
            return checkNotNull(best)
        } finally {
            // recycle() on an already-recycled bitmap is a no-op, so both lists are safe here.
            decoded.frames.forEach { it.recycle() }
            exports.forEach { it.recycle() }
        }
    }

    private fun <T> pickEvenly(list: List<T>, count: Int): List<T> {
        if (count >= list.size) return list
        return (0 until count).map { i ->
            list[(i.toFloat() * (list.size - 1) / (count - 1)).roundToInt()]
        }
    }

    /** The tray icon from the sticker's scene at rest (a clip's first frame). */
    private suspend fun encodeTray(item: MediaItem, decorState: DecorState, renderer: SceneRenderer): ByteArray {
        ensureCutReady(item)
        val still = renderStillOf(renderer, checkNotNull(item.source), checkNotNull(item.mask), decorState)
        val bytes = StickerRenderer.encodeTrayPng(still)
        still.recycle()
        return bytes
    }

    /** Guarantees source + mask exist (export can outrun a skipped cut-out). */
    private suspend fun ensureCutReady(item: MediaItem) {
        if (item.source == null) {
            item.source = withContext(ioDispatcher) { loadSource(item) } ?: placeholderSource()
        }
        if (item.autoMask == null) {
            item.autoMask = segment(checkNotNull(item.source))
        }
        if (item.mask == null) {
            item.mask = StickerRenderer.rebuildMask(checkNotNull(item.autoMask), item.strokes.toList())
        }
    }

    // ----------------------------------------------------- cut-out pipeline

    private suspend fun runCutout(item: MediaItem, resetStrokes: Boolean) {
        try {
            val src = item.source
                ?: withContext(ioDispatcher) { loadSource(item) }
                ?: placeholderSource()
            withContext(Dispatchers.Main.immediate) {
                item.source = src
                // Shows the raw picture behind the "Cutting out…" veil right away.
                tick++
                push()
            }
            landCutout(item, segment(src), resetStrokes)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            CrashReporting.record(e, "cutout")
            withContext(Dispatchers.Main.immediate) {
                if (item.source == null) item.source = placeholderSource()
            }
            landCutout(item, StickerRenderer.fullMask(), resetStrokes = false)
        }
    }

    /**
     * A finished cut-out: [auto] plus the kept strokes becomes the mask, and its subject and
     * silhouette are made before the veil lifts, so the canvas never shows an empty cut-out.
     */
    private suspend fun landCutout(item: MediaItem, auto: Bitmap, resetStrokes: Boolean) {
        // Long loaded by the time a cut-out finishes; today's radius if loading failed.
        val renderer = decorLoad.await()?.renderer
        withContext(Dispatchers.Main.immediate) {
            item.autoMask = auto
            if (resetStrokes) {
                item.strokes.clear()
                item.editor.clearMaskHistory()
            }
            val src = checkNotNull(item.source)
            val strokes = item.strokes.toList()
            val thickness = item.editor.state.outline.thickness
            val radius = renderer?.outlineRadius(thickness) ?: CreateSpec.OUTLINE_RADIUS
            val (mask, made) = withContext(Dispatchers.Default) {
                val mask = StickerRenderer.rebuildMask(auto, strokes)
                mask to subjectAndSilhouette(src, mask, radius)
            }
            item.mask = mask
            val version = ++item.maskVersion
            item.rebuildSeq++
            item.subject = made.first
            item.subjectVersion = version
            item.silhouette = made.second
            item.silhouetteVersion = version
            item.silhouetteThickness = thickness
            item.cut = CutStatus.Done
            tick++
            push()
            refreshDerived(item)
        }
    }

    private fun loadSource(item: MediaItem): Bitmap? = when {
        item.cameraFile != null -> try {
            android.graphics.BitmapFactory.decodeFile(item.cameraFile.absolutePath)?.let { raw ->
                val square = CreateMedia.centerCropSquare(raw, CreateSpec.CANVAS_SIZE)
                if (square !== raw) raw.recycle()
                square
            }
        } catch (e: Exception) {
            null
        }
        item.isVideo -> item.uri?.let { CreateMedia.videoThumb(appContext, it, CreateSpec.CANVAS_SIZE) }
        else -> item.uri?.let { CreateMedia.loadSquareBitmap(appContext, it) }
    }

    private fun placeholderSource(): Bitmap =
        Bitmap.createBitmap(CreateSpec.CANVAS_SIZE, CreateSpec.CANVAS_SIZE, Bitmap.Config.ARGB_8888)
            .apply { eraseColor(Color.parseColor("#F3F5F8")) }

    /** On-device subject segmentation → soft ARGB mask (full mask fallback). */
    private suspend fun segment(sourceBitmap: Bitmap): Bitmap = try {
        val image = InputImage.fromBitmap(sourceBitmap, 0)
        val result = getSegmenter().process(image).await()
        val mask = withContext(Dispatchers.Default) {
            StickerRenderer.maskFromConfidence(result.buffer, result.width, result.height)
        }
        if (maskIsEmpty(mask)) {
            mask.recycle()
            StickerRenderer.fullMask()
        } else {
            mask
        }
    } catch (ce: CancellationException) {
        throw ce
    } catch (e: Exception) {
        // ML Kit failed: the full-mask fallback keeps the flow going, but it is a silent quality drop.
        CrashReporting.record(e, "segmentation")
        StickerRenderer.fullMask()
    }

    private fun maskIsEmpty(mask: Bitmap): Boolean {
        val step = 32
        var y = 0
        while (y < mask.height) {
            var x = 0
            while (x < mask.width) {
                if ((mask.getPixel(x, y) ushr 24) > 40) return false
                x += step
            }
            y += step
        }
        return true
    }

    /**
     * Brings [item]'s derived bitmaps up to date, off the main thread: the subject and its silhouette
     * when the mask or the outline thickness changed since they were made, then the rail thumbnail of
     * the decorated sticker. [delayMs] lets a burst of decor edits settle first.
     */
    private fun refreshDerived(item: MediaItem, delayMs: Long = 0L) {
        derivedJobs.remove(item.id)?.cancel()
        derivedJobs[item.id] = viewModelScope.launch {
            if (delayMs > 0) delay(delayMs)
            val renderer = renderer ?: return@launch
            val src = item.source ?: return@launch
            val mask = item.mask ?: return@launch
            val decorState = item.editor.state
            val live = item.editor.liveStrokes
            val version = item.maskVersion
            val thickness = decorState.outline.thickness
            val radius = renderer.outlineRadius(thickness)
            val keptSubject = item.subject?.takeIf { item.subjectVersion == version }
            val keptSilhouette = item.silhouette
                ?.takeIf { item.silhouetteVersion == version && item.silhouetteThickness == thickness }
            val (subject, silhouette, thumb) = withContext(Dispatchers.Default) {
                val (subject, silhouette) = subjectAndSilhouette(src, mask, radius, keptSubject, keptSilhouette)
                val still = renderer.renderStill(SceneRenderer.Scene(subject, silhouette, decorState, live))
                val thumb = StickerRenderer.thumbOf(still)
                still.recycle()
                Triple(subject, silhouette, thumb)
            }
            if (item.maskVersion == version) {
                item.subject = subject
                item.subjectVersion = version
                item.silhouette = silhouette
                item.silhouetteVersion = version
                item.silhouetteThickness = thickness
            }
            item.stickerThumb = thumb.asImageBitmap()
            tick++
            push()
        }
    }

    /**
     * A scene's subject ([source] cut out by [mask]) and silhouette ([mask] dilated by [radius]; none
     * without a radius). A [subject] or [silhouette] the caller still holds current is reused rather
     * than made again. Bitmap work: call it off the main thread.
     */
    private fun subjectAndSilhouette(
        source: Bitmap,
        mask: Bitmap,
        radius: Float?,
        subject: Bitmap? = null,
        silhouette: Bitmap? = null
    ): Pair<Bitmap, Bitmap?> = Pair(
        subject ?: StickerRenderer.maskedSubject(source, mask),
        silhouette ?: radius?.let { StickerRenderer.outlineOf(mask, it) }
    )

    private fun getSegmenter(): Segmenter {
        segmenterOrNull?.let { return it }
        val options = SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
            .enableRawSizeMask()
            .build()
        return Segmentation.getClient(options).also { segmenterOrNull = it }
    }

    private fun nextId(): String = "m${idSeq++}"

    /** Whether the user turned animations off (spec §6); false where the setting can't be read. */
    private fun reduceMotion(): Boolean = runCatching {
        Settings.Global.getFloat(appContext.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }.getOrDefault(false)

    private fun isRtl(tag: String): Boolean = AppLanguages.byTag(tag)?.rtl == true

    /**
     * Reads the app language again (it can change while this activity-scoped session lives on); on a
     * change of direction, the thumbnails with text are rendered again.
     */
    private fun refreshLanguage() {
        val rtl = isRtl(AppLanguages.effectiveTag())
        if (rtl == rtlLanguage) return
        rtlLanguage = rtl
        items.filter { item -> item.editor.state.layers.any { it.content is LayerContent.Text } }
            .forEach { refreshDerived(it) }
        tick++
        push()
    }

    // ---------------------------------------------------------------- reset

    private fun resetSession() {
        cutoutJob?.cancel()
        exportJob?.cancel()
        derivedJobs.values.forEach { it.cancel() }
        derivedJobs.clear()
        activeStroke = null
        items.forEach { it.recycleBitmaps() }
        items.clear()
        decor?.clearCache()
        shots = 0
        source = ImportSource.Photos
        activeIndex = 0
        tool = EditorTool.Auto
        zoomed = false
        brush = 2
        addTab = AddTab.Text
        emojiTab = EmojiCatalog.CATEGORIES.first()
        skinPopover = null
        drawColour = DecorSpec.ROSE
        drawSize = MarkerSize.M
        playing = false
        liveLayerId = null
        gestureStart = null
        limitToastShown = false
        trayIndex = 0
        packName = ""
        tick = 0
        exportState = AddVisualState.Idle
        exportProgress = 0f
        savedPackId = null
        savedPackName = ""
        finished = false
        viewModelScope.launch(ioDispatcher) {
            File(appContext.cacheDir, "create").deleteRecursively()
        }
        push()
    }

    override fun onCleared() {
        cutoutJob?.cancel()
        exportJob?.cancel()
        derivedJobs.values.forEach { it.cancel() }
        items.forEach { it.recycleBitmaps() }
        items.clear()
        segmenterOrNull?.close()
        segmenterOrNull = null
        File(appContext.cacheDir, "create").deleteRecursively()
        super.onCleared()
    }
}
