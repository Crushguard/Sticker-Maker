package com.piptechnologies.stickermaker.feature.customize

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piptechnologies.stickermaker.core.data.prefs.PrefsRepository
import com.piptechnologies.stickermaker.core.data.repo.CatalogRepository
import com.piptechnologies.stickermaker.core.model.Category
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The eight themes exactly as design/catalog.json ships them (id, name, icon,
 * hue, order). Used whenever the remote `categories` collection is empty -
 * offline first run, Firestore error - so the picker always renders.
 */
internal val FallbackCategories: List<Category> = listOf(
    Category(id = "couples", name = "Couples", icon = "heart-handshake", hue = 10, order = 1),
    Category(id = "cute", name = "Cute", icon = "rabbit", hue = 330, order = 2),
    Category(id = "funny", name = "Funny", icon = "laugh", hue = 85, order = 3),
    Category(id = "anime", name = "Anime", icon = "sparkles", hue = 300, order = 4),
    Category(id = "romantic", name = "Romantic", icon = "flower-2", hue = 45, order = 5),
    Category(id = "flirty", name = "Flirty", icon = "message-circle-heart", hue = 200, order = 6),
    Category(id = "goodnight", name = "Good night", icon = "moon", hue = 250, order = 7),
    Category(id = "distance", name = "Long distance", icon = "plane", hue = 150, order = 8)
)

/**
 * State for the theme picker.
 *
 * @property categories remote categories, or [FallbackCategories] while
 * offline; already sorted by their catalog order.
 * @property selected theme ids currently checked (pre-filled from prefs, so
 * the Settings > Edit themes entry arrives pre-checked).
 * @property saved set right after the selection is persisted; the screen then
 * calls its onDone lambda.
 */
data class CustomizationUiState(
    val categories: List<Category> = FallbackCategories,
    val selected: Set<String> = emptySet(),
    val saved: Boolean = false
)

/**
 * Multi-select over the catalog categories. Home's chip row is built from
 * what is persisted here (Prototype cz.*: minimum one theme, the CTA reads
 * the count, edit mode saves in place).
 */
@HiltViewModel
class CustomizationViewModel @Inject constructor(
    catalogRepository: CatalogRepository,
    private val prefsRepository: PrefsRepository
) : ViewModel() {

    private val selection = MutableStateFlow<Set<String>>(emptySet())
    private val saved = MutableStateFlow(false)

    init {
        // Pre-check the persisted themes once; local toggles take over after.
        viewModelScope.launch {
            selection.value = selection.value + prefsRepository.selectedThemes.first()
        }
    }

    private val categories = catalogRepository.observeCategories()
        .map { remote -> remote.ifEmpty { FallbackCategories } }

    val uiState: StateFlow<CustomizationUiState> =
        combine(categories, selection, saved) { cats, sel, done ->
            CustomizationUiState(categories = cats, selected = sel, saved = done)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            CustomizationUiState()
        )

    fun onToggleTheme(id: String) {
        selection.update { current -> if (id in current) current - id else current + id }
    }

    /** Persists the selection; no-op while nothing is checked (CTA is disabled). */
    fun onSave() {
        val picked = selection.value
        if (picked.isEmpty() || saved.value) return
        viewModelScope.launch {
            prefsRepository.setSelectedThemes(picked)
            saved.value = true
        }
    }
}
