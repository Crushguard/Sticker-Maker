package com.piptechnologies.stickermaker.feature.rating

/**
 * Immutable snapshot of the persisted rating-prompt state. Loaded and stored by
 * [RatingPreferences]; consumed by the pure [RatingEligibility.isEligible].
 */
data class RatingState(
    /** The user opened the Play listing from five stars, or sent feedback: never ask again. */
    val resolved: Boolean = false,
    /** Explicit dismissals so far (swipe-away, Cancel on the feedback box). */
    val dismissCount: Int = 0,
    /** "Maybe later" / "Not now" taps so far: drives the short, growing re-ask gap. */
    val laterCount: Int = 0,
    /** How many times the sheet has actually been shown (bookkeeping only). */
    val promptsShown: Int = 0,
    /** Lifetime number of packs WhatsApp confirmed, catalog and own packs alike. */
    val packsAdded: Int = 0,
    /** Packs WhatsApp confirmed since the sheet was last shown. */
    val addsSincePrompt: Int = 0,
    /** Confirmed adds that must accumulate before the sheet may be shown again. */
    val requiredAdds: Int = RatingEligibility.FIRST_THRESHOLD,
)

/**
 * The whole "may we ask for a rating right now?" rule, as a pure function, following the other
 * apps' rating prompt (Status Saver's RatingEligibility), with a pack WhatsApp confirmed as the
 * app's success moment.
 *
 * The prompt is earned, never automatic: only a pack the user got into WhatsApp counts, so a fresh
 * install, a failed download or a cancelled add can never trigger it. The first confirmed pack earns
 * it, on the day of the install as on any other: that add is the moment the app delivered. From
 * there the cadence is: one prompt per app process, a short growing gap after "Maybe later"
 * (2 → 3 → 4 adds), a longer one after an explicit dismissal (4 → 6 adds), a hard stop after
 * [MAX_DISMISSALS] dismissals, and never again once the user rated or sent feedback.
 */
object RatingEligibility {

    /** Packs the user must have added to WhatsApp, ever, before the prompt may appear at all. */
    const val MIN_PACKS_ADDED = 1

    /** Hard stop after this many explicit dismissals. */
    const val MAX_DISMISSALS = 3

    /** Adds required before the very first prompt, on top of [MIN_PACKS_ADDED]. */
    const val FIRST_THRESHOLD = 1

    /**
     * @param state the persisted state, read off the main thread.
     * @param promptedThisSession whether the sheet already appeared in this app process.
     */
    fun isEligible(state: RatingState, promptedThisSession: Boolean): Boolean {
        if (promptedThisSession) return false
        if (state.resolved) return false
        // Only explicit dismissals stop the prompt for good: a prompt the user never answered
        // (closed or killed the app) must keep coming back.
        if (state.dismissCount >= MAX_DISMISSALS) return false
        if (state.packsAdded < MIN_PACKS_ADDED) return false
        return state.addsSincePrompt >= state.requiredAdds
    }

    /** Adds required after the user chose "Maybe later" for the [laterCount]-th time. */
    fun requiredAfterLater(laterCount: Int): Int = when (laterCount) {
        1 -> 2
        2 -> 3
        else -> 4
    }

    /** Adds required after the user explicitly dismissed for the [dismissCount]-th time. */
    fun requiredAfterDismissal(dismissCount: Int): Int = if (dismissCount <= 1) 4 else 6
}
