package app.lexa.ui.word

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.lexa.domain.repository.WordRepository
import app.lexa.ui.navigation.WordRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class WordViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    words: WordRepository,
) : ViewModel() {

    private val wordId = savedStateHandle.toRoute<WordRoute>().wordId

    val state: StateFlow<WordUiState> = words.observeWord(wordId)
        .map { WordUiState(isLoading = false, word = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WordUiState())
}
