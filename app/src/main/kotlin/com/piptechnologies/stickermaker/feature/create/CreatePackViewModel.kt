package com.piptechnologies.stickermaker.feature.create

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.Segmenter
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import com.piptechnologies.stickermaker.R
import com.piptechnologies.stickermaker.core.data.di.IoDispatcher
import com.piptechnologies.stickermaker.core.data.repo.MyPacksRepository
import com.piptechnologies.stickermaker.core.design.components.AddVisualState
import com.piptechnologies.stickermaker.whatsapp.AnimatedWebpMuxer
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

/** WhatsApp shows a publisher line; own packs carry the design's phrase for them. */
private const val PACK_PUBLISHER = "Made by you"

/** The design assigns no per-sticker emoji, so every sticker gets this default pair. */
private val DEFAULT_EMOJIS = listOf("❤️", "😊")

private const val TRAY_FILE = "tray.png"

/**
 * The one Create session shared by Import, Cut out and Pack details, exactly
 * like the prototype's single `create` state object: picked media, per-sticker
 * masks/strokes/caption/outline, active tool, pack name, tray choice and the
 * export → add-to-WhatsApp state machine.
 *
 * All bitmap and segmentation work runs off the main thread; session fields
 * are main-confined and published through [state].
 */
@HiltViewModel
class CreatePackViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val myPacksRepository: MyPacksRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val _state = MutableStateFlow(CreateUiState())
    val state: StateFlow<CreateUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<CreateEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<CreateEvent> = _events.asSharedFlow()

    // ------------------------------------------------------- internal model

    private class MediaItem(
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
        var outline: Bitmap? = null
        val strokes = mutableListOf<MaskStroke>()
        var text: String = ""
        var outlineOn: Boolean = true
        var frameThumb: ImageBitmap? = null
        var stickerThumb: ImageBitmap? = null

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
            cut = cut
        )

        fun recycleBitmaps() {
            source?.recycle()
            autoMask?.recycle()
            mask?.recycle()
            outline?.recycle()
            source = null
            autoMask = null
            mask = null
            outline = null
        }
    }

    // Main-confined session fields.
    private val items = mutableListOf<MediaItem>()
    private var idSeq = 0
    private var shots = 0
    private var source = ImportSource.Photos
    private var activeIndex = 0
    private var tool = EditorTool.Auto
    private var zoomed = false
    private var brush = 2
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
    private var textThumbJob: Job? = null
    private var typefaceCache: Typeface? = null

    // ---------------------------------------------------------------- state

    private fun selectedItems(): List<MediaItem> = items.filter { it.selected }

    private fun activeItem(): MediaItem? = selectedItems().getOrNull(activeIndex)

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
            activeText = active?.text.orEmpty(),
            activeOutlineOn = active?.outlineOn ?: true,
            activeCut = active?.cut ?: CutStatus.None,
            activeDurationLabel = active?.durationLabel,
            canUndo = active != null && (active.strokes.isNotEmpty() || active.text.isNotEmpty()),
            anyPending = selected.any { it.cut == CutStatus.Pending },
            editorTick = tick,
            trayIndex = trayIndex.coerceIn(0, max(0, selected.size - 1)),
            packName = packName,
            animatedPack = selected.any { it.isVideo },
            exportState = exportState,
            exportProgress = exportProgress
        )
    }

    private fun toast(message: String, check: Boolean = false) {
        _events.tryEmit(CreateEvent.ShowToast(message, check))
    }

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
            if (clamped) toast("30 stickers is the maximum")
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
                toast("30 stickers is the maximum")
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
            toast("Shot added to the pack")
        }
    }

    fun togglePicked(id: String) {
        val item = items.find { it.id == id } ?: return
        if (!item.selected && selectedItems().size >= CreateSpec.MAX_STICKERS) {
            toast("30 stickers is the maximum")
            return
        }
        item.selected = !item.selected
        activeIndex = activeIndex.coerceIn(0, max(0, selectedItems().size - 1))
        trayIndex = trayIndex.coerceIn(0, max(0, selectedItems().size - 1))
        push()
    }

    /**
     * Next on Import: queue the on-device cut-out for every selected sticker,
     * sequentially, so the editor opens with the per-sticker spinners running.
     */
    fun beginCutouts() {
        val targets = selectedItems().filter { it.cut == CutStatus.None }
        if (targets.isEmpty()) return
        targets.forEach { it.cut = CutStatus.Pending }
        push()
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

    // --------------------------------------------------------------- editor

    fun selectSticker(index: Int) {
        activeIndex = index.coerceIn(0, max(0, selectedItems().size - 1))
        activeStroke = null
        push()
    }

    fun selectTool(newTool: EditorTool) {
        tool = newTool
        push()
        if (newTool == EditorTool.Auto) {
            // Auto re-runs the cut-out on the current sticker and resets strokes.
            val item = activeItem() ?: return
            if (item.cut == CutStatus.Pending) return
            item.cut = CutStatus.Pending
            push()
            viewModelScope.launch { runCutout(item, resetStrokes = true) }
        }
    }

    fun toggleZoom() {
        zoomed = !zoomed
        push()
    }

    fun setBrush(size: Int) {
        brush = size.coerceIn(1, 3)
        push()
    }

    fun setStickerText(text: String) {
        val item = activeItem() ?: return
        item.text = text.take(CreateSpec.TEXT_MAX_CHARS)
        tick++
        push()
        textThumbJob?.cancel()
        textThumbJob = viewModelScope.launch {
            delay(350)
            refreshDerived(item)
        }
    }

    fun toggleOutline() {
        val item = activeItem() ?: return
        item.outlineOn = !item.outlineOn
        tick++
        push()
        refreshDerived(item)
    }

    fun beginStroke(x: Float, y: Float) {
        val item = activeItem() ?: return
        if (item.cut != CutStatus.Done) return
        if (tool != EditorTool.Brush && tool != EditorTool.Erase) return
        val mask = item.mask ?: return
        val stroke = MaskStroke(
            keep = tool == EditorTool.Brush,
            radiusPx = CreateSpec.brushRadiusPx(brush),
            points = mutableListOf(PointF(x, y))
        )
        activeStroke = stroke
        StickerRenderer.drawStrokeSegment(mask, stroke.keep, stroke.radiusPx, stroke.points[0], stroke.points[0])
        tick++
        push()
    }

    fun extendStroke(x: Float, y: Float) {
        val item = activeItem() ?: return
        val stroke = activeStroke ?: return
        val mask = item.mask ?: return
        val last = stroke.points.last()
        val point = PointF(x, y)
        StickerRenderer.drawStrokeSegment(mask, stroke.keep, stroke.radiusPx, last, point)
        stroke.points.add(point)
        tick++
        push()
    }

    fun endStroke() {
        val item = activeItem() ?: return
        val stroke = activeStroke ?: return
        activeStroke = null
        item.strokes.add(stroke)
        push()
        refreshDerived(item)
    }

    /** A single tap with Brush/Erase paints one dab (works while zoomed in). */
    fun tapStroke(x: Float, y: Float) {
        beginStroke(x, y)
        endStroke()
    }

    /** One touch back: last stroke first, then the caption, then a toast. */
    fun undo() {
        val item = activeItem() ?: return
        when {
            item.strokes.isNotEmpty() -> {
                item.strokes.removeAt(item.strokes.lastIndex)
                val auto = item.autoMask ?: return
                val strokes = item.strokes.toList()
                viewModelScope.launch {
                    val rebuilt = withContext(Dispatchers.Default) {
                        StickerRenderer.rebuildMask(auto, strokes)
                    }
                    item.mask = rebuilt
                    tick++
                    push()
                    refreshDerived(item)
                }
            }
            item.text.isNotEmpty() -> {
                item.text = ""
                tick++
                push()
                refreshDerived(item)
            }
            else -> toast("Nothing to undo")
        }
    }

    /** The live bitmaps the editor canvas draws. Read-only; never mutate. */
    fun activeCanvas(): ActiveCanvas {
        val item = activeItem()
        return ActiveCanvas(item?.source, item?.mask, item?.outline)
    }

    // --------------------------------------------------------- pack details

    fun selectTray(index: Int) {
        trayIndex = index.coerceIn(0, max(0, selectedItems().size - 1))
        push()
    }

    fun setPackName(name: String) {
        packName = name.take(CreateSpec.NAME_MAX_CHARS)
        push()
    }

    fun notifyAlreadyAdded() = toast("Already in WhatsApp")

    fun addToWhatsApp() = startExport(toWhatsApp = true)

    fun saveToMyPacksOnly() = startExport(toWhatsApp = false)

    /** Result of the ENABLE_STICKER_PACK activity. */
    fun onWhatsAppResult(added: Boolean) {
        if (added) {
            exportState = AddVisualState.Added
            push()
            viewModelScope.launch {
                _events.emit(CreateEvent.ShowToast("Added to WhatsApp", check = true))
                delay(900)
                finished = true
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
                _events.tryEmit(CreateEvent.LaunchAddToWhatsApp(existing, savedPackName))
            } else {
                finishSaveOnly()
            }
            return
        }
        exportState = AddVisualState.Downloading
        exportProgress = 0f
        push()
        val stickers = selectedItems()
        val finalName = packName.trim().ifEmpty { "Untitled pack" }
        val trayAt = trayIndex.coerceIn(0, max(0, stickers.size - 1))
        exportJob = viewModelScope.launch {
            try {
                val (id, name) = exportPack(stickers, finalName, trayAt)
                savedPackId = id
                savedPackName = name
                exportProgress = 1f
                if (toWhatsApp) {
                    exportState = AddVisualState.Sent
                    push()
                    _events.emit(CreateEvent.LaunchAddToWhatsApp(id, name))
                } else {
                    finishSaveOnly()
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                exportState = AddVisualState.Failed
                push()
            }
        }
    }

    private fun finishSaveOnly() {
        exportState = AddVisualState.Idle
        push()
        viewModelScope.launch {
            _events.emit(CreateEvent.ShowToast("Saved to My Packs"))
            delay(900)
            finished = true
            _events.emit(CreateEvent.ExportComplete)
        }
    }

    // --------------------------------------------------------------- export

    /**
     * Renders, size-caps, validates, writes (tmp dir → rename) and registers
     * the pack. Returns the saved pack's id and name.
     */
    private suspend fun exportPack(
        stickers: List<MediaItem>,
        finalName: String,
        trayAt: Int
    ): Pair<String, String> = withContext(Dispatchers.Default) {
        check(stickers.size in CreateSpec.MIN_STICKERS..CreateSpec.MAX_STICKERS) {
            "sticker count out of range: ${stickers.size}"
        }
        val packAnimated = stickers.any { it.isVideo }
        val typeface = typeface()
        val encoded = ArrayList<ByteArray>(stickers.size)
        stickers.forEachIndexed { index, item ->
            encoded += if (packAnimated) {
                encodeAnimatedSticker(item, typeface)
            } else {
                encodeStaticStickerOf(item, typeface)
            }
            progress((index + 1).toFloat() / (stickers.size + 1))
        }
        val trayItem = stickers.getOrNull(trayAt) ?: stickers.first()
        val trayBytes = encodeTray(trayItem, typeface)
        progress(1f)

        val dirId = "own-" + UUID.randomUUID().toString().replace("-", "").take(8)
        val fileNames = List(stickers.size) { String.format(Locale.ROOT, "%02d.webp", it + 1) }

        StickerPackValidator.verifyStickerPackValidity(
            ValidatablePack(
                identifier = dirId,
                name = finalName,
                publisher = PACK_PUBLISHER,
                trayImageFile = TRAY_FILE,
                trayBytes = trayBytes,
                animatedStickerPack = packAnimated,
                stickers = encoded.mapIndexed { index, bytes ->
                    ValidatableSticker(fileNames[index], bytes, DEFAULT_EMOJIS)
                }
            )
        )

        val dir = withContext(ioDispatcher) {
            writePackFiles(dirId, trayBytes, fileNames.zip(encoded))
        }
        val id = myPacksRepository.saveOwnPack(
            name = finalName,
            publisher = PACK_PUBLISHER,
            animated = packAnimated,
            stickers = fileNames.map { it to DEFAULT_EMOJIS },
            dir = dir.absolutePath,
            trayFile = TRAY_FILE
        )
        id to finalName
    }

    private suspend fun progress(fraction: Float) {
        withContext(Dispatchers.Main.immediate) {
            exportProgress = fraction.coerceIn(0f, 1f)
            push()
        }
    }

    /** 512 export frame (outline + masked subject + caption, 460 content fit). */
    private fun renderExportFrame(
        source: Bitmap,
        mask: Bitmap,
        outlineOn: Boolean,
        text: String,
        typeface: Typeface?
    ): Bitmap {
        val outline = if (outlineOn) StickerRenderer.outlineOf(mask) else null
        val composite = StickerRenderer.renderComposite(source, mask, outline, text, typeface)
        outline?.recycle()
        val export = StickerRenderer.renderExportCanvas(composite)
        composite.recycle()
        return export
    }

    private suspend fun encodeStaticStickerOf(item: MediaItem, typeface: Typeface?): ByteArray {
        ensureCutReady(item)
        val export = renderExportFrame(
            checkNotNull(item.source), checkNotNull(item.mask), item.outlineOn, item.text, typeface
        )
        val bytes = StickerRenderer.encodeStaticSticker(export)
        export.recycle()
        return bytes
    }

    /**
     * One sticker of an animated pack. Video items get the full per-frame
     * pipeline (auto mask on every frame + the shared manual strokes); still
     * pictures inside an animated pack are muxed as two identical frames so
     * WhatsApp's "all stickers animate" rule holds.
     */
    private suspend fun encodeAnimatedSticker(item: MediaItem, typeface: Typeface?): ByteArray {
        if (!item.isVideo) {
            ensureCutReady(item)
            val export = renderExportFrame(
                checkNotNull(item.source), checkNotNull(item.mask), item.outlineOn, item.text, typeface
            )
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
                exports += renderExportFrame(frame, mask, item.outlineOn, item.text, typeface)
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

    private suspend fun encodeTray(item: MediaItem, typeface: Typeface?): ByteArray {
        ensureCutReady(item)
        val mask = checkNotNull(item.mask)
        val outline = if (item.outlineOn) StickerRenderer.outlineOf(mask) else null
        val composite = StickerRenderer.renderComposite(
            checkNotNull(item.source), mask, outline, item.text, typeface
        )
        outline?.recycle()
        val bytes = StickerRenderer.encodeTrayPng(composite)
        composite.recycle()
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

    private fun writePackFiles(
        dirId: String,
        trayBytes: ByteArray,
        stickerFiles: List<Pair<String, ByteArray>>
    ): File {
        val root = File(appContext.filesDir, "own").apply { mkdirs() }
        val tmp = File(root, "$dirId.tmp")
        val finalDir = File(root, dirId)
        var completed = false
        try {
            tmp.deleteRecursively()
            if (!tmp.mkdirs()) throw IOException("Could not create ${tmp.absolutePath}")
            File(tmp, TRAY_FILE).writeBytes(trayBytes)
            stickerFiles.forEach { (name, bytes) -> File(tmp, name).writeBytes(bytes) }
            if (finalDir.exists() && !finalDir.deleteRecursively()) {
                throw IOException("Could not replace ${finalDir.absolutePath}")
            }
            if (!tmp.renameTo(finalDir)) {
                throw IOException("Could not move pack $dirId into place")
            }
            completed = true
            return finalDir
        } finally {
            if (!completed) tmp.deleteRecursively()
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
            val auto = segment(src)
            withContext(Dispatchers.Main.immediate) {
                item.autoMask = auto
                if (resetStrokes) item.strokes.clear()
                item.mask = StickerRenderer.rebuildMask(auto, item.strokes.toList())
                item.cut = CutStatus.Done
                tick++
                push()
            }
            refreshDerived(item)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            withContext(Dispatchers.Main.immediate) {
                if (item.source == null) item.source = placeholderSource()
                item.autoMask = StickerRenderer.fullMask()
                item.mask = StickerRenderer.rebuildMask(checkNotNull(item.autoMask), item.strokes.toList())
                item.cut = CutStatus.Done
                tick++
                push()
            }
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

    /** Rebuilds the outline + the small composite thumb after an edit settles. */
    private fun refreshDerived(item: MediaItem) {
        val src = item.source ?: return
        val mask = item.mask ?: return
        val text = item.text
        val outlineOn = item.outlineOn
        derivedJobs.remove(item.id)?.cancel()
        derivedJobs[item.id] = viewModelScope.launch {
            val (outline, thumb) = withContext(Dispatchers.Default) {
                val outline = StickerRenderer.outlineOf(mask)
                val composite = StickerRenderer.renderComposite(
                    src, mask, if (outlineOn) outline else null, text, typeface()
                )
                val thumb = StickerRenderer.thumbOf(composite)
                composite.recycle()
                outline to thumb
            }
            item.outline = outline
            item.stickerThumb = thumb.asImageBitmap()
            tick++
            push()
        }
    }

    private fun getSegmenter(): Segmenter {
        segmenterOrNull?.let { return it }
        val options = SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
            .enableRawSizeMask()
            .build()
        return Segmentation.getClient(options).also { segmenterOrNull = it }
    }

    private fun typeface(): Typeface? {
        typefaceCache?.let { return it }
        return try {
            ResourcesCompat.getFont(appContext, R.font.hg_extrabold)
        } catch (e: Exception) {
            null
        }?.also { typefaceCache = it }
    }

    private fun nextId(): String = "m${idSeq++}"

    // ---------------------------------------------------------------- reset

    private fun resetSession() {
        cutoutJob?.cancel()
        exportJob?.cancel()
        textThumbJob?.cancel()
        derivedJobs.values.forEach { it.cancel() }
        derivedJobs.clear()
        activeStroke = null
        items.forEach { it.recycleBitmaps() }
        items.clear()
        shots = 0
        source = ImportSource.Photos
        activeIndex = 0
        tool = EditorTool.Auto
        zoomed = false
        brush = 2
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
