package app.lexa.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import app.lexa.data.db.entity.ReviewLogEntity
import kotlinx.coroutines.flow.Flow

data class AnswerTotalsRow(val answers: Int, val correct: Int)

@Dao
interface ReviewLogDao {
    @Insert
    suspend fun insert(log: ReviewLogEntity): Long

    @Query("SELECT COUNT(*) AS answers, COALESCE(SUM(isCorrect), 0) AS correct FROM review_log")
    fun observeTotals(): Flow<AnswerTotalsRow>
}
