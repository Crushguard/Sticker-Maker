package com.piptechnologies.stickermaker.feature.create

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import com.piptechnologies.stickermaker.core.design.components.AddVisualState

/**
 * Shared types for the Create flow (Import → Cut out → Pack details).
 *
 * The prototype keeps one `create` session object across all three steps
 * (source, selected media, per-sticker edits, active index, tool, pack name,
 * tray index), so the flow shares a single [CreatePackViewModel].
 */

/** The segmented import source: Photos / Camera / Video. */
enum class ImportSource { Photos, Camera, Video }

/**
 * Editor tools from the dark tool bar. Zoom is not listed because the
 * prototype treats it as an orthogonal toggle: activating Zoom never changes
 * the current tool, it only scales the canvas.
 */
enum class EditorTool { Auto, Brush, Erase, Text }

/** Per-sticker cut-out progress: untouched → spinner → editable. */
enum class CutStatus { None, Pending, Done }

/** One picked media item as the screens see it. */
@Immutable
data class CreateItemUi(
    val id: String,
    val isVideo: Boolean,
    val selected: Boolean,
    /** "▶ 0:03" style clip length, video items only. */
    val durationLabel: String?,
    /** Coil model for the raw picture (gallery [android.net.Uri] or camera [java.io.File]). */
    val pickerModel: Any?,
    /** Raw preview for items Coil cannot load (video first frame). */
    val frameThumb: ImageBitmap?,
    /** Small composite preview (outline + cut subject + caption) once cut. */
    val stickerThumb: ImageBitmap?,
    val cut: CutStatus
)

/** Whole-session UI state observed by all three screens. */
@Immutable
data class CreateUiState(
    // Import
    val source: ImportSource = ImportSource.Photos,
    val items: List<CreateItemUi> = emptyList(),
    val selectedCount: Int = 0,
    val shots: Int = 0,
    // Editor
    val activeIndex: Int = 0,
    val tool: EditorTool = EditorTool.Auto,
    val zoomed: Boolean = false,
    val brush: Int = 2,
    val activeText: String = "",
    val activeOutlineOn: Boolean = true,
    val activeCut: CutStatus = CutStatus.None,
    val activeDurationLabel: String? = null,
    val canUndo: Boolean = false,
    val anyPending: Boolean = false,
    /** Bumped whenever an editor bitmap changed; invalidates the live canvas. */
    val editorTick: Int = 0,
    // Pack details
    val trayIndex: Int = 0,
    val packName: String = "",
    val animatedPack: Boolean = false,
    val exportState: AddVisualState = AddVisualState.Idle,
    val exportProgress: Float = 0f
) {
    /** The stickers of the pack, in pick order (editor + details rails). */
    val selectedItems: List<CreateItemUi> get() = items.filter { it.selected }
}

/** One-shot effects the screens react to. */
sealed interface CreateEvent {
    data class ShowToast(val message: String, val check: Boolean = false) : CreateEvent

    /** Export finished and the pack is saved; fire the WhatsApp add intent. */
    data class LaunchAddToWhatsApp(val identifier: String, val packName: String) : CreateEvent

    /** The flow is done (added or saved); navigate away. */
    data object ExportComplete : CreateEvent
}

/** Bitmaps the editor canvas draws for the active sticker. Never mutate them. */
class ActiveCanvas(
    val source: android.graphics.Bitmap?,
    val mask: android.graphics.Bitmap?,
    val outline: android.graphics.Bitmap?
)

/** Geometry and limit constants shared by the editor and the exporter. */
object CreateSpec {
    /** Square working/exported canvas edge (WhatsApp sticker size). */
    const val CANVAS_SIZE = 512

    /** Content is fitted into this many px of the 512 canvas (transparent margin around). */
    const val EXPORT_CONTENT = 460

    /** White outline radius in canvas px (~8px after the 460/512 export fit). */
    const val OUTLINE_RADIUS = 9f

    const val MIN_STICKERS = 3
    const val MAX_STICKERS = 30

    const val STATIC_LIMIT_BYTES = 100 * 1024
    const val ANIMATED_LIMIT_BYTES = 500 * 1024
    const val TRAY_LIMIT_BYTES = 50 * 1024
    const val TRAY_SIZE = 96

    /** Animated clips are capped to their first 3 seconds, ~12 frames. */
    const val VIDEO_MAX_DURATION_MS = 3_000L
    const val VIDEO_MAX_FRAMES = 12

    const val TEXT_MAX_CHARS = 16
    const val NAME_MAX_CHARS = 30

    /** Brush slider position (1..3) → stroke diameter as a fraction of the canvas. */
    fun brushRadiusPx(brush: Int): Float {
        val fraction = when (brush.coerceIn(1, 3)) {
            1 -> 0.10f
            2 -> 0.15f
            else -> 0.20f
        }
        return CANVAS_SIZE * fraction / 2f
    }
}

/** Mapping from the session export machine to the shared AddBar visuals. */
fun AddVisualState.isBusy(): Boolean =
    this == AddVisualState.Downloading || this == AddVisualState.Sent
