package app.lexa.ui.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lexa.domain.model.Direction
import app.lexa.domain.repository.SettingsRepository
import app.lexa.domain.repository.StatsRepository
import app.lexa.domain.repository.WordRepository
import app.lexa.domain.time.TimeProvider
import app.lexa.domain.training.AnswerResult
import app.lexa.domain.training.CardDirection
import app.lexa.domain.training.CheckResult
import app.lexa.domain.training.TrainingCard
import app.lexa.domain.training.TrainingSession
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.shareIn
import app.lexa.domain.time.nowTicks
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** How many candidates the card shows as the prompt (RU→EN) or as the correct answer. */
private const val SHOWN_CANDIDATES = 3

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    words: WordRepository,
    private val stats: StatsRepository,
    private val settings: SettingsRepository,
    private val training: TrainingSession,
    private val time: TimeProvider,
) : ViewModel() {

    /**
     * "Now" for the due count and the streak: re-read at local midnight, on [refresh] (screen resumed)
     * and when a session ends, so answered words drop out and a new day is seen without a restart.
     */
    private val refreshes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val now = time.nowTicks(refreshes).shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)
    private val session = MutableStateFlow<TrainingPhase?>(null)

    /** The running session alone, for tests that should not depend on the Room flows behind [state]. */
    internal val phase: StateFlow<TrainingPhase?> get() = session

    val state: StateFlow<HomeUiState> = combine(
        now.flatMapLatest { words.observeDueCount(it) },
        words.observeCounts(),
        now.flatMapLatest { stats.observeStreak(time.today()) },
        session,
    ) { due, counts, streak, phase ->
        HomeUiState(isLoading = false, dueCount = due, counts = counts, streakDays = streak.current, session = phase)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private var cards: List<TrainingCard> = emptyList()
    private var directions: List<CardDirection> = emptyList()
    private var index = 0
    private var correct = 0
    private var practice = false
    private var lastResult: AnswerResult? = null
    private var busy = false

    /** One celebration per session (CLAUDE.md §3): a learned word or a beaten best streak. */
    private var celebrated = false
    private var bestStreakAtStart = 0

    /**
     * Starts a session of due words. [practice] trains the hardest words outside the schedule
     * (wired to «Повторить их сейчас» in phase 4): correct answers there do not move the box.
     */
    fun start(practice: Boolean = false) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                this@HomeViewModel.practice = practice
                celebrated = false
                bestStreakAtStart = stats.observeStreak(time.today()).first().best
                val direction = settings.settings.first().direction
                cards = training.build(practice)
                directions = cards.map {
                    when (direction) {
                        Direction.EN_RU -> CardDirection.EN_RU
                        Direction.RU_EN -> CardDirection.RU_EN
                        Direction.MIXED -> if (Random.nextBoolean()) CardDirection.EN_RU else CardDirection.RU_EN
                    }
                }
                index = 0
                correct = 0
                if (cards.isNotEmpty()) session.value = question()
            } finally {
                busy = false
            }
        }
    }

    /** Re-reads "now" (called when the screen resumes). */
    fun refresh() {
        refreshes.tryEmit(Unit)
    }

    fun onAnswerChange(text: String) {
        if (busy) return
        val phase = session.value as? TrainingPhase.Question ?: return
        session.value = phase.copy(answer = text)
    }

    fun check() {
        val phase = session.value as? TrainingPhase.Question ?: return
        if (phase.answer.isBlank() || busy) return
        busy = true
        viewModelScope.launch {
            try {
                val card = cards[index]
                val direction = directions[index]
                val result = training.answer(card, direction, phase.answer, wasDue = !practice)
                lastResult = result
                if (result.check.isCorrect) correct++
                val verdict = when (result.check) {
                    is CheckResult.Exact -> Verdict.CORRECT
                    is CheckResult.AcceptedWithTypo -> Verdict.TYPO
                    is CheckResult.Wrong -> Verdict.WRONG
                }
                val celebration = celebrationFor(result)
                // A result only lands on the card it was computed for.
                if (session.value !== phase) return@launch
                session.value = TrainingPhase.Feedback(
                    card = phase.card,
                    position = phase.position,
                    total = phase.total,
                    answer = phase.answer.trim(),
                    verdict = verdict,
                    correctAnswer = when (val check = result.check) {
                        is CheckResult.AcceptedWithTypo -> check.expected
                        else -> shownAnswer(card, direction)
                    },
                    boxAfter = result.transition.state.box,
                    learned = result.transition.learned,
                    example = card.example,
                    celebration = celebration,
                )
            } finally {
                busy = false
            }
        }
    }

    /** «Я знал, это опечатка»: re-scores the wrong answer as correct and undoes its box change. */
    fun overrideAsTypo() {
        val phase = session.value as? TrainingPhase.Feedback ?: return
        val result = lastResult ?: return
        if (phase.verdict != Verdict.WRONG || busy) return
        busy = true
        viewModelScope.launch {
            try {
                val transition = training.override(cards[index], result.logId, wasDue = !practice)
                correct++
                if (session.value !== phase) return@launch
                session.value = phase.copy(
                    verdict = Verdict.OVERRIDDEN,
                    boxAfter = transition.state.box,
                    learned = transition.learned,
                )
            } finally {
                busy = false
            }
        }
    }

    private suspend fun celebrationFor(result: AnswerResult): Celebration? {
        if (celebrated || !result.check.isCorrect) return null
        val celebration = if (result.transition.learned) {
            Celebration.Learned
        } else {
            // The first answer of the day extends the streak; it is a record only past the best one so far.
            val streak = stats.observeStreak(time.today()).first().current
            if (streak >= 2 && streak > bestStreakAtStart) Celebration.StreakRecord(streak) else null
        }
        if (celebration != null) celebrated = true
        return celebration
    }

    fun next() {
        // While an answer or an override is being written, «Дальше» waits: otherwise the late result
        // would overwrite the next card.
        if (busy || session.value !is TrainingPhase.Feedback) return
        index++
        lastResult = null
        session.value = if (index < cards.size) question() else TrainingPhase.Done(correct = correct, total = cards.size)
    }

    fun finish() {
        if (busy) return
        session.value = null
        cards = emptyList()
        refresh()
    }

    private fun question(): TrainingPhase.Question {
        val card = cards[index]
        val direction = directions[index]
        return TrainingPhase.Question(
            card = TrainingCardUi(
                prompt = when (direction) {
                    CardDirection.EN_RU -> card.term
                    CardDirection.RU_EN -> shownAnswer(card, CardDirection.EN_RU)
                },
                // IPA would give the answer away when the term is what must be typed.
                ipa = card.ipa.takeIf { direction == CardDirection.EN_RU },
                box = card.state.box,
                expectsEnglish = direction == CardDirection.RU_EN,
            ),
            position = index + 1,
            total = cards.size,
        )
    }

    private fun shownAnswer(card: TrainingCard, direction: CardDirection): String =
        TrainingSession.expected(card, direction).take(SHOWN_CANDIDATES).joinToString(", ")

    // After every field initializer: a request may start the session immediately.
    init {
        // «Повторить их сейчас» on the statistics screen asks for practice through this handle.
        viewModelScope.launch {
            savedStateHandle.getStateFlow(START_PRACTICE, false).collect { requested ->
                if (requested) {
                    savedStateHandle[START_PRACTICE] = false
                    start(practice = true)
                }
            }
        }
    }

    companion object {
        /** Set on this entry's SavedStateHandle to start a practice session of the hardest words. */
        const val START_PRACTICE = "startPractice"
    }
}
