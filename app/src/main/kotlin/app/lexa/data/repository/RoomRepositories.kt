package app.lexa.data.repository

import app.lexa.data.db.dao.HistoryDao
import app.lexa.data.db.dao.ReviewLogDao
import app.lexa.data.db.dao.WordDao
import app.lexa.data.db.entity.EntryPoint
import app.lexa.data.db.entity.TranslationHistoryEntity
import app.lexa.data.db.entity.WordEntity
import app.lexa.domain.text.normalizeKey
import app.lexa.domain.time.TimeProvider
import app.lexa.domain.leitner.LeitnerRules
import app.lexa.domain.model.AnswerTotals
import app.lexa.domain.model.HistoryItem
import app.lexa.domain.model.WordCounts
import app.lexa.domain.model.WordSummary
import app.lexa.domain.repository.HistoryRepository
import app.lexa.domain.repository.ReviewRepository
import app.lexa.domain.repository.WordRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal fun WordEntity.toSummary() = WordSummary(
    id = id,
    term = term,
    translation = translations.joinToString(", ") { it.text },
    isFavorite = isFavorite,
    box = box,
    mistakesCount = mistakesCount,
    addedAt = addedAt,
    learnedAt = learnedAt,
)

class RoomWordRepository @Inject constructor(private val dao: WordDao) : WordRepository {
    override fun observeNotLearned(): Flow<List<WordSummary>> =
        dao.observeNotLearned().map { list -> list.map { it.toSummary() } }

    override fun observeFavorites(): Flow<List<WordSummary>> =
        dao.observeFavorites().map { list -> list.map { it.toSummary() } }

    override fun observeLearned(): Flow<List<WordSummary>> =
        dao.observeLearned().map { list -> list.map { it.toSummary() } }

    override fun observeWord(id: Long): Flow<WordSummary?> = dao.observeById(id).map { it?.toSummary() }

    override fun observeCounts(): Flow<WordCounts> =
        dao.observeCounts().map { WordCounts(learned = it.learned, learning = it.learning, new = it.new) }

    override fun observeDueCount(nowMillis: Long): Flow<Int> = dao.observeDueCount(nowMillis)

    override fun observeBoxDistribution(): Flow<List<Int>> = dao.observeBoxCounts().map { rows ->
        val byBox = rows.associate { it.box to it.n }
        LeitnerRules.boxes.map { byBox[it] ?: 0 }
    }
}

class RoomReviewRepository @Inject constructor(private val dao: ReviewLogDao) : ReviewRepository {
    override fun observeAnswerTotals(): Flow<AnswerTotals> =
        dao.observeTotals().map { AnswerTotals(answers = it.answers, correct = it.correct) }
}

class RoomHistoryRepository @Inject constructor(
    private val dao: HistoryDao,
    private val time: TimeProvider,
) : HistoryRepository {
    override suspend fun record(query: String, wordId: Long?) {
        dao.insert(
            TranslationHistoryEntity(
                query = query.trim(),
                queryKey = normalizeKey(query),
                wordId = wordId,
                translatedAt = time.nowMillis(),
                entryPoint = EntryPoint.APP,
            ),
        )
    }

    override fun observeHistory(): Flow<List<HistoryItem>> = dao.observeRecent().map { list ->
        list.map { HistoryItem(id = it.id, query = it.query, translatedAt = it.translatedAt) }
    }
}
