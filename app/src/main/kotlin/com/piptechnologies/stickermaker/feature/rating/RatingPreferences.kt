package com.piptechnologies.stickermaker.feature.rating

import android.content.Context
import android.content.SharedPreferences
import java.util.concurrent.Executors

/**
 * Storage for the rating prompt, like the other apps' RatingPreferences. The decision itself is the
 * pure [RatingEligibility.isEligible]; this object only loads, mutates and persists the
 * [RatingState] that feeds it.
 *
 * Only a pack WhatsApp confirmed counts ([recordPackAdded]). How the user closed the sheet decides
 * when it re-arms:
 *
 *  - **Resolved** (five stars and the Play listing opened, or feedback sent): never shown again.
 *  - **Later** ("Maybe later" / "Not now"): re-arms after a short, growing gap.
 *  - **Dismissed** (swipe-away, Cancel): re-arms after a longer gap, and stops for good after
 *    [RatingEligibility.MAX_DISMISSALS] explicit dismissals.
 *  - **No answer** (the app closed under the sheet): nothing is recorded, so it comes back after
 *    the next add.
 *
 * Threading: SharedPreferences is never touched from the main thread here. Every mutation, and the
 * check in [claimPrompt], runs on one private background thread, in order: a claim always sees the
 * add recorded just before it.
 */
object RatingPreferences {

    private const val PREFS_NAME = "rating_prompt"

    private const val KEY_RESOLVED = "resolved"
    private const val KEY_DISMISS_COUNT = "dismiss_count"
    private const val KEY_LATER_COUNT = "later_count"
    private const val KEY_PROMPTS_SHOWN = "prompts_shown"
    private const val KEY_PACKS_ADDED = "packs_added"
    private const val KEY_ADDS_SINCE_PROMPT = "adds_since_prompt"
    private const val KEY_REQUIRED_ADDS = "required_adds"

    /** In-memory guard: at most one prompt per app process. Resets on cold start. */
    @Volatile
    private var shownThisSession = false

    private val worker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "rating-prefs").apply {
            isDaemon = true
            priority = Thread.MIN_PRIORITY
        }
    }

    /** A pack WhatsApp confirmed: counts toward the prompt. */
    fun recordPackAdded(context: Context) = mutate(context) {
        it.copy(packsAdded = it.packsAdded + 1, addsSincePrompt = it.addsSincePrompt + 1)
    }

    /**
     * Decides whether the sheet may show now and, when it may, records it as shown. Blocks until
     * the background thread has answered: **call off the main thread**.
     */
    fun claimPrompt(context: Context): Boolean {
        val appContext = context.applicationContext
        return worker.submit<Boolean> {
            val prefs = prefs(appContext)
            val state = read(prefs)
            if (!RatingEligibility.isEligible(state, shownThisSession)) return@submit false
            shownThisSession = true
            write(prefs, state.copy(addsSincePrompt = 0, promptsShown = state.promptsShown + 1))
            true
        }.get()
    }

    /** Five stars and the Play listing opened, or feedback sent: stop asking for good. */
    fun onResolved(context: Context) = mutate(context) { it.copy(resolved = true) }

    /** "Maybe later" / "Not now": re-arm after a short, growing gap. */
    fun onLater(context: Context) = mutate(context) {
        val laterCount = it.laterCount + 1
        it.copy(
            laterCount = laterCount,
            requiredAdds = RatingEligibility.requiredAfterLater(laterCount),
            addsSincePrompt = 0,
        )
    }

    /** Swipe-away or Cancel: back off harder. */
    fun onDismissed(context: Context) = mutate(context) {
        val dismissCount = it.dismissCount + 1
        it.copy(
            dismissCount = dismissCount,
            requiredAdds = RatingEligibility.requiredAfterDismissal(dismissCount),
            addsSincePrompt = 0,
        )
    }

    private fun mutate(context: Context, transform: (RatingState) -> RatingState) {
        val appContext = context.applicationContext
        worker.execute {
            val prefs = prefs(appContext)
            write(prefs, transform(read(prefs)))
        }
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun read(prefs: SharedPreferences): RatingState = RatingState(
        resolved = prefs.getBoolean(KEY_RESOLVED, false),
        dismissCount = prefs.getInt(KEY_DISMISS_COUNT, 0),
        laterCount = prefs.getInt(KEY_LATER_COUNT, 0),
        promptsShown = prefs.getInt(KEY_PROMPTS_SHOWN, 0),
        packsAdded = prefs.getInt(KEY_PACKS_ADDED, 0),
        addsSincePrompt = prefs.getInt(KEY_ADDS_SINCE_PROMPT, 0),
        requiredAdds = prefs.getInt(KEY_REQUIRED_ADDS, RatingEligibility.FIRST_THRESHOLD),
    )

    private fun write(prefs: SharedPreferences, state: RatingState) {
        prefs.edit().apply {
            putBoolean(KEY_RESOLVED, state.resolved)
            putInt(KEY_DISMISS_COUNT, state.dismissCount)
            putInt(KEY_LATER_COUNT, state.laterCount)
            putInt(KEY_PROMPTS_SHOWN, state.promptsShown)
            putInt(KEY_PACKS_ADDED, state.packsAdded)
            putInt(KEY_ADDS_SINCE_PROMPT, state.addsSincePrompt)
            putInt(KEY_REQUIRED_ADDS, state.requiredAdds)
        }.apply()
    }
}
