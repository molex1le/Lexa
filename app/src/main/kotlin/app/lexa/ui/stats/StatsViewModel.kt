package app.lexa.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lexa.domain.repository.ReviewRepository
import app.lexa.domain.repository.WordRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Phase 1 reads the cheap totals straight from Room. Learned-by-day, streaks, week accuracy and hard
 * words are queries of phase 4; until then they stay at their honest zero defaults.
 */
@HiltViewModel
class StatsViewModel @Inject constructor(
    words: WordRepository,
    reviews: ReviewRepository,
) : ViewModel() {

    val state: StateFlow<StatsUiState> = combine(
        words.observeCounts(),
        words.observeBoxDistribution(),
        reviews.observeAnswerTotals(),
    ) { counts, boxes, totals ->
        StatsUiState(counts = counts, boxes = boxes, allAnswers = totals)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())
}
