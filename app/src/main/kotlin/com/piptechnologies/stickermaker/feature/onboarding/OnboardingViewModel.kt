package com.piptechnologies.stickermaker.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piptechnologies.stickermaker.core.data.prefs.PrefsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State for the two-slide onboarding.
 *
 * @property page 0 or 1 (the design ships exactly two slides).
 * @property finished true once the onboarded flag is persisted; the screen
 * then calls its onDone navigation lambda.
 */
data class OnboardingUiState(
    val page: Int = 0,
    val finished: Boolean = false
)

/**
 * Drives the pager and persists `onboarded = true` before the screen leaves
 * (Prototype ob.next / ob.skip: both Skip and "Get started" continue to the
 * theme picker).
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val prefsRepository: PrefsRepository
) : ViewModel() {

    private val state = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> =
        state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), state.value)

    private var completing = false

    /** The primary button: "Next" on the first slide, "Get started" on the last. */
    fun onNext() {
        if (state.value.page == 0) state.update { it.copy(page = 1) } else complete()
    }

    /** The ghost Skip on the first slide goes straight to the theme picker. */
    fun onSkip() = complete()

    private fun complete() {
        if (completing) return
        completing = true
        viewModelScope.launch {
            prefsRepository.setOnboarded(true)
            state.update { it.copy(finished = true) }
        }
    }
}
