package com.piptechnologies.stickermaker.core.design

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The icons' path data, as Compose reads it. Its parser wants an arc's two flags apart ("1 0 8.653"):
 * written together ("108.653"), as minified SVG allows, the arc is dropped without a word and the icon
 * loses that stroke.
 */
class LoveIconsTest {

    private fun icons(): List<ImageVector> = LoveIcons::class.java.declaredMethods
        .filter { Modifier.isPublic(it.modifiers) && it.parameterCount == 0 }
        .filter { it.returnType == ImageVector::class.java }
        .map { it.invoke(LoveIcons) as ImageVector }

    private fun strokes(group: VectorGroup): List<VectorPath> = group.flatMap { node ->
        when (node) {
            is VectorPath -> listOf(node)
            is VectorGroup -> strokes(node)
        }
    }

    private fun PathNode.moves() = this is PathNode.MoveTo || this is PathNode.RelativeMoveTo

    @Test
    fun everyStrokeOfEveryIconDraws() {
        val icons = icons()
        assertTrue("only ${icons.size} icons found", icons.size >= 40)
        for (icon in icons) {
            val strokes = strokes(icon.root)
            assertTrue("${icon.name} has no stroke", strokes.isNotEmpty())
            for (stroke in strokes) {
                val draws = stroke.pathData.any { !it.moves() && it !is PathNode.Close }
                assertTrue("${icon.name}: a stroke moves and draws nothing", draws)
            }
        }
    }

    @Test
    fun theAddToolsFaceKeepsItsArcs() {
        val arcs = strokes(LoveIcons.SmilePlus.root).map { stroke ->
            stroke.pathData.count { it is PathNode.RelativeArcTo }
        }
        // The open circle of the face, an eye, the plus's bar, the smile, the plus's stem, the other eye.
        assertEquals(listOf(1, 0, 0, 1, 0, 0), arcs)
    }
}
