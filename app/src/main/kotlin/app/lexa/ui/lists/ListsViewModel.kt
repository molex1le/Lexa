package app.lexa.ui.lists

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.lexa.domain.repository.WordRepository
import app.lexa.ui.navigation.ListTab
import app.lexa.ui.navigation.ListsRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class ListsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    words: WordRepository,
) : ViewModel() {

    private val tab = MutableStateFlow(savedStateHandle.toRoute<ListsRoute>().tab)

    val state: StateFlow<ListsUiState> = combine(
        tab,
        words.observeNotLearned(),
        words.observeFavorites(),
        words.observeLearned(),
    ) { tab, new, favorites, learned ->
        ListsUiState(tab = tab, isLoading = false, newWords = new, favorites = favorites, learned = learned)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListsUiState(tab = tab.value))

    fun onSelectTab(value: ListTab) {
        tab.value = value
    }
}
