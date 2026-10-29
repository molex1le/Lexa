package app.lexa.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lexa.domain.repository.StatsRepository
import app.lexa.domain.repository.WordRepository
import app.lexa.domain.time.TimeProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import app.lexa.domain.time.nowTicks
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/** How many hard words the statistics show; practice mode trains them, most mistakes first. */
const val HARD_WORDS_SHOWN = 5

/** Every number comes from a Room Flow over `words` and `review_log` (docs/ARCHITECTURE.md §7). */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatsViewModel @Inject constructor(
    words: WordRepository,
    private val stats: StatsRepository,
    time: TimeProvider,
) : ViewModel() {

    private val refreshes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Today, re-read at local midnight and when the screen resumes: a process left alive overnight moves on. */
    private val today = time.nowTicks(refreshes).map { time.today() }.distinctUntilChanged()

    /** Re-reads "today" (called when the screen resumes). */
    fun refresh() {
        refreshes.tryEmit(Unit)
    }

    private val counts = combine(words.observeCounts(), words.observeBoxDistribution(), words.observeLearned()) { c, b, l ->
        StatsUiState(counts = c, boxes = b, learnedWords = l)
    }

    val state: StateFlow<StatsUiState> = today.flatMapLatest { today -> byDay(today) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    private fun byDay(today: LocalDate) = combine(
        counts,
        stats.observeLearnedPeriods(today),
        stats.observeLearnedByDay(today, CHART_DAYS),
        stats.observeStreak(today),
        combine(stats.observeAccuracy(today), stats.observeHardWords(HARD_WORDS_SHOWN), ::Pair),
    ) { base, periods, byDay, streak, (accuracy, hard) ->
        base.copy(
            learnedToday = periods.today,
            learnedWeek = periods.week,
            learnedMonth = periods.month,
            learnedByDay = byDay,
            streak = streak.current,
            bestStreak = streak.best,
            weekAnswers = accuracy.week,
            allAnswers = accuracy.all,
            hardWords = hard,
        )
    }
}
