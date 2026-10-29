package app.lexa.data.repository

import androidx.room.withTransaction
import app.lexa.data.db.LexaDatabase
import app.lexa.data.db.entity.ExampleSource
import app.lexa.data.db.entity.ReviewDirection
import app.lexa.data.db.entity.ReviewLogEntity
import app.lexa.data.db.entity.ReviewOutcome
import app.lexa.data.db.entity.WordEntity
import app.lexa.domain.leitner.LeitnerRules
import app.lexa.domain.leitner.ReviewState
import app.lexa.domain.text.normalizeKey
import app.lexa.domain.model.Example
import app.lexa.domain.model.ExampleOrigin
import app.lexa.domain.training.ReviewRecord
import app.lexa.domain.training.TrainingCard
import app.lexa.domain.training.TrainingRepository
import javax.inject.Inject

internal fun WordEntity.toCard() = TrainingCard(
    wordId = id,
    term = term,
    translations = translations.map { it.text },
    ipa = ipa,
    // Prefer an example with a Russian translation: the feedback shows both lines.
    example = (examples.firstOrNull { it.ru != null } ?: examples.firstOrNull())?.let {
        Example(
            en = it.en,
            ru = it.ru,
            origin = when (it.source) {
                ExampleSource.TATOEBA -> ExampleOrigin.TATOEBA
                ExampleSource.DICTIONARY -> ExampleOrigin.DICTIONARY
                ExampleSource.MACHINE, ExampleSource.LLM -> ExampleOrigin.MACHINE
            },
        )
    },
    state = ReviewState(box = box, nextReviewAt = nextReviewAt, mistakesCount = mistakesCount, learnedAt = learnedAt),
    synonyms = synonyms,
    audioUrl = audioUrl,
)

class RoomTrainingRepository @Inject constructor(private val db: LexaDatabase) : TrainingRepository {
    private val words get() = db.wordDao()
    private val log get() = db.reviewLogDao()

    override suspend fun dueCards(nowMillis: Long, limit: Int): List<TrainingCard> =
        words.due(nowMillis, limit).map { it.toCard() }

    override suspend fun hardCards(limit: Int): List<TrainingCard> = words.hardest(limit).map { it.toCard() }

    override suspend fun saveAnswer(state: ReviewState, reviewedAt: Long, record: ReviewRecord): Long =
        db.withTransaction {
            words.updateSchedule(record.wordId, state.box, state.nextReviewAt, state.mistakesCount, state.learnedAt, reviewedAt)
            log.insert(
                ReviewLogEntity(
                    wordId = record.wordId,
                    answeredAt = record.answeredAt,
                    localDay = record.localDay,
                    direction = ReviewDirection.valueOf(record.direction.name),
                    userAnswer = record.userAnswer,
                    outcome = ReviewOutcome.valueOf(record.outcome.name),
                    isCorrect = record.isCorrect,
                    fromBox = record.fromBox,
                    toBox = record.toBox,
                    wasDue = record.wasDue,
                ),
            )
        }

    override suspend fun overrideAnswer(wordId: Long, state: ReviewState, logId: Long, toBox: Int) =
        db.withTransaction {
            val reviewedAt = words.find(wordId)?.lastReviewedAt ?: state.nextReviewAt
            words.updateSchedule(wordId, state.box, state.nextReviewAt, state.mistakesCount, state.learnedAt, reviewedAt)
            log.markOverridden(logId, toBox)
        }

    override suspend fun translationsOf(term: String): List<String> =
        words.findByKey(normalizeKey(term))?.takeIf { it.deletedAt == null }?.translations?.map { it.text }.orEmpty()

    override suspend fun remove(wordId: Long) = words.deleteById(wordId)

    override suspend fun markLearned(wordId: Long, nowMillis: Long) {
        val word = words.find(wordId) ?: return
        words.updateSchedule(wordId, LeitnerRules.LAST_BOX, word.nextReviewAt, word.mistakesCount, nowMillis, nowMillis)
    }
}
