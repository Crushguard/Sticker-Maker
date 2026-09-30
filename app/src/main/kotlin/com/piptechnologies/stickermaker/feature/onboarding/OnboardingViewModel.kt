package com.piptechnologies.stickermaker.feature.onboarding

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update

/**
 * State for the two-slide onboarding.
 *
 * @property page 0 or 1 (the design ships exactly two slides).
 */
data class OnboardingUiState(val page: Int = 0)

/**
 * Drives the pager (Prototype ob.next / ob.skip): Skip on either slide and "Get started" hand
 * over to the Custom Stickers flow through [done]. The intro stays under that flow, so Back
 * returns here; the flow writes the onboarded flag when it ends.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor() : ViewModel() {

    private val state = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = state.asStateFlow()

    private val handOver = Channel<Unit>(Channel.CONFLATED)

    /** One event per hand-over, not state, so coming back to the intro does not fire it again. */
    val done: Flow<Unit> = handOver.receiveAsFlow()

    /** The primary button: "Next" on the first slide, "Get started" on the last. */
    fun onNext() {
        if (state.value.page == 0) state.update { it.copy(page = 1) } else handOver.trySend(Unit)
    }

    /** The ghost Skip, on both slides. */
    fun onSkip() {
        handOver.trySend(Unit)
    }
}
