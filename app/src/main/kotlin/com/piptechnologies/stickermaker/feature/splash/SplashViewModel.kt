package com.piptechnologies.stickermaker.feature.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piptechnologies.stickermaker.core.data.prefs.PrefsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * State for the launch screen.
 *
 * @property onboarded null while DataStore is still being read; then whether
 * the onboarding flow has already been completed.
 */
data class SplashUiState(
    val onboarded: Boolean? = null
)

/** Reads the onboarding flag while the branding beat plays. */
@HiltViewModel
class SplashViewModel @Inject constructor(
    prefsRepository: PrefsRepository
) : ViewModel() {

    // Eager so the DataStore read overlaps the branding beat instead of
    // starting after it.
    val uiState: StateFlow<SplashUiState> = prefsRepository.onboarded
        .map { SplashUiState(onboarded = it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SplashUiState())
}
