package app.lexa.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.lexa.data.db.entity.WordEntity
import kotlinx.coroutines.flow.Flow

data class WordCountsRow(val learned: Int, val learning: Int, val new: Int)

data class BoxCountRow(val box: Int, val n: Int)

@Dao
interface WordDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(word: WordEntity): Long

    /** Includes soft-deleted rows: termKey is unique across all of them. */
    @Query("SELECT * FROM words WHERE termKey = :termKey")
    suspend fun findByKey(termKey: String): WordEntity?

    @Query("UPDATE words SET deletedAt = NULL WHERE id = :id")
    suspend fun restore(id: Long)

    @Query("DELETE FROM words WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE words SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("SELECT * FROM words WHERE deletedAt IS NULL AND learnedAt IS NULL ORDER BY addedAt DESC")
    fun observeNotLearned(): Flow<List<WordEntity>>

    @Query("SELECT * FROM words WHERE deletedAt IS NULL AND isFavorite = 1 ORDER BY addedAt DESC")
    fun observeFavorites(): Flow<List<WordEntity>>

    @Query("SELECT * FROM words WHERE deletedAt IS NULL AND learnedAt IS NOT NULL ORDER BY learnedAt DESC")
    fun observeLearned(): Flow<List<WordEntity>>

    @Query("SELECT * FROM words WHERE id = :id")
    suspend fun find(id: Long): WordEntity?

    @Query("SELECT * FROM words WHERE id = :id AND deletedAt IS NULL")
    fun observeById(id: Long): Flow<WordEntity?>

    @Query(
        """
        SELECT
          COALESCE(SUM(learnedAt IS NOT NULL), 0)          AS learned,
          COALESCE(SUM(learnedAt IS NULL AND box >= 2), 0) AS learning,
          COALESCE(SUM(learnedAt IS NULL AND box = 1), 0)  AS new
        FROM words WHERE deletedAt IS NULL
        """,
    )
    fun observeCounts(): Flow<WordCountsRow>

    @Query("SELECT COUNT(*) FROM words WHERE deletedAt IS NULL AND learnedAt IS NULL AND nextReviewAt <= :now")
    fun observeDueCount(now: Long): Flow<Int>

    @Query("SELECT box, COUNT(*) AS n FROM words WHERE deletedAt IS NULL AND learnedAt IS NULL GROUP BY box")
    fun observeBoxCounts(): Flow<List<BoxCountRow>>

    /** The training queue (ARCHITECTURE §9): new words first, oldest added first. */
    @Query(
        """
        SELECT * FROM words WHERE deletedAt IS NULL AND learnedAt IS NULL AND nextReviewAt <= :now
        ORDER BY box ASC, addedAt ASC, id ASC LIMIT :limit
        """,
    )
    suspend fun due(now: Long, limit: Int): List<WordEntity>

    /** Words in learning with mistakes, most mistakes first: practice outside the schedule. */
    @Query(
        """
        SELECT * FROM words WHERE deletedAt IS NULL AND learnedAt IS NULL AND mistakesCount > 0
        ORDER BY mistakesCount DESC, addedAt ASC LIMIT :limit
        """,
    )
    suspend fun hardest(limit: Int): List<WordEntity>

    @Query(
        """
        UPDATE words SET box = :box, nextReviewAt = :nextReviewAt, mistakesCount = :mistakesCount,
          learnedAt = :learnedAt, lastReviewedAt = :reviewedAt
        WHERE id = :id
        """,
    )
    suspend fun updateSchedule(id: Long, box: Int, nextReviewAt: Long, mistakesCount: Int, learnedAt: Long?, reviewedAt: Long)
}
