package app.lexa.domain.training

import app.lexa.domain.leitner.LeitnerScheduler
import app.lexa.domain.leitner.ReviewState
import app.lexa.domain.leitner.Transition
import app.lexa.domain.model.Example
import app.lexa.domain.model.VocabLevel
import app.lexa.domain.time.TimeProvider
import javax.inject.Inject

/** One word as a training card. [translations] are stored strings; candidates are split from them. */
data class TrainingCard(
    val wordId: Long,
    val term: String,
    val translations: List<String>,
    val ipa: String? = null,
    val example: Example? = null,
    val state: ReviewState,
    /** Set for a built-in word not yet in the list ([wordId] is 0 then). */
    val deck: DeckWord? = null,
    /** English synonyms from the dictionary: accepted (without credit) in RU→EN. */
    val synonyms: List<String> = emptyList(),
    val audioUrl: String? = null,
)

/** How the answer was given besides the typed text. */
enum class AnswerMode {
    TYPED,
    /** The first letter was shown: a right answer does not move the word up. */
    HINTED,
    /** «Не знаю»: counted as wrong, no typo override. */
    GAVE_UP,
}

/** The side shown on one card: EN_RU shows the term and expects Russian. */
enum class CardDirection { EN_RU, RU_EN }

enum class AnswerOutcome { CORRECT, CORRECT_TYPO, WRONG, OVERRIDDEN }

/** One row of review_log (docs/ARCHITECTURE.md §6.2). */
data class ReviewRecord(
    val wordId: Long,
    val answeredAt: Long,
    val localDay: Long,
    val direction: CardDirection,
    val userAnswer: String,
    val outcome: AnswerOutcome,
    val fromBox: Int,
    val toBox: Int,
    val wasDue: Boolean,
) {
    val isCorrect: Boolean get() = outcome != AnswerOutcome.WRONG
}

interface TrainingRepository {
    /** Not learned, not deleted, nextReviewAt ≤ [nowMillis]; box 1 first, then oldest added. */
    suspend fun dueCards(nowMillis: Long, limit: Int): List<TrainingCard>

    /** Not learned, most mistakes first: out-of-schedule practice of hard words. */
    suspend fun hardCards(limit: Int): List<TrainingCard>

    /** Writes the new word state and the log row in one transaction; returns the log row id. */
    suspend fun saveAnswer(state: ReviewState, reviewedAt: Long, record: ReviewRecord): Long

    /** «Я знал, это опечатка»: new word state, and the same log row becomes OVERRIDDEN with [toBox]. */
    suspend fun overrideAnswer(wordId: Long, state: ReviewState, logId: Long, toBox: Int)

    /** Russian translations of an English word in the learner's list, or empty. */
    suspend fun translationsOf(term: String): List<String>

    /** «Убрать из изучения»: the word leaves the list for good. */
    suspend fun remove(wordId: Long)

    /** «Я это знаю»: the word counts as learned now and leaves training. */
    suspend fun markLearned(wordId: Long, nowMillis: Long)
}

/** What one answer did. [logId] is needed to undo it with «Я знал, это опечатка». */
data class AnswerResult(
    val check: CheckResult,
    val transition: Transition,
    val logId: Long,
    /** For a deck card answered wrong: the id of the word it became in «Новые». */
    val newWordId: Long? = null,
    /** RU→EN: the answer was another right English word; this is the one being learned. No change. */
    val alsoRight: String? = null,
)

/**
 * A training session (docs/ARCHITECTURE.md §9): builds the queue, checks each answer, moves the word
 * between boxes and writes a review_log row per answer.
 */
class TrainingSession @Inject constructor(
    private val repository: TrainingRepository,
    private val deck: DeckRepository,
    private val scheduler: LeitnerScheduler,
    private val time: TimeProvider,
    private val random: kotlin.random.Random = kotlin.random.Random.Default,
) {
    /**
     * Hard words when [practice] (those answers do not promote, §17). Otherwise due words (at most
     * [MAX_DUE], so new words always get a place) topped up with random unseen words of [level],
     * shuffled together.
     */
    suspend fun build(practice: Boolean = false, level: VocabLevel? = null, limit: Int = SESSION_SIZE): List<TrainingCard> {
        if (practice) return repository.hardCards(limit)
        val due = repository.dueCards(time.nowMillis(), if (level == null) limit else maxDue(limit))
        if (level == null) return due
        // At most DAILY_NEW_LIMIT unknown built-in words a day go to «Новые»; past that, only review.
        val addedToday = deck.addedSince(time.startOfToday())
        val room = if (addedToday >= DAILY_NEW_LIMIT) 0 else limit - due.size
        val fresh = deck.randomUnseen(level, room).map { it.toCard() }
        return (due + fresh).shuffled(random)
    }

    suspend fun answer(
        card: TrainingCard,
        direction: CardDirection,
        userAnswer: String,
        wasDue: Boolean,
        mode: AnswerMode = AnswerMode.TYPED,
    ): AnswerResult {
        val expected = expected(card, direction)
        val check = if (mode == AnswerMode.GAVE_UP) CheckResult.Wrong(expected)
        else AnswerChecker.check(userAnswer, expected, language(direction))
        if (check is CheckResult.Wrong && mode != AnswerMode.GAVE_UP && direction == CardDirection.RU_EN &&
            isAnotherRightWord(card, userAnswer)
        ) {
            return AnswerResult(check, Transition(card.state, card.state.box), logId = -1, alsoRight = card.term)
        }
        val deckWord = card.deck
        if (deckWord != null) {
            // With a hint the word is not really known yet: it joins «Новые» like a wrong one.
            val known = check.isCorrect && mode != AnswerMode.HINTED
            return answerDeck(card, deckWord, direction, userAnswer, check, known)
        }
        val promote = wasDue && mode != AnswerMode.HINTED
        val transition = if (check.isCorrect) scheduler.onCorrect(card.state, promote) else scheduler.onWrong(card.state)
        val outcome = when (check) {
            is CheckResult.Exact -> AnswerOutcome.CORRECT
            is CheckResult.AcceptedWithTypo -> AnswerOutcome.CORRECT_TYPO
            is CheckResult.Wrong -> AnswerOutcome.WRONG
        }
        val now = time.nowMillis()
        val record = ReviewRecord(
            wordId = card.wordId,
            answeredAt = now,
            localDay = time.today().toEpochDay(),
            direction = direction,
            userAnswer = userAnswer.trim(),
            outcome = outcome,
            fromBox = card.state.box,
            toBox = transition.toBox,
            wasDue = wasDue,
        )
        val logId = repository.saveAnswer(transition.state, now, record)
        return AnswerResult(check, transition, logId)
    }

    /** Re-scores a wrong answer as correct from the card's state before it; no new log row. */
    suspend fun override(card: TrainingCard, result: AnswerResult, wasDue: Boolean): Transition {
        val deckWord = card.deck
        val newWordId = result.newWordId
        if (deckWord != null && newWordId != null) {
            deck.undoAddAsNew(newWordId, deckWord.term)
            return Transition(card.state, DECK_KNOWN)
        }
        val transition = scheduler.onOverride(card.state, wasDue)
        repository.overrideAnswer(card.wordId, transition.state, result.logId, transition.toBox)
        return transition
    }

    /**
     * A built-in word: known → it is never shown again; not known → it joins «Новые» in box 1 with
     * the wrong answer logged, exactly like a word that fell back from a higher box.
     */
    private suspend fun answerDeck(
        card: TrainingCard,
        word: DeckWord,
        direction: CardDirection,
        userAnswer: String,
        check: CheckResult,
        known: Boolean,
    ): AnswerResult {
        if (known) {
            deck.markKnown(word.term)
            return AnswerResult(check, Transition(card.state, DECK_KNOWN), logId = -1)
        }
        val now = time.nowMillis()
        val transition = scheduler.onWrong(card.state.copy(box = 1, nextReviewAt = now))
        val record = ReviewRecord(
            wordId = 0,
            answeredAt = now,
            localDay = time.today().toEpochDay(),
            direction = direction,
            userAnswer = userAnswer.trim(),
            outcome = AnswerOutcome.WRONG,
            fromBox = 1,
            toBox = transition.toBox,
            wasDue = false,
        )
        val (wordId, logId) = deck.addAsNew(word, record)
        return AnswerResult(check, transition, logId, newWordId = wordId)
    }

    /**
     * RU→EN: «trip» for «поездка» when «journey» is taught. Right if the answer is a synonym of the
     * term, or a word (in the list or the built-in set) sharing a Russian translation with it.
     */
    private suspend fun isAnotherRightWord(card: TrainingCard, answer: String): Boolean {
        val given = AnswerChecker.normalize(answer, AnswerLanguage.ENGLISH)
        if (given.isEmpty() || given == AnswerChecker.normalize(card.term, AnswerLanguage.ENGLISH)) return false
        if (card.synonyms.any { AnswerChecker.normalize(it, AnswerLanguage.ENGLISH) == given }) return true
        val theirs = repository.translationsOf(answer.trim()) + listOfNotNull(deck.translationsOf(answer.trim()))
        if (theirs.isEmpty()) return false
        val ours = AnswerChecker.candidates(card.translations).map { AnswerChecker.normalize(it, AnswerLanguage.RUSSIAN) }.toSet()
        return AnswerChecker.candidates(theirs).any { AnswerChecker.normalize(it, AnswerLanguage.RUSSIAN) in ours }
    }

    /** «Убрать из изучения». A built-in word answered wrong leaves too and is never offered again. */
    suspend fun remove(card: TrainingCard, result: AnswerResult?) {
        val deckWord = card.deck
        val newWordId = result?.newWordId
        when {
            deckWord != null && newWordId != null -> deck.undoAddAsNew(newWordId, deckWord.term)
            deckWord != null -> deck.markKnown(deckWord.term)
            else -> repository.remove(card.wordId)
        }
    }

    /** «Я это знаю»: learned now; a built-in word is simply never offered again. */
    suspend fun markKnown(card: TrainingCard, result: AnswerResult?) {
        val deckWord = card.deck
        val newWordId = result?.newWordId
        when {
            deckWord != null && newWordId != null -> deck.undoAddAsNew(newWordId, deckWord.term)
            deckWord != null -> deck.markKnown(deckWord.term)
            else -> repository.markLearned(card.wordId, time.nowMillis())
        }
    }

    companion object {
        const val SESSION_SIZE = 20
        /** Unknown built-in words that may join «Новые» per day. */
        const val DAILY_NEW_LIMIT = 10

        /** Due words take at most 60 % of a session, so new words always get a place. */
        fun maxDue(limit: Int): Int = maxOf(1, limit * 3 / 5)

        /** «j _ _ _ _ _ _»: the first letter of the first answer, then a slot per letter. */
        fun hint(card: TrainingCard, direction: CardDirection): String {
            val answer = expected(card, direction).firstOrNull()?.removePrefix("to ") ?: return ""
            return answer.mapIndexed { i, c -> if (i == 0 || c == ' ') c.toString() else "_" }.joinToString(" ")
        }
        /** [Transition.toBox] of a deck word answered correctly: known, not entered into the boxes. */
        const val DECK_KNOWN = -1

        fun DeckWord.toCard() = TrainingCard(
            wordId = 0,
            term = term,
            translations = listOf(translations),
            state = ReviewState(box = 0, nextReviewAt = 0, mistakesCount = 0),
            deck = this,
        )

        fun expected(card: TrainingCard, direction: CardDirection): List<String> = when (direction) {
            CardDirection.EN_RU -> AnswerChecker.candidates(card.translations)
            CardDirection.RU_EN -> listOf(card.term)
        }

        fun language(direction: CardDirection): AnswerLanguage = when (direction) {
            CardDirection.EN_RU -> AnswerLanguage.RUSSIAN
            CardDirection.RU_EN -> AnswerLanguage.ENGLISH
        }
    }
}
