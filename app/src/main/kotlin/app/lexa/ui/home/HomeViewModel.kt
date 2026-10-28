package app.lexa.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lexa.domain.repository.WordRepository
import app.lexa.domain.time.TimeProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class HomeViewModel @Inject constructor(
    words: WordRepository,
    time: TimeProvider,
) : ViewModel() {

    // Streak comes from review_log in phase 4; until then it is honestly 0.
    val state: StateFlow<HomeUiState> = combine(
        words.observeDueCount(time.nowMillis()),
        words.observeCounts(),
    ) { due, counts ->
        HomeUiState(isLoading = false, dueCount = due, counts = counts)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}
