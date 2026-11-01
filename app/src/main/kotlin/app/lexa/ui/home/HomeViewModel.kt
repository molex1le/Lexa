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
import app.lexa.domain.model.Settings
import app.lexa.domain.training.AnswerMode
import app.lexa.domain.training.DailyProgress
import app.lexa.domain.training.DailyProgressRepository
import app.lexa.domain.training.progressOn
import app.lexa.widget.WidgetRefresher
import app.lexa.domain.training.CheckResult
import app.lexa.ui.translator.Pronouncer
import app.lexa.domain.training.DeckRepository
import app.lexa.domain.training.DeckWord
import app.lexa.domain.training.PlacementTest
import app.lexa.domain.model.VocabLevel
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
    private val deck: DeckRepository,
    private val time: TimeProvider,
    private val pronouncer: Pronouncer,
    private val progress: DailyProgressRepository,
    private val widgets: WidgetRefresher,
) : ViewModel() {

    /**
     * "Now" for the due count and the streak: re-read at local midnight, on [refresh] (screen resumed)
     * and when a session ends, so answered words drop out and a new day is seen without a restart.
     */
    private val refreshes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val now = time.nowTicks(refreshes).shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)
    private val session = MutableStateFlow<TrainingPhase?>(null)
    private val placement = MutableStateFlow<Placement?>(null)

    /** The running session alone, for tests that should not depend on the Room flows behind [state]. */
    internal val phase: StateFlow<TrainingPhase?> get() = session

    /** «12 из 20» for today; re-read at midnight through [now], so a new day starts at 0. */
    private val dailyProgress: StateFlow<DailyProgress> = combine(progress.count, settings.settings, now) { count, s, _ ->
        count.progressOn(time.today().toEpochDay(), s.dailyGoal)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DailyProgress(done = 0, goal = Settings.DEFAULT_DAILY_GOAL))

    val state: StateFlow<HomeUiState> = combine(
        combine(now.flatMapLatest { words.observeDueCount(it) }, words.observeCounts(), dailyProgress, ::Triple),
        now.flatMapLatest { stats.observeStreak(time.today()) },
        session,
        settings.settings,
        placement,
    ) { (due, counts, daily), streak, phase, s, test ->
        HomeUiState(
            isLoading = false,
            dueCount = due,
            counts = counts,
            streakDays = streak.current,
            session = phase,
            level = s.vocabLevel,
            placement = test,
            daily = daily,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private var testWords: List<DeckWord> = emptyList()
    private var testAnswers = mutableListOf<Boolean>()

    fun chooseLevel(level: VocabLevel) {
        placement.value = null
        viewModelScope.launch { settings.setVocabLevel(level) }
    }

    /** Placement test: [PlacementTest.PER_LEVEL] random words of each level, easy to hard. */
    fun startPlacementTest() {
        viewModelScope.launch {
            val sample = deck.sample(PlacementTest.PER_LEVEL)
            testWords = VocabLevel.entries.flatMap { sample[it].orEmpty() }
            testAnswers = mutableListOf()
            if (testWords.isNotEmpty()) placement.value = Placement.Asking(testWords[0].term, 1, testWords.size)
        }
    }

    fun answerPlacement(known: Boolean) {
        if (placement.value !is Placement.Asking) return
        testAnswers += known
        val next = testAnswers.size
        placement.value = if (next < testWords.size) {
            Placement.Asking(testWords[next].term, next + 1, testWords.size)
        } else {
            val knownByLevel = testWords.zip(testAnswers).filter { it.second }.groupingBy { it.first.level }.eachCount()
            Placement.Result(PlacementTest.level(knownByLevel), known = testAnswers.count { it }, total = testWords.size)
        }
    }

    /** From the result back to the list of levels. */
    fun choosePlacementOther() {
        placement.value = null
    }

    private var cards: MutableList<TrainingCard> = mutableListOf()
    private var directions: MutableList<CardDirection> = mutableListOf()

    /** Per slot: null for a first answer; for a retry, the result of the answer it repeats. */
    private val retryOf: MutableList<AnswerResult?> = mutableListOf()

    /** Per slot: 0 for a first answer, 1.. for the n-th retry of that word. */
    private val retryRound: MutableList<Int> = mutableListOf()

    /** The cards the session started with: the summary counts these, not the retries added on the way. */
    private var originalSize = 0
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
                val prefs = settings.settings.first()
                cards = training.build(practice, prefs.vocabLevel, prefs.sessionSize).toMutableList()
                directions = cards.map {
                    when (direction) {
                        Direction.EN_RU -> CardDirection.EN_RU
                        Direction.RU_EN -> CardDirection.RU_EN
                        Direction.MIXED -> if (Random.nextBoolean()) CardDirection.EN_RU else CardDirection.RU_EN
                    }
                }.toMutableList()
                retryOf.clear()
                retryRound.clear()
                cards.forEach { _ -> retryOf += null; retryRound += 0 }
                originalSize = cards.size
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

    /** «Подсказка»: the first letter; a right answer after it does not move the word up. */
    fun showHint() {
        val phase = session.value as? TrainingPhase.Question ?: return
        if (busy || phase.hint != null) return
        session.value = phase.copy(hint = TrainingSession.hint(cards[index], directions[index]))
    }

    /** «Не знаю»: the answer is shown, the word goes to «Новые». */
    fun dontKnow() = answer(AnswerMode.GAVE_UP)

    fun check() {
        val phase = session.value as? TrainingPhase.Question ?: return
        if (phase.answer.isBlank()) return
        answer(if (phase.hint != null) AnswerMode.HINTED else AnswerMode.TYPED)
    }

    private fun answer(mode: AnswerMode) {
        val phase = session.value as? TrainingPhase.Question ?: return
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                val card = cards[index]
                val direction = directions[index]
                val typed = if (mode == AnswerMode.GAVE_UP) "" else phase.answer
                if (retryOf[index] != null) {
                    answerRetry(phase, card, direction, typed, mode)
                    return@launch
                }
                val result = training.answer(card, direction, typed, wasDue = !practice, mode = mode)
                lastResult = result
                if (result.check.isCorrect) correct++
                progress.add(time.today().toEpochDay())
                widgets.refresh()
                val newWordId = result.newWordId
                // A built-in word that joined «Новые» gets its dictionary card in the background.
                if (newWordId != null && card.deck != null) viewModelScope.launch { deck.enrich(newWordId, card.term) }
                val verdict = when {
                    result.alsoRight != null -> Verdict.ALSO_RIGHT
                    mode == AnswerMode.GAVE_UP -> Verdict.GAVE_UP
                    else -> when (result.check) {
                        is CheckResult.Exact -> Verdict.CORRECT
                        is CheckResult.AcceptedWithTypo -> Verdict.TYPO
                        is CheckResult.Wrong -> Verdict.WRONG
                    }
                }
                val celebration = celebrationFor(result)
                // A wrong answer comes back at the end of the session (the box change already happened).
                if (verdict == Verdict.WRONG || verdict == Verdict.GAVE_UP) queueRetry(card, direction, result, round = 1)
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
                    fromDeck = card.deck != null,
                    hinted = mode == AnswerMode.HINTED,
                    nextReviewDays = nextReviewDays(result),
                    alsoRight = result.alsoRight,
                )
            } finally {
                busy = false
            }
        }
    }

    /**
     * The retry of a word answered wrong: checked, shown, but nothing is written (boxes, mistake count and
     * review log took the first answer). A wrong retry comes round once more, up to [TrainingSession.MAX_RETRIES].
     */
    private suspend fun answerRetry(
        phase: TrainingPhase.Question,
        card: TrainingCard,
        direction: CardDirection,
        typed: String,
        mode: AnswerMode,
    ) {
        val first = retryOf[index]
        val drill = training.drill(card, direction, typed, mode)
        lastResult = first
        val verdict = when {
            drill.alsoRight != null -> Verdict.ALSO_RIGHT
            mode == AnswerMode.GAVE_UP -> Verdict.GAVE_UP
            else -> when (drill.check) {
                is CheckResult.Exact -> Verdict.CORRECT
                is CheckResult.AcceptedWithTypo -> Verdict.TYPO
                is CheckResult.Wrong -> Verdict.WRONG
            }
        }
        val round = retryRound[index]
        val again = (verdict == Verdict.WRONG || verdict == Verdict.GAVE_UP) && round < TrainingSession.MAX_RETRIES
        if (again && first != null) queueRetry(card, direction, first, round + 1)
        if (session.value !== phase) return
        session.value = TrainingPhase.Feedback(
            card = phase.card,
            position = phase.position,
            total = phase.total,
            answer = phase.answer.trim(),
            verdict = verdict,
            correctAnswer = when (val check = drill.check) {
                is CheckResult.AcceptedWithTypo -> check.expected
                else -> shownAnswer(card, direction)
            },
            boxAfter = 1,
            learned = false,
            example = card.example,
            fromDeck = false,
            hinted = mode == AnswerMode.HINTED,
            alsoRight = drill.alsoRight,
            retry = true,
            retryAgain = again,
        )
    }

    private fun queueRetry(card: TrainingCard, direction: CardDirection, first: AnswerResult, round: Int) {
        if (round > TrainingSession.MAX_RETRIES) return
        cards += card
        directions += direction
        retryOf += first
        retryRound += round
    }

    /** The word left training (override, «Я это знаю», «Убрать»): its queued retries go too. */
    private fun dropRetries(card: TrainingCard) {
        var i = cards.lastIndex
        while (i > index) {
            if (retryOf[i] != null && cards[i].term == card.term) {
                cards.removeAt(i)
                directions.removeAt(i)
                retryOf.removeAt(i)
                retryRound.removeAt(i)
            }
            i--
        }
    }

    /** «Я знал, это опечатка»: re-scores the wrong answer as correct and undoes its box change. */
    fun overrideAsTypo() {
        val phase = session.value as? TrainingPhase.Feedback ?: return
        val result = lastResult ?: return
        if (phase.verdict != Verdict.WRONG || phase.removed || busy) return
        busy = true
        viewModelScope.launch {
            try {
                val transition = training.override(cards[index], result, wasDue = !practice)
                correct++
                dropRetries(cards[index])
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

    /** Days until the word comes back, for «повторим через N дней»; null when it is not in a box now. */
    private fun nextReviewDays(result: AnswerResult): Int? {
        val state = result.transition.state
        if (result.transition.learned || state.box !in 1..5 || state.learnedAt != null) return null
        val days = java.time.temporal.ChronoUnit.DAYS.between(
            time.today(),
            java.time.Instant.ofEpochMilli(state.nextReviewAt).atZone(time.zone).toLocalDate(),
        )
        return days.toInt().coerceAtLeast(0)
    }

    fun pronounce() {
        val card = cards.getOrNull(index) ?: return
        pronouncer.play(card.term, card.audioUrl)
    }

    /** «Убрать из изучения» or «Я это знаю» on the feedback: the word leaves training, the session goes on. */
    fun removeWord() = leave { card, result -> training.remove(card, result) }

    fun markKnown() = leave { card, result -> training.markKnown(card, result) }

    private fun leave(action: suspend (TrainingCard, AnswerResult?) -> Unit) {
        val phase = session.value as? TrainingPhase.Feedback ?: return
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                action(cards[index], lastResult)
                dropRetries(cards[index])
                // A wrong first answer that is undone this way no longer counts against the session.
                if (!phase.retry && (phase.verdict == Verdict.WRONG || phase.verdict == Verdict.GAVE_UP)) correct++
                if (session.value === phase) session.value = phase.copy(removed = true)
            } finally {
                busy = false
            }
        }
    }

    override fun onCleared() = pronouncer.release()

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
        session.value = if (index < cards.size) question() else TrainingPhase.Done(
            correct = correct,
            total = originalSize,
            daily = dailyProgress.value,
        )
    }

    fun finish() {
        if (busy) return
        session.value = null
        cards = mutableListOf()
        directions = mutableListOf()
        retryOf.clear()
        retryRound.clear()
        refresh()
        widgets.refresh()
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
                // A retried word is in box 1 by now, whatever box the card was made in.
                box = if (retryOf[index] != null) 1 else card.state.box,
                expectsEnglish = direction == CardDirection.RU_EN,
            ),
            position = index + 1,
            total = cards.size,
            retry = retryOf[index] != null,
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
