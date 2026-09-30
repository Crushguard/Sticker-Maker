package com.piptechnologies.stickermaker.feature.namepack

import com.piptechnologies.stickermaker.feature.namepack.engine.Fit
import com.piptechnologies.stickermaker.feature.namepack.engine.LetteringFit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LetteringFitTest {

    private val halfEm: (String, Float) -> Float = { line, size -> line.length * size * 0.5f }

    private fun fit(text: String, w: Float, h: Float): Fit = LetteringFit.fit(text, w, h, halfEm)

    @Test
    fun aShortNameFitsOneLineAtTheStartSize() {
        assertEquals(Fit(82f, listOf("Sara"), true), fit("Sara", 300f, 100f))
    }

    @Test
    fun theStartSizeIsCappedAt128() {
        assertEquals(128f, fit("Hi", 400f, 200f).size)
    }

    @Test
    fun twoLinesAtALargerSizeBeatOneLineAtASmallerSize() {
        // Start floor(150 × 0.82) = 123, so sizes are odd. One line needs size ≤ 31.5;
        // "Love you," / "Anastasia" (4.5 × size wide) first fits at 65.
        val result = fit("Love you, Anastasia", 300f, 150f)
        assertEquals(Fit(65f, listOf("Love you,", "Anastasia"), true), result)
    }

    @Test
    fun theSplitWithTheNarrowestLongerLineWins() {
        // At 54 both "Hi my | love Sara" (9 chars) and "Hi my love | Sara" (10 chars) fit; 9 wins.
        assertEquals(Fit(54f, listOf("Hi my", "love Sara"), true), fit("Hi my love Sara", 280f, 120f))
    }

    @Test
    fun theFirstSplitWinsATie() {
        assertEquals(listOf("Good", "morning, Sara"), fit("Good morning, Sara", 200f, 120f).lines)
    }

    @Test
    fun sizesStepDownByTwoFromTheStart() {
        val result = fit("abcdefghijklmnop", 300f, 60f) // start floor(49.2) = 49, fits once 8s ≤ 300 → 37
        assertEquals(37f, result.size)
        assertEquals(1, result.lines.size)
    }

    @Test
    fun theFloorIsTriedEvenFromAnOddStart() {
        // Start floor(150 × 0.82) = 123 steps 21 → 19, past the floor; "aaaaaaaaaa" is 105 wide at 21, 100 at 20.
        assertEquals(Fit(20f, listOf("aaaaaaaaaa"), true), fit("aaaaaaaaaa", 103f, 150f))
    }

    @Test
    fun neverThreeLinesAndNothingIsClippedAtTheFloor() {
        val result = fit("Anastasia-Mari", 100f, 40f)
        assertEquals(Fit(20f, listOf("Anastasia-Mari"), false), result)
        assertFalse(fit("one two three four five six seven", 60f, 50f).ok)
    }

    @Test
    fun lineHeightIsOnePointZeroEightTimesTheSize() {
        assertEquals(108f, Fit(100f, listOf("x"), true).lineHeight, 0.001f)
    }
}
