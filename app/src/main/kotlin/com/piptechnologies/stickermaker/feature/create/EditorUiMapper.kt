package com.piptechnologies.stickermaker.feature.create

import com.piptechnologies.stickermaker.feature.create.decor.DecorEditor
import com.piptechnologies.stickermaker.feature.create.decor.LayerContent

/**
 * This state with the active sticker's decor as the editor shows it (spec §3, §4, §8, §9): its
 * layers at their rendered size, the selection, the snap guides, the Undo state, the outline, the
 * preset and the Add › Text field. The field shows the selected text layer, else the style, colour
 * and font the next text layer gets. With no [editor] (no sticker yet) the state is unchanged.
 */
internal fun CreateUiState.withDecor(editor: DecorEditor?): CreateUiState {
    if (editor == null) return this
    val decor = editor.state
    val text = decor.layer(editor.selectedId)?.content as? LayerContent.Text
    val pending = editor.textDefaults
    return copy(
        canUndo = editor.canUndo,
        layers = decor.layers.map { layer ->
            val size = editor.renderedSize(layer)
            LayerUi(
                layer.id, kindOf(layer.content), layer.cx, layer.cy, size.w, size.h,
                layer.rotation, layer.flipped, layer.behind
            )
        },
        selectedLayerId = editor.selectedId,
        guideX = editor.guides.x,
        guideY = editor.guides.y,
        outline = decor.outline,
        preset = decor.preset,
        textValue = text?.text.orEmpty(),
        textStyle = text?.style ?: pending.style,
        textColour = text?.colour ?: pending.colour,
        textFont = text?.font ?: pending.font
    )
}

private fun kindOf(content: LayerContent): LayerKind = when (content) {
    is LayerContent.Text -> LayerKind.Text
    is LayerContent.Emoji -> LayerKind.Emoji
    is LayerContent.Decor -> LayerKind.Decor
    is LayerContent.Drawing -> LayerKind.Drawing
}
