package com.piptechnologies.stickermaker.feature.create

import android.content.Intent
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import com.piptechnologies.stickermaker.core.design.components.AddVisualState
import com.piptechnologies.stickermaker.core.ui.UiText
import com.piptechnologies.stickermaker.feature.create.decor.DecorSpec
import com.piptechnologies.stickermaker.feature.create.decor.EmojiCatalog
import com.piptechnologies.stickermaker.feature.create.decor.FontMood
import com.piptechnologies.stickermaker.feature.create.decor.MarkerSize
import com.piptechnologies.stickermaker.feature.create.decor.MotionPreset
import com.piptechnologies.stickermaker.feature.create.decor.OutlineStyle
import com.piptechnologies.stickermaker.feature.create.decor.SkinTone
import com.piptechnologies.stickermaker.feature.create.decor.TextStyleId

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
 * Editor tools from the dark tool bar (spec §8). Zoom is not listed: it is a
 * button on the canvas card that never changes the current tool, it only
 * scales the canvas.
 */
enum class EditorTool { Auto, Brush, Erase, Add, Draw, Animate }

/** The Add sheet's tabs (spec §9). */
enum class AddTab { Text, Emoji, Stickers }

/** What a layer shows, as the overlay needs it (only text layers get the edit handle). */
enum class LayerKind { Text, Emoji, Decor, Drawing }

/** A skin-tone cell: its art is there ([Ready]), downloading ([Loading]) or couldn't be fetched ([Failed]). */
enum class ToneState { Ready, Loading, Failed }

/**
 * One layer of the active sticker for the overlay, in 512 canvas px: its centre, its rendered
 * size (base size × [scale]), its [scale] (the corner handle resizes from it), its rotation in
 * degrees clockwise, and whether it is mirrored or drawn behind the cut-out.
 */
@Immutable
data class LayerUi(
    val id: Long,
    val kind: LayerKind,
    val cx: Float,
    val cy: Float,
    val width: Float,
    val height: Float,
    val scale: Float,
    val rotation: Float,
    val flipped: Boolean,
    val behind: Boolean
)

/** One cell of the skin-tone popover; [model] is what Coil loads (an asset URL or the downloaded file). */
@Immutable
data class ToneCellUi(val tone: SkinTone, val state: ToneState, val model: Any?)

/** The skin-tone popover over the emoji [file]: the bundled Default first, then the five downloadable tones. */
@Immutable
data class SkinPopoverUi(val file: String, val cells: List<ToneCellUi>)

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
    /** Small preview of the decorated sticker (outline, layers and cut subject) once cut. */
    val stickerThumb: ImageBitmap?,
    val cut: CutStatus,
    /** Whether a motion preset moves this sticker (the rail's ANIM badge; clips move anyway). */
    val animated: Boolean = false
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
    val activeCut: CutStatus = CutStatus.None,
    val activeDurationLabel: String? = null,
    /** The active sticker is a clip: presets are off (spec §7). */
    val activeIsVideo: Boolean = false,
    /** The active sticker's undo stack has a step (spec §4). */
    val canUndo: Boolean = false,
    val anyPending: Boolean = false,
    /** Bumped whenever something the canvas draws changed; invalidates the live canvas. */
    val editorTick: Int = 0,
    /** The decor data and renderer are loaded: layers can be added and the scene drawn. */
    val dataReady: Boolean = false,
    /**
     * The decor data has loaded and the active sticker's cut-out is done, so Add, Draw and Animate can
     * open (the tool bar dims them otherwise; the view model refuses them, and a rail switch onto a
     * sticker still being cut returns to Auto).
     */
    val layerToolsEnabled: Boolean = false,
    // Decor of the active sticker (spec §3-§8)
    val layers: List<LayerUi> = emptyList(),
    val selectedLayerId: Long? = null,
    /** The layer a drag, pinch or handle gesture is moving; the canvas draws it from its nearest cached size. */
    val liveLayerId: Long? = null,
    /** The centre snap guides: vertical (x = 256) and horizontal (y = 256). */
    val guideX: Boolean = false,
    val guideY: Boolean = false,
    val outline: OutlineStyle = OutlineStyle(),
    val preset: String = MotionPreset.NONE,
    /** With reduced motion, the canvas plays one loop of the preset while this is set. */
    val playing: Boolean = false,
    // Add sheet (spec §9)
    val addTab: AddTab = AddTab.Text,
    /** The Emoji tab's category chip: a category id, or `recent`. */
    val emojiTab: String = EmojiCatalog.CATEGORIES.first(),
    /** Recently picked emoji files, most recent first. */
    val recents: List<String> = emptyList(),
    val skinPopover: SkinPopoverUi? = null,
    /** The Text field: the selected text layer's text, else empty (typing then adds a layer). */
    val textValue: String = "",
    /** The selected text layer's style, colour and font, else those the next text layer gets. */
    val textStyle: TextStyleId = TextStyleId.Sticker,
    val textColour: Int = DecorSpec.ROSE,
    val textFont: FontMood = FontMood.Round,
    // Draw
    val drawColour: Int = DecorSpec.ROSE,
    val drawSize: MarkerSize = MarkerSize.M,
    // Pack details
    val trayIndex: Int = 0,
    val packName: String = "",
    /** Any sticker is a clip or has a motion preset: the pack is animated (spec §7 pack kind). */
    val animatedPack: Boolean = false,
    val exportState: AddVisualState = AddVisualState.Idle,
    val exportProgress: Float = 0f
) {
    /** The stickers of the pack, in pick order (editor + details rails). */
    val selectedItems: List<CreateItemUi> get() = items.filter { it.selected }
}

/** One-shot effects the screens react to. */
sealed interface CreateEvent {
    data class ShowToast(val message: UiText, val check: Boolean = false) : CreateEvent

    /** Export finished and the pack is saved; fire WhatsApp's add [intent] (resolved off the main thread). */
    data class LaunchAddToWhatsApp(val intent: Intent) : CreateEvent

    /** WhatsApp went away since Add was tapped: show the install sheet. */
    data object ShowNoWhatsApp : CreateEvent

    /** The flow is done (added or saved); navigate away. */
    data object ExportComplete : CreateEvent
}

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
