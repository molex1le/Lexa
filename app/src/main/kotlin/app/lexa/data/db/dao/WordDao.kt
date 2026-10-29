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
}
