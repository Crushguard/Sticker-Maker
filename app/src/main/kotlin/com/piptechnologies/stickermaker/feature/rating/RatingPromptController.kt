package com.piptechnologies.stickermaker.feature.rating

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single entry point that decides *when* the rating sheet may appear, like the other apps'
 * RatingPromptController. Whether the user has earned the question at all lives in
 * [RatingEligibility]; the counters live in [RatingPreferences].
 *
 * Timing rule: a pack WhatsApp confirmed **arms** the prompt, and [RatingPromptHost] shows it at
 * the next natural pause: back on Home, a pack page, Saved or My Packs, a moment after the "Added
 * to WhatsApp" toast. It is never shown on a launch, during first-run setup, over the Create flow,
 * or after a failed or cancelled add.
 *
 * Settings › Rate us is a user-initiated action and deliberately bypasses all of this: it shows the
 * sheet directly, and only tells this object when the user rated or sent feedback.
 */
object RatingPromptController {

    private val armedByAdd = MutableStateFlow(false)

    /** True while a pack WhatsApp confirmed waits for its natural pause. In-memory only. */
    val armed: StateFlow<Boolean> = armedByAdd.asStateFlow()

    /** WhatsApp confirmed a pack (catalog or own): count it and arm the prompt. */
    fun onPackAdded(context: Context) {
        RatingPreferences.recordPackAdded(context)
        armedByAdd.value = true
    }

    /**
     * The host reached a natural pause with an armed add. Blocks on the preferences thread:
     * **call off the main thread**, then [disarm] once the answer is acted on.
     *
     * @return true when the sheet should show now (it is then already counted as shown).
     */
    fun claimPause(context: Context): Boolean {
        if (!armedByAdd.value) return false
        return RatingPreferences.claimPrompt(context)
    }

    /**
     * One pause is consumed per armed add, whether or not the user turned out to be eligible: the
     * prompt must follow an add, not surface at some unrelated moment later. Called by the host
     * after it acted on [claimPause]; disarming earlier would restart the host's effect (it is
     * keyed on [armed]) before the sheet could be shown.
     */
    fun disarm() {
        armedByAdd.value = false
    }

    /** Five stars and the Play listing opened, or feedback sent (from the prompt or from Settings). */
    fun onRated(context: Context) = RatingPreferences.onResolved(context)

    /** "Maybe later" / "Not now" on a prompted sheet. */
    fun onLater(context: Context) = RatingPreferences.onLater(context)

    /** A prompted sheet swiped away or cancelled. */
    fun onDismissed(context: Context) = RatingPreferences.onDismissed(context)
}
