package com.piptechnologies.stickermaker.feature.rating

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RatingEligibilityTest {

    /** A fresh install that has just had its first pack confirmed by WhatsApp. */
    private val earned = RatingState(packsAdded = 1, addsSincePrompt = 1)

    @Test
    fun `the first pack WhatsApp confirmed earns the prompt`() {
        assertTrue(RatingEligibility.isEligible(earned, promptedThisSession = false))
    }

    @Test
    fun `nothing before a pack reached WhatsApp`() {
        assertFalse(RatingEligibility.isEligible(RatingState(), promptedThisSession = false))
    }

    @Test
    fun `at most once per app process`() {
        assertFalse(RatingEligibility.isEligible(earned, promptedThisSession = true))
    }

    @Test
    fun `never again once the user rated or sent feedback`() {
        val state = earned.copy(resolved = true, addsSincePrompt = 99)
        assertFalse(RatingEligibility.isEligible(state, promptedThisSession = false))
    }

    @Test
    fun `explicit dismissals stop the prompt for good after the limit`() {
        val state = earned.copy(dismissCount = RatingEligibility.MAX_DISMISSALS, addsSincePrompt = 99)
        assertFalse(RatingEligibility.isEligible(state, promptedThisSession = false))
    }

    @Test
    fun `after a prompt the next one waits for the required number of new adds`() {
        val waiting = earned.copy(packsAdded = 3, addsSincePrompt = 1, requiredAdds = 3)
        assertFalse(RatingEligibility.isEligible(waiting, promptedThisSession = false))
        assertTrue(RatingEligibility.isEligible(waiting.copy(addsSincePrompt = 3), promptedThisSession = false))
    }

    @Test
    fun `later backs off gently and dismissals harder`() {
        assertEquals(listOf(2, 3, 4, 4), (1..4).map(RatingEligibility::requiredAfterLater))
        assertEquals(listOf(4, 6, 6), (1..3).map(RatingEligibility::requiredAfterDismissal))
    }
}
