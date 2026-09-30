package com.piptechnologies.stickermaker.feature.create.decor

/** One undoable touch (spec §4). */
sealed interface EditStep {
    /** A Brush or Erase stroke; the view model removes the item's last mask stroke. */
    data object MaskStroke : EditStep
    /** A Draw stroke that is still live. */
    data object LiveStroke : EditStep
    /** Any decor change; undo restores [before]. */
    data class Decor(val before: DecorState) : EditStep
}

/** One sticker's undo stack. */
class EditHistory {
    private val steps = ArrayDeque<EditStep>()
    val isEmpty: Boolean get() = steps.isEmpty()
    fun push(step: EditStep) = steps.addLast(step)
    fun pop(): EditStep? = steps.removeLastOrNull()
    /** The cut-out re-ran and reset its strokes. */
    fun removeMaskStrokes() { steps.removeAll { it == EditStep.MaskStroke } }
    /** Live strokes became a layer (one [EditStep.Decor] replaces them). */
    fun removeLiveStrokes() { steps.removeAll { it == EditStep.LiveStroke } }
    /** Remove the last step if it's a no-op [Decor] step (before == current). */
    fun dropIfUnchanged(current: DecorState) {
        val last = steps.lastOrNull()
        if (last is EditStep.Decor && last.before == current) {
            steps.removeLast()
        }
    }
}
