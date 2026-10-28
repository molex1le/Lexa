package app.lexa.domain.repository

import app.lexa.domain.model.AnswerTotals
import app.lexa.domain.model.WordCounts
import app.lexa.domain.model.WordSummary
import kotlinx.coroutines.flow.Flow

interface WordRepository {
    /** Words not yet learned: the «Новые слова» list, boxes 1..5. */
    fun observeNotLearned(): Flow<List<WordSummary>>
    fun observeFavorites(): Flow<List<WordSummary>>
    fun observeLearned(): Flow<List<WordSummary>>
    fun observeWord(id: Long): Flow<WordSummary?>
    fun observeCounts(): Flow<WordCounts>
    fun observeDueCount(nowMillis: Long): Flow<Int>
    /** Count of words per box 1..5 among words in learning; missing boxes are 0. */
    fun observeBoxDistribution(): Flow<List<Int>>
}

interface ReviewRepository {
    fun observeAnswerTotals(): Flow<AnswerTotals>
}

interface HistoryRepository {
    fun observeHistory(): Flow<List<app.lexa.domain.model.HistoryItem>>
}
