package app.lexa.data.repository

import app.lexa.data.db.dao.HistoryDao
import app.lexa.data.db.dao.ReviewLogDao
import app.lexa.data.db.dao.WordDao
import app.lexa.data.db.entity.EntryPoint
import app.lexa.data.db.entity.ExampleSource
import app.lexa.domain.model.Example
import app.lexa.domain.model.ExampleOrigin
import app.lexa.domain.model.PartOfSpeech
import app.lexa.domain.model.Sense
import app.lexa.domain.model.Translation
import app.lexa.domain.model.WordDetails
import app.lexa.domain.repository.StatsRepository
import app.lexa.domain.stats.Accuracy
import app.lexa.domain.stats.LearnedPeriods
import app.lexa.domain.stats.Streak
import app.lexa.domain.stats.StreakCalculator
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.flow.combine
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
    lastReviewedAt = lastReviewedAt,
)

/** The stored entry back in the translator's model: translations grouped by part of speech, in stored order. */
internal fun WordEntity.toDetails() = WordDetails(
    summary = toSummary(),
    entry = Translation(
        term = term,
        ipa = ipa,
        audioUrl = audioUrl,
        senses = translations.groupBy { it.pos }.map { (pos, items) ->
            Sense(pos = pos?.let { PartOfSpeech.parse(it) }, translations = items.map { it.text })
        },
        definition = definition,
        examples = examples.map { Example(en = it.en, ru = it.ru, origin = it.source.toOrigin()) },
        synonyms = synonyms,
    ),
)

private fun ExampleSource.toOrigin() = when (this) {
    ExampleSource.TATOEBA -> ExampleOrigin.TATOEBA
    ExampleSource.DICTIONARY -> ExampleOrigin.DICTIONARY
    else -> ExampleOrigin.MACHINE
}

private fun LocalDate.startMillis(zone: ZoneId) = atStartOfDay(zone).toInstant().toEpochMilli()

class RoomWordRepository @Inject constructor(
    private val dao: WordDao,
    private val time: TimeProvider,
) : WordRepository {
    override fun observeNotLearned(): Flow<List<WordSummary>> =
        dao.observeNotLearned().map { list -> list.map { it.toSummary() } }

    override fun observeFavorites(): Flow<List<WordSummary>> =
        dao.observeFavorites().map { list -> list.map { it.toSummary() } }

    override fun observeLearned(): Flow<List<WordSummary>> =
        dao.observeLearned().map { list -> list.map { it.toSummary() } }

    override fun observeWord(id: Long): Flow<WordSummary?> = dao.observeById(id).map { it?.toSummary() }

    override fun observeDetails(id: Long): Flow<WordDetails?> = dao.observeById(id).map { it?.toDetails() }

    override fun observeCounts(): Flow<WordCounts> =
        dao.observeCounts().map { WordCounts(learned = it.learned, learning = it.learning, new = it.new) }

    override fun observeDueCount(nowMillis: Long): Flow<Int> = dao.observeDueCount(nowMillis)

    override fun observeBoxDistribution(): Flow<List<Int>> = dao.observeBoxCounts().map { rows ->
        val byBox = rows.associate { it.box to it.n }
        LeitnerRules.boxes.map { byBox[it] ?: 0 }
    }

    override suspend fun setFavorite(id: Long, favorite: Boolean) = dao.setFavorite(id, favorite)

    override suspend fun softDelete(id: Long) = dao.softDelete(id, time.nowMillis())

    override suspend fun restore(id: Long) = dao.restore(id)

    override suspend fun purge(id: Long) = dao.purge(id)

    override suspend fun purgeDeletedBefore(beforeMillis: Long) = dao.purgeDeletedBefore(beforeMillis)

    override suspend fun returnToLearning(id: Long) = dao.returnToLearning(id, time.today().startMillis(time.zone))
}

class RoomStatsRepository @Inject constructor(
    private val words: WordDao,
    private val reviews: ReviewLogDao,
    private val time: TimeProvider,
) : StatsRepository {
    private val zone get() = time.zone

    override fun observeLearnedPeriods(today: LocalDate): Flow<LearnedPeriods> = words.observeLearnedPeriods(
        todayStart = today.startMillis(zone),
        weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).startMillis(zone),
        monthStart = today.withDayOfMonth(1).startMillis(zone),
    ).map { LearnedPeriods(today = it.today, week = it.week, month = it.month) }

    override fun observeLearnedByDay(today: LocalDate, days: Int): Flow<List<Int>> {
        val first = today.minusDays(days - 1L)
        return words.observeLearnedSince(first.startMillis(zone)).map { learned ->
            val bins = IntArray(days)
            learned.forEach { millis ->
                val index = StreakCalculator.localDay(Instant.ofEpochMilli(millis), zone) - first.toEpochDay()
                if (index in 0 until days) bins[index.toInt()]++
            }
            bins.toList()
        }
    }

    override fun observeStreak(today: LocalDate): Flow<Streak> =
        reviews.observePracticeDays().map { StreakCalculator.compute(it, today.toEpochDay()) }

    override fun observeAccuracy(today: LocalDate): Flow<Accuracy> = combine(
        reviews.observeTotalsSince(today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toEpochDay()),
        reviews.observeTotals(),
    ) { week, all ->
        Accuracy(
            week = AnswerTotals(answers = week.answers, correct = week.correct),
            all = AnswerTotals(answers = all.answers, correct = all.correct),
        )
    }

    override fun observeHardWords(limit: Int): Flow<List<WordSummary>> =
        words.observeHardest(limit).map { list -> list.map { it.toSummary() } }
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
