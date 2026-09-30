package com.piptechnologies.stickermaker.feature.create.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.piptechnologies.stickermaker.R
import com.piptechnologies.stickermaker.feature.create.AddTab
import com.piptechnologies.stickermaker.feature.create.decor.Affine
import com.piptechnologies.stickermaker.feature.create.decor.DecorCatalog
import com.piptechnologies.stickermaker.feature.create.decor.DecorSpec
import com.piptechnologies.stickermaker.feature.create.decor.EmojiCatalog
import com.piptechnologies.stickermaker.feature.create.decor.FontFile
import com.piptechnologies.stickermaker.feature.create.decor.FontMood
import com.piptechnologies.stickermaker.feature.create.decor.MotionBook
import com.piptechnologies.stickermaker.feature.create.decor.MotionMath
import com.piptechnologies.stickermaker.feature.create.decor.MotionPreset
import com.piptechnologies.stickermaker.feature.create.decor.OutlineStyle
import com.piptechnologies.stickermaker.feature.create.decor.OutlineThickness
import com.piptechnologies.stickermaker.feature.create.decor.SkinTone
import com.piptechnologies.stickermaker.feature.create.decor.TextStyleBook
import com.piptechnologies.stickermaker.feature.create.decor.TextStyleId
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The rules of the rows under the tool bar and of the Add sheet, on the data files the app ships. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class EditorRulesTest {

    private fun asset(path: String) = File("src/main/assets/$path").readText()

    private val presets = MotionBook.parse(asset("motion/presets.json")).presets
    private val styles = TextStyleBook.parse(asset("text/text-styles.json"))

    // --------------------------------------------------------------- words

    @Test
    fun everyShippedColourHasAName() {
        assertEquals(8, styles.colours.size)
        assertEquals(5, styles.outlineColours.size)
        (styles.colours + styles.outlineColours).forEach { assertNotNull(it.id, colourLabel(it.id)) }
        assertEquals("eight colours, eight names", 8, styles.colours.map { colourLabel(it.id) }.toSet().size)
        assertEquals(R.string.create_colour_rose, colourLabel("rose"))
        assertEquals(R.string.create_colour_sky, colourLabel("sky"))
        assertNull("an id the app has no word for", colourLabel("teal"))
    }

    @Test
    fun everyShippedPresetHasALabel() {
        assertEquals(9, presets.size)
        assertEquals("nine presets, nine labels", 9, presets.map { presetLabel(it.id) }.toSet().size)
        presets.forEach { assertNotNull(it.id, presetLabel(it.id)) }
        assertEquals(R.string.create_preset_none, presetLabel(MotionPreset.NONE))
        assertEquals(R.string.create_preset_heartbeat, presetLabel("heartbeat"))
        assertNull(presetLabel("spin"))
    }

    // ------------------------------------------------------------ Draw row

    /** What the fitted row takes: the swatches, a gap after each, and the size control. */
    private fun drawRowWidth(fit: DrawRowFit, swatches: Int = 8) =
        swatches * 22f + swatches * fit.gap + 3 * fit.cell + 8f

    @Test
    fun theDrawRowKeepsTheSpecWhereTheCardIsWideEnough() {
        // A 412 dp phone: the card is 372, less 12 dp on both sides.
        val fit = drawRowFit(room = 348f, swatches = 8)
        assertEquals(DrawRowFit(gap = 7f, cell = 34f), fit)
        assertTrue("what is left is the flexible space", drawRowWidth(fit) <= 348f)
    }

    @Test
    fun theDrawRowNarrowsItsSizeCellsBeforeItsGaps() {
        // The 390 dp reference: 8 swatches, 8 gaps and three 34 dp cells are 350 dp; the row has 326.
        val reference = drawRowFit(room = 326f, swatches = 8)
        assertEquals(7f, reference.gap, 0f)
        assertEquals((326f - 176f - 56f - 8f) / 3f, reference.cell, 1e-3f)
        assertEquals(326f, drawRowWidth(reference), 1e-3f)
        // A 360 dp phone: the cells stop at 20 dp and the gaps close up a little.
        val narrow = drawRowFit(room = 296f, swatches = 8)
        assertEquals(20f, narrow.cell, 0f)
        assertEquals(6.5f, narrow.gap, 1e-3f)
        assertEquals(296f, drawRowWidth(narrow), 1e-3f)
        // A 320 dp phone still fits, swatch to swatch.
        val small = drawRowFit(room = 256f, swatches = 8)
        assertEquals(20f, small.cell, 0f)
        assertEquals(1.5f, small.gap, 1e-3f)
        // Narrower than the swatches and the smallest control: nothing goes below its floor.
        assertEquals(DrawRowFit(gap = 0f, cell = 20f), drawRowFit(room = 100f, swatches = 8))
    }

    // --------------------------------------------------------- Outline row

    /** Thin · Medium · Thick in the UI font at 12 sp, in px at 1 px per dp. */
    private val english = listOf(23, 43, 29)

    @Test
    fun theOutlineSubRowIsTheControlAGapAndTheSwatches() {
        // 95 of labels, 3 × 20 of padding, 2 gaps of 2 and the track's 4; 7; 5 × 22 and 4 × 7.
        assertEquals(163f + 7f + 138f, outlineRowWidth(english, swatches = 5, dp = 1f, sidePadding = 10f), 1e-3f)
        val tripled = outlineRowWidth(english.map { it * 3 }, swatches = 5, dp = 3f, sidePadding = 10f)
        assertEquals("px scale with the density", 3f * 308f, tripled, 1e-3f)
        val bare = outlineRowWidth(english, swatches = 0, dp = 1f, sidePadding = 10f)
        assertEquals("no swatches before the data loads", 163f, bare, 1e-3f)
    }

    @Test
    fun theOutlineSubRowTightensThenStacks() {
        // The 390 dp reference card has 322 dp inside; a 360 dp phone 292.
        assertEquals(OutlineRowFit.Full, outlineRowFit(english, swatches = 5, dp = 1f, room = 322f))
        assertEquals(OutlineRowFit.Full, outlineRowFit(english, swatches = 5, dp = 1f, room = 308f))
        assertEquals(OutlineRowFit.Tight, outlineRowFit(english, swatches = 5, dp = 1f, room = 292f))
        assertEquals(OutlineRowFit.Stacked, outlineRowFit(english, swatches = 5, dp = 1f, room = 280f))
        assertTrue(OutlineRowFit.Full.oneLine && OutlineRowFit.Tight.oneLine)
        assertFalse(OutlineRowFit.Stacked.oneLine)
        // Longer words keep one line at 390 only with the tight padding: 120 + 3 × 12 + 8 + 145 is 309.
        val longer = listOf(40, 40, 40)
        assertEquals(OutlineRowFit.Tight, outlineRowFit(longer, swatches = 5, dp = 1f, room = 322f))
        assertEquals(OutlineRowFit.Stacked, outlineRowFit(longer, swatches = 5, dp = 1f, room = 292f))
    }

    @Test
    fun onlyTheSwitchAndTheColourFade() {
        val white = OutlineStyle()
        val rose = white.copy(colour = DecorSpec.ROSE)
        assertNull("the first outline shown", outlineFadeFrom(null, white))
        assertNull("nothing changed", outlineFadeFrom(white, white))
        assertEquals("a colour", white, outlineFadeFrom(white, rose))
        assertEquals("switched off", rose, outlineFadeFrom(rose, rose.copy(on = false)))
        assertEquals("switched on", white.copy(on = false), outlineFadeFrom(white.copy(on = false), white))
        assertNull("a thickness snaps", outlineFadeFrom(white, white.copy(thickness = OutlineThickness.Thick)))
        assertNull("off either way", outlineFadeFrom(white.copy(on = false), rose.copy(on = false)))
    }

    @Test
    fun anOutlineFadeStartsOnAChangeOfTheSameSticker() {
        val white = OutlineStyle()
        val rose = white.copy(colour = DecorSpec.ROSE)
        val fade = OutlineFade()
        assertFalse("the first outline shows at once", fade.show(0, white, snap = false))
        assertNull(fade.from)
        assertEquals(1f, fade.blend, 0f)

        assertTrue(fade.show(0, rose, snap = false))
        assertEquals(white, fade.from)
        assertEquals("the old outline first", 0f, fade.blend, 0f)
        assertFalse("the same outline again changes nothing", fade.show(0, rose, snap = false))
        assertEquals(white, fade.from)
        assertEquals(0f, fade.blend, 0f)

        assertFalse("another sticker shows its own at once", fade.show(1, white, snap = false))
        assertNull(fade.from)
        assertEquals(1f, fade.blend, 0f)

        assertFalse("a thickness snaps", fade.show(1, white.copy(thickness = OutlineThickness.Thin), snap = false))
        assertFalse("reduced motion snaps", fade.show(1, rose, snap = true))
        assertNull(fade.from)
        assertEquals(1f, fade.blend, 0f)

        assertTrue("back on that sticker, from what it showed", fade.show(1, rose.copy(on = false), snap = false))
        assertEquals(rose, fade.from)
    }

    // ------------------------------------------------------- Animate strip

    @Test
    fun everyTileLoopsAtItsOwnLength() {
        val heartbeat = presets.first { it.id == "heartbeat" }
        val float = presets.first { it.id == "float" }
        assertEquals(1000, heartbeat.durationMs)
        assertEquals(2000, float.durationMs)
        assertEquals(0f, tileTimeMs(heartbeat, 0L), 0f)
        assertEquals(250f, tileTimeMs(heartbeat, 1250L), 0f)
        assertEquals(1250f, tileTimeMs(float, 1250L), 0f)
        assertEquals("exact after hours on the clock", 123f, tileTimeMs(heartbeat, 36_000_123L), 0f)
        assertEquals("None stays at rest", 0f, tileTimeMs(presets.first { it.isNone }, 1250L), 0f)
    }

    /** What a layer with [scope]'s values does to an [edge]-px box: scale and turn about the pivot, then shift. */
    private fun layerMap(scope: GraphicsLayerScope, edge: Float): Affine {
        val px = scope.transformOrigin.pivotFractionX * edge
        val py = scope.transformOrigin.pivotFractionY * edge
        return Affine.translate(scope.translationX, scope.translationY) * Affine.translate(px, py) *
            Affine.rotate(scope.rotationZ) * Affine.scale(scope.scaleX, scope.scaleY) * Affine.translate(-px, -py)
    }

    @Test
    fun aTilesTwoLayersAreTheStickersFrame() {
        val edge = 132f
        val corners =
            listOf(Offset.Zero, Offset(edge, 0f), Offset(edge, edge), Offset(0f, edge), Offset(edge / 2f, edge / 3f))
        presets.forEach { preset ->
            listOf(0f, 0.14f, 0.3f, 0.5f, 0.77f).forEach { t ->
                val pose = GraphicsLayerScope().apply { tilePose(preset, t * preset.durationMs, edge) }
                val base = GraphicsLayerScope().apply {
                    scaleX = preset.baseScale
                    scaleY = preset.baseScale
                }
                // The mini's layer sits inside the base-scale layer, which turns about its centre.
                val tile = layerMap(base, edge) * layerMap(pose, edge)
                val frame = MotionMath.transform(preset, t, canvas = edge)
                corners.forEach { p ->
                    val expected = frame.mapPoint(p)
                    val actual = tile.mapPoint(p)
                    assertEquals("${preset.id} at $t: x of $p", expected.x, actual.x, 0.01f)
                    assertEquals("${preset.id} at $t: y of $p", expected.y, actual.y, 0.01f)
                }
            }
        }
    }

    @Test
    fun theTilePoseIsAtRestOnFrameZero() {
        presets.forEach { preset ->
            val scope = GraphicsLayerScope().apply { tilePose(preset, 0f, 132f) }
            assertEquals(preset.id, 1f, scope.scaleX, 0f)
            assertEquals(preset.id, 1f, scope.scaleY, 0f)
            assertEquals(preset.id, 0f, scope.rotationZ, 0f)
            assertEquals(preset.id, 0f, scope.translationX, 0f)
            assertEquals(preset.id, 0f, scope.translationY, 0f)
        }
        val bounce = presets.first { it.id == "bounce" }
        val up = GraphicsLayerScope().apply { tilePose(bounce, 350f, 100f) }
        assertEquals("bounce turns about its foot", 1f, up.transformOrigin.pivotFractionY, 0f)
        assertEquals("7% of the mini up at the top of the bounce", -7f, up.translationY, 0.01f)
    }

    @Test
    fun theNoteSaysWhyAStripIsOffOrHowToPlay() {
        assertEquals(AnimateNote(null, R.string.create_animate_note), animateNote(clip = false, reduceMotion = false))
        assertEquals(
            AnimateNote(R.string.create_animate_note_reduced, R.string.create_animate_note),
            animateNote(clip = false, reduceMotion = true)
        )
        val clipNote = AnimateNote(null, R.string.create_animate_note_clip)
        assertEquals(clipNote, animateNote(clip = true, reduceMotion = false))
        assertEquals("a clip's presets are off either way", clipNote, animateNote(clip = true, reduceMotion = true))
    }

    // ----------------------------------------------------------- Add sheet

    @Test
    fun everyControlOfTheSheetHasItsOwnWord() {
        assertEquals(3, AddTab.entries.map(::addTabLabel).toSet().size)
        assertEquals(4, TextStyleId.entries.map(::textStyleLabel).toSet().size)
        assertEquals(3, FontMood.entries.map(::fontMoodLabel).toSet().size)
        assertEquals(6, SkinTone.entries.map(::skinToneLabel).toSet().size)
        assertEquals(R.string.create_skin_medium_light, skinToneLabel(SkinTone.MediumLight))
        assertEquals(R.string.create_text_style_stroke, textStyleLabel(TextStyleId.Stroke))
    }

    @Test
    fun fontChipsAreLetteredInTheirOwnLatinFace() {
        assertEquals(FontFile.BALOO, fontMoodFace(FontMood.Round))
        assertEquals(FontFile.CAVEAT, fontMoodFace(FontMood.Hand))
        assertEquals(FontFile.LILITA, fontMoodFace(FontMood.Display))
        assertEquals(listOf(13f, 15f, 13f), FontMood.entries.map(::fontMoodSp))
    }

    @Test
    fun recentIsTheLastChipOnceSomethingWasUsed() {
        val categories = EmojiCatalog.parse(asset("emoji/subset.json")).tabs.keys - EmojiCatalog.LOVE
        assertEquals("the chips are the grid's tabs, Love aside", categories.toList(), emojiChips(hasRecents = false))
        val used = emojiChips(hasRecents = true)
        assertEquals(EmojiCatalog.CATEGORIES + EMOJI_RECENT, used)
        assertEquals(EMOJI_RECENT, used.last())
        used.forEach { assertNotNull(it, emojiTabLabel(it)) }
        assertEquals("seven chips, seven labels", 7, used.map(::emojiTabLabel).toSet().size)
        assertNull(emojiTabLabel("flags"))
    }

    @Test
    fun theGridsCanKeyTheirCellsByFile() {
        val emoji = EmojiCatalog.parse(asset("emoji/subset.json"))
        emoji.tabs.forEach { (tab, items) ->
            assertEquals("$tab has an emoji twice", items.size, items.map { it.file }.toSet().size)
        }
        val pieces = DecorCatalog.parse(asset("decor/decor.json")).pieces
        assertEquals("a decoration piece is listed twice", pieces.size, pieces.map { it.file }.toSet().size)
    }

    @Test
    fun theChipThatIsOnFallsBackToTheFirst() {
        val fresh = emojiChips(hasRecents = false)
        assertEquals("hands", emojiChipOn("hands", fresh))
        assertEquals("Recent before anything was used", "smileys", emojiChipOn(EMOJI_RECENT, fresh))
        assertEquals(EMOJI_RECENT, emojiChipOn(EMOJI_RECENT, emojiChips(hasRecents = true)))
    }

    @Test
    fun theSkinPopoverIsStartAlignedAndItsTailPointsAtTheEmoji() {
        // The 390 dp reference in dp: a 358 grid of six 54.67 cells 6 apart; the card is 246 wide.
        val cell = (358f - 5 * 6f) / 6f
        fun column(n: Int) = (n - 1) * (cell + 6f) + cell / 2f
        fun place(centre: Float) = skinPopoverPlacement(centre, room = 358f, width = 246f, inset = 4f, corner = 22f)

        val first = place(column(1))
        assertEquals(4f, first.start, 0f)
        assertEquals(column(1) - 4f, first.tail, 1e-3f)
        val fourth = place(column(4))
        assertEquals("still start-aligned", 4f, fourth.start, 0f)
        assertEquals(column(4) - 4f, fourth.tail, 1e-3f)
        // Past the card's end, the card slides after the emoji; the tail stays at its last place.
        val fifth = place(column(5))
        assertEquals(column(5) - 224f, fifth.start, 1e-3f)
        assertEquals(224f, fifth.tail, 1e-3f)
        val sixth = place(column(6))
        assertTrue("the card stays 4 dp inside the grid", sixth.start + 246f <= 358f - 4f)
        assertEquals(column(6), sixth.start + sixth.tail, 1e-3f)

        assertEquals("the tail keeps off the card's corner", PopoverPlacement(4f, 22f), place(10f))
        val narrow = skinPopoverPlacement(200f, room = 240f, width = 246f, inset = 4f, corner = 22f)
        assertEquals("a grid narrower than the card: start-aligned", 4f, narrow.start, 0f)
    }

    @Test
    fun twoGroupsSitAtTheEndsOfALineAndOneAtItsStart() {
        val density = Density(1f)
        fun arrange(sizes: IntArray, direction: LayoutDirection) = IntArray(sizes.size).also { out ->
            with(EndsApart) { density.arrange(100, sizes, direction, out) }
        }
        assertEquals(7.dp, EndsApart.spacing)
        assertArrayEquals(intArrayOf(0, 60), arrange(intArrayOf(30, 40), LayoutDirection.Ltr))
        assertArrayEquals(intArrayOf(0), arrange(intArrayOf(30), LayoutDirection.Ltr))
        assertArrayEquals("mirrored", intArrayOf(70, 0), arrange(intArrayOf(30, 40), LayoutDirection.Rtl))
        val wrapped = arrange(intArrayOf(30), LayoutDirection.Rtl)
        assertArrayEquals("a wrapped group starts at the right", intArrayOf(70), wrapped)
    }

    @Test
    fun theContentEndsWhereTheSheetStandsOverTheFooter() {
        assertEquals(200, sheetOverlap(sheetHeight = 300, footerHeight = 100))
        assertEquals("closed, or still sliding up", 0, sheetOverlap(sheetHeight = 0, footerHeight = 100))
        val low = sheetOverlap(sheetHeight = 80, footerHeight = 100)
        assertEquals("a sheet lower than the footer covers none of it", 0, low)
    }
}
