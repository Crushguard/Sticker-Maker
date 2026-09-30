package com.piptechnologies.stickermaker.feature.create

import com.piptechnologies.stickermaker.feature.create.decor.DecorEditor
import com.piptechnologies.stickermaker.feature.create.decor.DecorSpec
import com.piptechnologies.stickermaker.feature.create.decor.FontMood
import com.piptechnologies.stickermaker.feature.create.decor.LayerContent
import com.piptechnologies.stickermaker.feature.create.decor.OutlineThickness
import com.piptechnologies.stickermaker.feature.create.decor.Size2
import com.piptechnologies.stickermaker.feature.create.decor.TextStyleId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorUiMapperTest {

    private var ids = 0L
    // Text 200 × 60, everything else 100 × 50 at scale 1.
    private val editor = DecorEditor(
        sizeOf = { c -> if (c is LayerContent.Text) Size2(200f, 60f) else Size2(100f, 50f) },
        subjectBox = { null },
        newId = { ++ids }
    )

    @Test
    fun noEditorLeavesTheDefaults() {
        assertEquals(CreateUiState(), CreateUiState().withDecor(null))
    }

    @Test
    fun layersCarryTheirRenderedSizeAndKind() {
        val decor = editor.add(LayerContent.Decor("doodles-1.webp", listOf("💕")))!!
        editor.beginGesture(decor)
        editor.pinch(decor, zoom = 1.5f, rotationDeg = 30f)
        editor.drag(decor, -2f, 40f)                       // x snaps back to the centre line
        var ui = CreateUiState().withDecor(editor)
        val moved = LayerUi(decor, LayerKind.Decor, 256f, 296f, 150f, 75f, 30f, flipped = false, behind = false)
        assertEquals(listOf(moved), ui.layers)
        assertEquals(decor, ui.selectedLayerId)
        assertTrue(ui.guideX)
        assertFalse(ui.guideY)
        editor.endGesture()
        editor.flip(decor)
        editor.toggleBehind(decor)
        editor.add(LayerContent.Emoji("red_heart.webp", "❤️"))
        ui = CreateUiState().withDecor(editor)
        assertFalse(ui.guideX)
        assertEquals(listOf(LayerKind.Decor, LayerKind.Emoji), ui.layers.map { it.kind })
        assertTrue(ui.layers[0].flipped && ui.layers[0].behind)
        assertTrue(ui.canUndo)
    }

    @Test
    fun theTextFieldFollowsTheSelectedTextLayerElseThePendingStyle() {
        editor.setTextStyle(TextStyleId.Classic)
        editor.setTextFont(FontMood.Hand)
        var ui = CreateUiState().withDecor(editor)
        assertEquals("", ui.textValue)
        assertEquals(TextStyleId.Classic, ui.textStyle)
        assertEquals(FontMood.Hand, ui.textFont)
        assertEquals(DecorSpec.ROSE, ui.textColour)
        assertTrue(editor.setText("miss u"))
        editor.setTextColour(DecorSpec.WHITE)
        ui = CreateUiState().withDecor(editor)
        assertEquals("miss u", ui.textValue)
        assertEquals(DecorSpec.WHITE, ui.textColour)
        assertEquals(LayerKind.Text, ui.layers.single().kind)
        editor.select(null)
        editor.setTextStyle(TextStyleId.Bubble)                // only the pending style now
        ui = CreateUiState().withDecor(editor)
        assertNull(ui.selectedLayerId)
        assertEquals("", ui.textValue)
        assertEquals(TextStyleId.Bubble, ui.textStyle)
    }

    @Test
    fun outlineAndPresetComeFromTheDecor() {
        editor.setOutlineThickness(OutlineThickness.Thick)
        editor.setPreset("heartbeat")
        val ui = CreateUiState().withDecor(editor)
        assertEquals(OutlineThickness.Thick, ui.outline.thickness)
        assertEquals("heartbeat", ui.preset)
    }
}
