package app.lexa.domain.repository

import app.lexa.domain.model.AnswerTotals
import app.lexa.domain.model.WordCounts
import app.lexa.domain.model.WordDetails
import app.lexa.domain.model.WordSummary
import app.lexa.domain.stats.Accuracy
import app.lexa.domain.stats.LearnedPeriods
import app.lexa.domain.stats.Streak
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

interface WordRepository {
    /** Words not yet learned: the «Новые слова» list, boxes 1..5. */
    fun observeNotLearned(): Flow<List<WordSummary>>
    fun observeFavorites(): Flow<List<WordSummary>>
    /** Learned words, most recently learned first. */
    fun observeLearned(): Flow<List<WordSummary>>
    fun observeWord(id: Long): Flow<WordSummary?>
    fun observeDetails(id: Long): Flow<WordDetails?>
    fun observeCounts(): Flow<WordCounts>
    fun observeDueCount(nowMillis: Long): Flow<Int>
    /** Count of words per box 1..5 among words in learning; missing boxes are 0. */
    fun observeBoxDistribution(): Flow<List<Int>>

    suspend fun setFavorite(id: Long, favorite: Boolean)
    /** Hides the word everywhere until [restore] or [purge] (docs/ARCHITECTURE.md §6.1). */
    suspend fun softDelete(id: Long)
    suspend fun restore(id: Long)
    /** Deletes a soft-deleted word for good, with its review history. A live word is left alone. */
    suspend fun purge(id: Long)
    /** Deletes every word soft-deleted before [beforeMillis]: leftovers of a killed process. */
    suspend fun purgeDeletedBefore(beforeMillis: Long)
    /** A learned word goes back to box 1, due today. */
    suspend fun returnToLearning(id: Long)
    /** «Я это знаю»: the word counts as learned now. */
    suspend fun markLearned(id: Long)
}

interface ReviewRepository {
    fun observeAnswerTotals(): Flow<AnswerTotals>
}

/** Statistics derived from `words` and `review_log` only (docs/ARCHITECTURE.md §7); [today] is in the user's zone. */
interface StatsRepository {
    fun observeLearnedPeriods(today: LocalDate): Flow<LearnedPeriods>
    /** Learned per day for [days] days ending [today], oldest first; empty days are 0. */
    fun observeLearnedByDay(today: LocalDate, days: Int): Flow<List<Int>>
    fun observeStreak(today: LocalDate): Flow<Streak>
    fun observeAccuracy(today: LocalDate): Flow<Accuracy>
    /** Words in learning with mistakes, most first: the ones practice mode trains. */
    fun observeHardWords(limit: Int): Flow<List<WordSummary>>
}

interface HistoryRepository {
    fun observeHistory(): Flow<List<app.lexa.domain.model.HistoryItem>>
    /** One row per lookup, repeats included (docs/ARCHITECTURE.md §6.4). */
    suspend fun record(query: String, wordId: Long?)
    /** Forgets every past lookup; the words themselves stay. */
    suspend fun clear()
}
