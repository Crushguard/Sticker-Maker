package com.piptechnologies.stickermaker.feature.namepack

import com.piptechnologies.stickermaker.feature.namepack.engine.Character
import com.piptechnologies.stickermaker.feature.namepack.engine.Relation
import com.piptechnologies.stickermaker.feature.namepack.engine.Tone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NamePackStateTest {

    private val start = NamePackState()

    @Test
    fun startsOnYourNameWithMangoAndGirlfriend() {
        assertEquals(NameStep.YOU, start.step)
        assertEquals(Character.MANGO, start.character)
        assertEquals(Relation.GIRLFRIEND, start.relation)
        assertEquals(Tone.SWEET, start.tone)
    }

    @Test
    fun emojiInYourNameBlocksContinueAndShowsTheNote() {
        val s = NamePackReducer.typeYou(start, "Aymen 😍")
        assertEquals(NameNote.EMOJI, s.youNote)
        assertFalse(s.canContinue)
        assertEquals(NameStep.YOU, NamePackReducer.continueFromYou(s).step)
    }

    @Test
    fun anEmptyOwnNameContinuesAndSkipClearsItButKeepsTheCharacter() {
        assertEquals(NameStep.LOVE, NamePackReducer.continueFromYou(start).step)
        val typed = NamePackReducer.pickCharacter(NamePackReducer.typeYou(start, "Aymen"), Character.CAPY)
        val skipped = NamePackReducer.skipYou(typed)
        assertEquals("", skipped.you.value)
        assertEquals(Character.CAPY, skipped.character)
        assertEquals(NameStep.LOVE, skipped.step)
    }

    @Test
    fun makingWithoutTheirNameShowsTheErrorUntilTheyType() {
        val love = start.copy(step = NameStep.LOVE)
        val (errored, started) = NamePackReducer.make(love)
        assertFalse(started)
        assertTrue(errored.needNameError)
        assertEquals(NameNote.NEED_NAME, errored.loveNote)
        assertFalse(NamePackReducer.typeLove(errored, "S").needNameError)
    }

    @Test
    fun emojiInTheirNameMakesNothing() {
        val love = NamePackReducer.typeLove(start.copy(step = NameStep.LOVE), "Sara 😍")
        val (next, started) = NamePackReducer.make(love)
        assertFalse(started)
        assertEquals(NameStep.LOVE, next.step)
        assertEquals(NameNote.EMOJI, next.loveNote)
    }

    @Test
    fun aValidNameStartsBuilding() {
        val love = NamePackReducer.typeLove(start.copy(step = NameStep.LOVE), "  Sara ")
        val (next, started) = NamePackReducer.make(love)
        assertTrue(started)
        assertEquals(NameStep.BUILDING, next.step)
        assertEquals("Sara", next.love.value)
    }

    @Test
    fun theLimitNoteShowsAtFourteenAndAPasteIsClamped() {
        val s = NamePackReducer.typeLove(start.copy(step = NameStep.LOVE), "Anastasia-Maria")
        assertEquals("Anastasia-Mari", s.love.raw)
        assertEquals(14, s.love.count)
        assertEquals(NameNote.LIMIT, s.loveNote)
    }

    @Test
    fun pasteIsClamped() {
        assertEquals(14, NamePackReducer.typeYou(start, "x".repeat(50)).you.count)
    }

    @Test
    fun familyRelationsAreSweetOnly() {
        val flirty = NamePackReducer.pickTone(start, Tone.FLIRTY)
        val mom = NamePackReducer.pickRelation(flirty, Relation.MOM)
        assertEquals(Tone.SWEET, mom.effectiveTone)
        assertEquals(Tone.SWEET, NamePackReducer.pickTone(mom, Tone.FLIRTY).effectiveTone)
        assertEquals(Tone.FLIRTY, NamePackReducer.pickRelation(mom, Relation.CRUSH).effectiveTone)
    }

    @Test
    fun backWalksTheSteps() {
        assertNull(NamePackReducer.back(start))
        assertEquals(NameStep.YOU, NamePackReducer.back(start.copy(step = NameStep.LOVE))!!.step)
        assertEquals(NameStep.LOVE, NamePackReducer.back(start.copy(step = NameStep.BUILDING))!!.step)
        assertEquals(NameStep.LOVE, NamePackReducer.back(start.copy(step = NameStep.REVEAL))!!.step)
    }

    @Test
    fun savedStateRoundTrips() {
        val s = NamePackState(
            step = NameStep.LOVE, you = NameField("Aymen"), love = NameField("Sara"),
            relation = Relation.WIFE, character = Character.BUNNY, tone = Tone.FLIRTY
        )
        val saved = s.toSaved()
        assertEquals(s, namePackStateFrom { saved[it] })
    }

    @Test
    fun restoredRevealRebuilds() {
        val saved = start.copy(step = NameStep.REVEAL, love = NameField("Sara")).toSaved()
        assertEquals(NameStep.BUILDING, namePackStateFrom { saved[it] }.step)
    }

    @Test
    fun anEmptySavedStateIsTheStart() {
        assertEquals(start, namePackStateFrom { null })
    }

    @Test
    fun aSecondTapOnAFadingStepDoesNothing() {
        val building = NamePackReducer.typeLove(start.copy(step = NameStep.LOVE), "Sara").copy(step = NameStep.BUILDING)
        assertEquals(building to false, NamePackReducer.make(building))
        assertEquals(building, NamePackReducer.continueFromYou(building))
        val onLove = start.copy(step = NameStep.LOVE, you = NameField("Aymen"))
        assertEquals(onLove, NamePackReducer.continueFromYou(onLove))
        assertEquals(onLove, NamePackReducer.skipYou(onLove))
    }

    @Test
    fun keysTypedIntoYourNameAfterItLeftChangeNothing() {
        // The your-name field keeps focus while the step fades out: "Aymen" must not become "AymenS".
        val onLove = start.copy(step = NameStep.LOVE, you = NameField("Aymen"))
        assertEquals(onLove, NamePackReducer.typeYou(onLove, "AymenS"))
    }

    @Test
    fun keysTypedIntoTheirNameOffItsStepChangeNothing() {
        val onYou = start.copy(you = NameField("Aymen"))
        assertEquals(onYou, NamePackReducer.typeLove(onYou, "Sara"))
        val building = start.copy(step = NameStep.BUILDING, love = NameField("Sara"))
        assertEquals(building, NamePackReducer.typeLove(building, "SaraX"))
    }

    @Test
    fun aPastedNameLosesItsInvisibleMarks() {
        val sara = "\u0633\u0627\u0631\u0629"
        val s = NamePackReducer.typeLove(start.copy(step = NameStep.LOVE), "\u200F" + sara)
        assertEquals(sara, s.love.raw)
        assertEquals(NameNote.NONE, s.loveNote)
        assertTrue(s.canMake)
    }

    @Test
    fun aFinishedBuildFillsTheBarEvenWhenItsLastProgressLandsLate() {
        assertEquals(1f, buildProgress(done = 11, total = 12, finished = true, elapsedMs = 1_700, minMs = 1_700), 0f)
        assertEquals(0.5f, buildProgress(done = 6, total = 12, finished = false, elapsedMs = 5_000, minMs = 1_700), 0f)
        assertEquals(0.25f, buildProgress(done = 12, total = 12, finished = true, elapsedMs = 425, minMs = 1_700), 0.0001f)
    }
}
