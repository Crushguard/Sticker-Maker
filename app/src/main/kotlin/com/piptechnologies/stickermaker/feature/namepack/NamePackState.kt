package com.piptechnologies.stickermaker.feature.namepack

import com.piptechnologies.stickermaker.feature.namepack.engine.Character
import com.piptechnologies.stickermaker.feature.namepack.engine.NameInput
import com.piptechnologies.stickermaker.feature.namepack.engine.Relation
import com.piptechnologies.stickermaker.feature.namepack.engine.Tone
import kotlin.math.min

/** The four steps of the name flow, one route. */
enum class NameStep { YOU, LOVE, BUILDING, REVEAL }

/** The note under a name field, by priority: emoji, missing name, limit. */
enum class NameNote { NONE, EMOJI, NEED_NAME, LIMIT }

/** A name field as typed ([raw], already clamped) and what the rules make of it. */
data class NameField(val raw: String = "") {
    val value: String get() = NameInput.normalize(raw)
    val count: Int get() = NameInput.graphemeCount(raw)
    val atLimit: Boolean get() = count >= NameInput.MAX_GRAPHEMES
    val hasEmoji: Boolean get() = !NameInput.isLetterable(value)
    val isBlank: Boolean get() = value.isEmpty()
}

/** Everything the steps decide on. Rendered stickers live in the ViewModel, not here. */
data class NamePackState(
    val step: NameStep = NameStep.YOU,
    val you: NameField = NameField(),
    val love: NameField = NameField(),
    val relation: Relation = Relation.GIRLFRIEND,
    val character: Character = Character.MANGO,
    val tone: Tone = Tone.SWEET,
    val needNameError: Boolean = false,
) {
    val effectiveTone: Tone get() = if (relation.family) Tone.SWEET else tone

    val youNote: NameNote
        get() = when {
            you.hasEmoji -> NameNote.EMOJI
            you.atLimit -> NameNote.LIMIT
            else -> NameNote.NONE
        }

    val loveNote: NameNote
        get() = when {
            love.hasEmoji -> NameNote.EMOJI
            needNameError -> NameNote.NEED_NAME
            love.atLimit -> NameNote.LIMIT
            else -> NameNote.NONE
        }

    /** Your name is optional; only emoji blocks Continue. */
    val canContinue: Boolean get() = !you.hasEmoji

    val canMake: Boolean get() = !love.isBlank && !love.hasEmoji
}

/** The flow's transitions: pure, so the rules are unit-tested without Android. */
object NamePackReducer {

    /**
     * Your name, typed on its own step only: during the fade to their name the your-name field still
     * has focus, and keys reaching it then must not change a name no longer on screen.
     */
    fun typeYou(s: NamePackState, raw: String) =
        if (s.step != NameStep.YOU) s else s.copy(you = NameField(NameInput.clamp(NameInput.stripInvisible(raw))))

    /** Their name, typed on its own step only (see [typeYou]); a keystroke clears the missing-name note. */
    fun typeLove(s: NamePackState, raw: String) =
        if (s.step != NameStep.LOVE) s
        else s.copy(love = NameField(NameInput.clamp(NameInput.stripInvisible(raw))), needNameError = false)

    fun pickCharacter(s: NamePackState, character: Character) = s.copy(character = character)

    fun pickRelation(s: NamePackState, relation: Relation) = s.copy(relation = relation)

    /** Family relations show only Sweet, so a tone pick changes nothing there. */
    fun pickTone(s: NamePackState, tone: Tone) = if (s.relation.family) s else s.copy(tone = tone)

    fun continueFromYou(s: NamePackState) = if (s.step == NameStep.YOU && s.canContinue) s.copy(step = NameStep.LOVE) else s

    fun skipYou(s: NamePackState) = if (s.step == NameStep.YOU) s.copy(you = NameField(), step = NameStep.LOVE) else s

    /**
     * "Make our stickers": the new state and whether building starts. Only from their-name: a
     * second tap on the step while it fades out does nothing.
     */
    fun make(s: NamePackState): Pair<NamePackState, Boolean> = when {
        s.step != NameStep.LOVE -> s to false
        s.love.hasEmoji -> s to false
        s.love.isBlank -> s.copy(needNameError = true) to false
        else -> s.copy(step = NameStep.BUILDING, needNameError = false) to true
    }

    /** System back; null leaves the flow (back to the intro). */
    fun back(s: NamePackState): NamePackState? = when (s.step) {
        NameStep.YOU -> null
        NameStep.LOVE -> s.copy(step = NameStep.YOU)
        NameStep.BUILDING, NameStep.REVEAL -> s.copy(step = NameStep.LOVE)
    }

    fun built(s: NamePackState) = s.copy(step = NameStep.REVEAL)

    fun buildFailed(s: NamePackState) = s.copy(step = NameStep.LOVE)
}

/**
 * The Building bar: finished renders over [total], never ahead of [minMs] of elapsed time. A
 * finished build counts as complete, because parallel renders can report their counts late and
 * out of order (12, then 11).
 */
fun buildProgress(done: Int, total: Int, finished: Boolean, elapsedMs: Long, minMs: Long): Float {
    val real = if (finished) 1f else done.toFloat() / total
    return min(real, elapsedMs.toFloat() / minMs).coerceIn(0f, 1f)
}

private const val KEY_STEP = "np_step"
private const val KEY_YOU = "np_you"
private const val KEY_LOVE = "np_love"
private const val KEY_RELATION = "np_relation"
private const val KEY_CHARACTER = "np_character"
private const val KEY_TONE = "np_tone"

/** What a SavedStateHandle keeps, so a language switch or process death resumes the flow. */
fun NamePackState.toSaved(): Map<String, String> = mapOf(
    KEY_STEP to step.name,
    KEY_YOU to you.raw,
    KEY_LOVE to love.raw,
    KEY_RELATION to relation.id,
    KEY_CHARACTER to character.id,
    KEY_TONE to tone.id,
)

/** The state back from [saved]; a saved Reveal restarts at Building (stickers are not saved state). */
fun namePackStateFrom(saved: (String) -> String?): NamePackState {
    val step = saved(KEY_STEP)?.let { name -> NameStep.entries.firstOrNull { it.name == name } } ?: NameStep.YOU
    return NamePackState(
        step = if (step == NameStep.REVEAL) NameStep.BUILDING else step,
        you = NameField(saved(KEY_YOU).orEmpty()),
        love = NameField(saved(KEY_LOVE).orEmpty()),
        relation = Relation.byId(saved(KEY_RELATION)),
        character = Character.byId(saved(KEY_CHARACTER)),
        tone = Tone.byId(saved(KEY_TONE)),
    )
}
