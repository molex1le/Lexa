package app.lexa.ui.lists

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.lexa.domain.model.WordSort
import app.lexa.domain.model.WordSummary
import app.lexa.domain.repository.SettingsRepository
import app.lexa.domain.repository.WordRepository
import app.lexa.ui.navigation.ListTab
import app.lexa.ui.navigation.ListsRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private data class Local(val tab: ListTab, val query: String = "", val deletedWordId: Long? = null)

@HiltViewModel
class ListsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val words: WordRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val local = MutableStateFlow(Local(savedStateHandle.toRoute<ListsRoute>().tab))

    private val lists = combine(words.observeNotLearned(), words.observeFavorites(), words.observeLearned(), ::Triple)

    val state: StateFlow<ListsUiState> = combine(local, lists, settings.settings.map { it.wordSort }) { l, (new, fav, learned), sort ->
        ListsUiState(
            tab = l.tab,
            isLoading = false,
            newWords = new,
            favorites = fav,
            learned = learned,
            query = l.query,
            sort = sort,
            deletedWordId = l.deletedWordId,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListsUiState(tab = local.value.tab))

    fun onSelectTab(value: ListTab) = local.update { it.copy(tab = value) }

    fun onQueryChange(value: String) = local.update { it.copy(query = value) }

    fun onSortChange(sort: WordSort) {
        viewModelScope.launch { settings.setWordSort(sort) }
    }

    fun onToggleFavorite(word: WordSummary) {
        viewModelScope.launch { words.setFavorite(word.id, !word.isFavorite) }
    }

    fun onDelete(id: Long) {
        val previous = local.value.deletedWordId
        local.update { it.copy(deletedWordId = id) }
        viewModelScope.launch {
            // A second swipe replaces the snackbar: the first word is gone for good.
            if (previous != null && previous != id) words.purge(previous)
            words.softDelete(id)
        }
    }

    fun onUndoDelete(id: Long) {
        clearPending(id)
        viewModelScope.launch { words.restore(id) }
    }

    fun onDeleteConfirmed(id: Long) {
        clearPending(id)
        viewModelScope.launch { words.purge(id) }
    }

    fun onReturnToLearning(id: Long) {
        viewModelScope.launch { words.returnToLearning(id) }
    }

    private fun clearPending(id: Long) = local.update { if (it.deletedWordId == id) it.copy(deletedWordId = null) else it }
}
