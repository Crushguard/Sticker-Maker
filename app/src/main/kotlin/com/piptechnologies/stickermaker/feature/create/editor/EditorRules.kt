package com.piptechnologies.stickermaker.feature.create.editor

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import com.piptechnologies.stickermaker.R
import com.piptechnologies.stickermaker.feature.create.decor.MarkerSize
import com.piptechnologies.stickermaker.feature.create.decor.MotionMath
import com.piptechnologies.stickermaker.feature.create.decor.MotionPreset
import com.piptechnologies.stickermaker.feature.create.decor.OutlineStyle
import com.piptechnologies.stickermaker.feature.create.decor.OutlineThickness

// The small rules of the rows under the tool bar, free of Compose state so they can be unit-tested:
// the words for the data files' ids, how the Draw row and the Outline sub-row fit their card, the
// preset tiles' clock and pose, the note under them, and what an outline change fades from.

// ------------------------------------------------------------------ words

/**
 * The name of a text, marker or outline colour: `create_colour_<id>` for the ids of
 * `text-styles.json`; null for an id the app has no word for.
 */
@StringRes
internal fun colourLabel(id: String): Int? = when (id) {
    "white" -> R.string.create_colour_white
    "ink" -> R.string.create_colour_ink
    "rose" -> R.string.create_colour_rose
    "peach" -> R.string.create_colour_peach
    "gold" -> R.string.create_colour_gold
    "mint" -> R.string.create_colour_mint
    "sky" -> R.string.create_colour_sky
    "violet" -> R.string.create_colour_violet
    else -> null
}

/**
 * A motion preset's tile label: `create_preset_<id>` for the ids of `presets.json`; null for an id the app
 * has no word for.
 */
@StringRes
internal fun presetLabel(id: String): Int? = when (id) {
    "none" -> R.string.create_preset_none
    "heartbeat" -> R.string.create_preset_heartbeat
    "wiggle" -> R.string.create_preset_wiggle
    "bounce" -> R.string.create_preset_bounce
    "float" -> R.string.create_preset_float
    "jelly" -> R.string.create_preset_jelly
    "shake" -> R.string.create_preset_shake
    "hearts" -> R.string.create_preset_hearts
    "sparkle" -> R.string.create_preset_sparkle
    else -> null
}

/** What TalkBack calls a marker size: Small, Medium or Large. */
@StringRes
internal fun markerSizeLabel(size: MarkerSize): Int = when (size) {
    MarkerSize.S -> R.string.create_draw_small
    MarkerSize.M -> R.string.create_draw_medium
    MarkerSize.L -> R.string.create_draw_large
}

/** The label of an outline thickness: Thin, Medium or Thick. */
@StringRes
internal fun thicknessLabel(thickness: OutlineThickness): Int = when (thickness) {
    OutlineThickness.Thin -> R.string.create_outline_thin
    OutlineThickness.Medium -> R.string.create_outline_medium
    OutlineThickness.Thick -> R.string.create_outline_thick
}

// ---------------------------------------------------------------- Draw row

/** A swatch under the tool bar is this wide, and 7 dp from the next (spec §8). */
internal const val ROW_SWATCH_DP = 22f
internal const val SWATCH_GAP_DP = 7f

/** The Draw row has three size cells, 34 dp wide where the row has the room (spec §8). */
private const val SIZE_CELLS = 3
private const val SIZE_CELL_DP = 34f

/** A size cell never gets narrower than this: its largest dot is 13 dp. */
private const val MIN_SIZE_CELL_DP = 20f

/** What the size control is besides its cells: 2 dp of track on both sides and 2 dp between the cells. */
private const val SIZE_TRACK_DP = 2f * 2f + (SIZE_CELLS - 1) * 2f

/** How the Draw row fits its card, in dp: the gap after each swatch and the width of a size cell. */
internal data class DrawRowFit(val gap: Float, val cell: Float)

/**
 * The Draw row's fit in [room] dp (spec §8): [swatches] 22 dp swatches, 7 dp apart and 7 dp or more from
 * three 34 dp size cells. That row is wider than its card on a 390 dp phone, where the prototype's cells
 * shrink; here too the cells give way first, down to [MIN_SIZE_CELL_DP], and then the gaps close up.
 */
internal fun drawRowFit(room: Float, swatches: Int): DrawRowFit {
    // A gap after every swatch: between them, and before the size control.
    val gaps = swatches.coerceAtLeast(1)
    val fixed = swatches * ROW_SWATCH_DP + SIZE_TRACK_DP
    val cell = ((room - fixed - gaps * SWATCH_GAP_DP) / SIZE_CELLS).coerceIn(MIN_SIZE_CELL_DP, SIZE_CELL_DP)
    val gap = ((room - fixed - SIZE_CELLS * cell) / gaps).coerceIn(0f, SWATCH_GAP_DP)
    return DrawRowFit(gap, cell)
}

// ------------------------------------------------------------- Outline row

/**
 * How the Outline sub-row fits its card: the thickness cells' side padding in dp, and whether the
 * swatches share the control's line.
 */
internal enum class OutlineRowFit(val sidePadding: Float, val oneLine: Boolean) {
    /** The spec's sub-row: 10 dp padding, the swatches at the end of the line. */
    Full(10f, true),

    /** 6 dp padding on the same line: long labels, 360 dp phones. */
    Tight(6f, true),

    /** The swatches on a line of their own: where even that is too wide. */
    Stacked(10f, false)
}

/**
 * The Outline sub-row's width on one line (spec §8), in px: the thickness control ([labelWidths] in px,
 * each between [sidePadding] dp on both sides, 2 dp apart in a 2 dp track), 7 dp, and [swatches] 22 dp
 * swatches 7 dp apart. [dp] is px per dp.
 */
internal fun outlineRowWidth(labelWidths: List<Int>, swatches: Int, dp: Float, sidePadding: Float): Float {
    val cells = labelWidths.sum() + labelWidths.size * 2f * sidePadding * dp
    val control = cells + (labelWidths.size - 1).coerceAtLeast(0) * 2f * dp + 2f * 2f * dp
    if (swatches <= 0) return control
    return control + SWATCH_GAP_DP * dp + (swatches * ROW_SWATCH_DP + (swatches - 1) * SWATCH_GAP_DP) * dp
}

/** The first of [OutlineRowFit]'s steps whose sub-row ([labelWidths] in px, [dp] px per dp) fits [room] px. */
internal fun outlineRowFit(labelWidths: List<Int>, swatches: Int, dp: Float, room: Float): OutlineRowFit = when {
    outlineRowWidth(labelWidths, swatches, dp, OutlineRowFit.Full.sidePadding) <= room -> OutlineRowFit.Full
    outlineRowWidth(labelWidths, swatches, dp, OutlineRowFit.Tight.sidePadding) <= room -> OutlineRowFit.Tight
    else -> OutlineRowFit.Stacked
}

/**
 * The outline the live canvas crossfades from (spec §5) when a sticker that showed [shown] now shows
 * [outline]: [shown], after the switch or the colour changed. Null, for no fade, when nothing changed,
 * when neither is on, or when the thickness changed: the subject's silhouette is dilated again for the
 * new one, so that change snaps.
 */
internal fun outlineFadeFrom(shown: OutlineStyle?, outline: OutlineStyle): OutlineStyle? =
    shown?.takeIf { it != outline && it.thickness == outline.thickness && (it.on || outline.on) }

// ------------------------------------------------------------ Animate strip

/**
 * Where [preset]'s loop is at [clockMs] on the strip's shared clock, in ms: every tile loops at its own
 * length. 0 for a preset that doesn't move.
 */
internal fun tileTimeMs(preset: MotionPreset, clockMs: Long): Float =
    if (preset.isNone || preset.durationMs <= 0) 0f else (clockMs % preset.durationMs).toFloat()

/**
 * Puts [preset]'s pose [timeMs] into its loop on the layer of a preset tile's mini, [edge] px wide
 * (spec §7): the keyframe's scale and turn about the preset's pivot, and its shift. The layer around it
 * shrinks the mini by the preset's base scale about its centre; together they are the sticker's frame.
 */
internal fun GraphicsLayerScope.tilePose(preset: MotionPreset, timeMs: Float, edge: Float) {
    val pose = MotionMath.pose(preset, if (preset.durationMs > 0) timeMs / preset.durationMs else 0f)
    transformOrigin = TransformOrigin(preset.pivotX, preset.pivotY)
    scaleX = pose.scaleX
    scaleY = pose.scaleY
    rotationZ = pose.rotate
    translationX = pose.dx * edge
    translationY = pose.dy * edge
}

/** The note under the preset strip: [body], after [prefix] and a space when there is one. */
internal data class AnimateNote(@StringRes val prefix: Int?, @StringRes val body: Int)

/**
 * The note under the preset strip (spec §8): why presets are off on a [clip]; else the animated-pack
 * rule, which starts with how to play when motion is reduced ([reduceMotion]).
 */
internal fun animateNote(clip: Boolean, reduceMotion: Boolean): AnimateNote = when {
    clip -> AnimateNote(null, R.string.create_animate_note_clip)
    reduceMotion -> AnimateNote(R.string.create_animate_note_reduced, R.string.create_animate_note)
    else -> AnimateNote(null, R.string.create_animate_note)
}
