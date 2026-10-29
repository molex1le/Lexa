package app.lexa.domain.training

import app.lexa.domain.leitner.LeitnerScheduler
import app.lexa.domain.leitner.ReviewState
import app.lexa.domain.leitner.Transition
import app.lexa.domain.model.Example
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
)

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
}

/** What one answer did. [logId] is needed to undo it with «Я знал, это опечатка». */
data class AnswerResult(
    val check: CheckResult,
    val transition: Transition,
    val logId: Long,
)

/**
 * A training session (docs/ARCHITECTURE.md §9): builds the queue, checks each answer, moves the word
 * between boxes and writes a review_log row per answer.
 */
class TrainingSession @Inject constructor(
    private val repository: TrainingRepository,
    private val scheduler: LeitnerScheduler,
    private val time: TimeProvider,
) {
    /** Due words, or hard words when [practice] (those answers do not promote, §17). */
    suspend fun build(practice: Boolean = false, limit: Int = SESSION_SIZE): List<TrainingCard> =
        if (practice) repository.hardCards(limit) else repository.dueCards(time.nowMillis(), limit)

    suspend fun answer(card: TrainingCard, direction: CardDirection, userAnswer: String, wasDue: Boolean): AnswerResult {
        val check = AnswerChecker.check(userAnswer, expected(card, direction), language(direction))
        val transition = if (check.isCorrect) scheduler.onCorrect(card.state, wasDue) else scheduler.onWrong(card.state)
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
    suspend fun override(card: TrainingCard, logId: Long, wasDue: Boolean): Transition {
        val transition = scheduler.onOverride(card.state, wasDue)
        repository.overrideAnswer(card.wordId, transition.state, logId, transition.toBox)
        return transition
    }

    companion object {
        const val SESSION_SIZE = 20

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
