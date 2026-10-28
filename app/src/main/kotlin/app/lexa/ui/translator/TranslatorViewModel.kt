package app.lexa.ui.translator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lexa.domain.repository.HistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class TranslatorViewModel @Inject constructor(
    history: HistoryRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")

    val state: StateFlow<TranslatorUiState> = combine(query, history.observeHistory()) { q, items ->
        TranslatorUiState(query = q, recent = items.distinctBy { it.query.lowercase() }.take(RECENT_LIMIT))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TranslatorUiState())

    fun onQueryChange(value: String) {
        query.value = value
    }

    /** Lookup and auto-add to «Новые слова» arrive in phase 2. */
    fun onTranslate() = Unit

    private companion object {
        const val RECENT_LIMIT = 5
    }
}
