package com.piptechnologies.stickermaker.feature.language

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piptechnologies.stickermaker.core.data.prefs.PrefsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** State for the language screen: the tag currently persisted in prefs. */
data class LanguageUiState(
    val selectedTag: String = PrefsRepository.LANGUAGE_SYSTEM
)

@HiltViewModel
class LanguageViewModel @Inject constructor(
    private val prefs: PrefsRepository
) : ViewModel() {

    val uiState: StateFlow<LanguageUiState> = prefs.language
        .map { tag -> LanguageUiState(selectedTag = tag) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LanguageUiState()
        )

    /**
     * Persists the picked language tag. Applying it via
     * AppCompatDelegate.setApplicationLocales is the screen's job (it may
     * recreate the activity, which clears this ViewModel - hence the
     * NonCancellable write so the preference always lands).
     */
    fun select(tag: String) {
        viewModelScope.launch {
            withContext(NonCancellable) { prefs.setLanguage(tag) }
        }
    }
}
