package com.piptechnologies.stickermaker.feature.create.decor

/** A point in 512 canvas px. */
data class Pt(val x: Float, val y: Float)

/** One marker stroke; [points] are canvas px while live, and relative to the layer centre once flushed. */
data class MarkerStroke(val points: List<Pt>, val colour: Int, val size: MarkerSize)

/** What a layer shows: text, emoji, decoration piece or drawing. */
sealed interface LayerContent {
    data class Text(val text: String, val style: TextStyleId, val colour: Int, val font: FontMood) : LayerContent
    data class Emoji(val file: String, val glyph: String, val tone: SkinTone = SkinTone.Default) : LayerContent
    data class Decor(val file: String, val emojis: List<String>) : LayerContent
    data class Drawing(val strokes: List<MarkerStroke>) : LayerContent
}

/** One layer; [scale] multiplies the content's base size (spec §3). */
data class Layer(
    val id: Long,
    val content: LayerContent,
    val cx: Float,
    val cy: Float,
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val flipped: Boolean = false,
    val behind: Boolean = false
)

/** The die-cut outline: on/off, thickness, colour as ARGB. */
data class OutlineStyle(
    val on: Boolean = true,
    val thickness: OutlineThickness = OutlineThickness.Medium,
    val colour: Int = DecorSpec.WHITE
)

/** Everything decorating one sticker. Live Draw strokes are kept by [DecorEditor], outside the undo snapshots. */
data class DecorState(
    val layers: List<Layer> = emptyList(),
    val outline: OutlineStyle = OutlineStyle(),
    val preset: String = MotionPreset.NONE
) {
    val animated: Boolean get() = preset != MotionPreset.NONE
    fun layer(id: Long?): Layer? = layers.firstOrNull { it.id == id }
}

/** Width/height in 512 canvas px. */
data class Size2(val w: Float, val h: Float)

/** Axis-aligned box in 512 canvas px. */
data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val cx: Float get() = (left + right) / 2f
    val cy: Float get() = (top + bottom) / 2f
}

/** Style, colour and font used for the next new text layer. */
data class TextDefaults(
    val style: TextStyleId = TextStyleId.Sticker,
    val colour: Int = DecorSpec.ROSE,
    val font: FontMood = FontMood.Round
)

/** Whether the centre snap guides show on the x and y axes. */
data class Guides(val x: Boolean, val y: Boolean) { companion object { val NONE = Guides(false, false) } }

/** Numbers from the handoff (spec §3). */
object DecorSpec {
    const val CANVAS = 512f
    const val MAX_LAYERS = 8
    /** The floor: a layer's longer side stays at 10% of the canvas or more (spec §3's 10–100%, on the longer side). */
    const val MIN_SIDE = 0.10f
    /** Emoji stop at half the canvas on their longer side: the art is 256 px. */
    const val EMOJI_MAX_SIDE = 0.5f
    const val EMOJI_WIDTH = 0.30f
    const val SNAP_PX = 6f
    const val ROTATION_SNAP_DEG = 4f
    const val DUPLICATE_OFFSET = 0.08f
    const val FIRST_TEXT_Y = 0.87f
    const val TEXT_MAX_CHARS = 30
    const val HIT_SLOP_PX = 12f
    const val ROSE = 0xFFC23359.toInt()
    const val WHITE = 0xFFFFFFFF.toInt()
}
