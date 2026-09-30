package com.piptechnologies.stickermaker.feature.onboarding

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OnboardingViewModelTest {

    @Test
    fun nextTurnsThePageThenHandsOver() = runBlocking {
        val vm = OnboardingViewModel()
        vm.onNext()
        assertEquals(1, vm.uiState.value.page)
        vm.onNext()
        withTimeout(1_000) { vm.done.first() }
    }

    @Test
    fun skipHandsOverFromTheFirstSlide() = runBlocking {
        val vm = OnboardingViewModel()
        vm.onSkip()
        withTimeout(1_000) { vm.done.first() }
        assertEquals(0, vm.uiState.value.page)
    }

    /** Back from "Your name" shows the intro again; it must stay there, not hand over twice. */
    @Test
    fun aHandOverIsDeliveredOnce() = runBlocking {
        val vm = OnboardingViewModel()
        vm.onSkip()
        withTimeout(1_000) { vm.done.first() }
        assertNull(withTimeoutOrNull(200) { vm.done.first() })
    }
}
