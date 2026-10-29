package app.lexa.ui.word

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.lexa.domain.repository.WordRepository
import app.lexa.ui.navigation.WordRoute
import app.lexa.ui.translator.Pronouncer
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class WordViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val words: WordRepository,
    private val pronouncer: Pronouncer,
) : ViewModel() {

    private val wordId = savedStateHandle.toRoute<WordRoute>().wordId

    val state: StateFlow<WordUiState> = words.observeDetails(wordId)
        .map { WordUiState(isLoading = false, details = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WordUiState())

    fun onToggleFavorite() {
        val word = state.value.details?.summary ?: return
        viewModelScope.launch { words.setFavorite(word.id, !word.isFavorite) }
    }

    fun onPronounce() {
        val entry = state.value.details?.entry ?: return
        pronouncer.play(entry.term, entry.audioUrl)
    }

    fun onReturnToLearning() {
        viewModelScope.launch { words.returnToLearning(wordId) }
    }

    override fun onCleared() = pronouncer.release()
}
