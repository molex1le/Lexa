package app.lexa.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import app.lexa.data.db.entity.TranslationHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Insert
    suspend fun insert(entry: TranslationHistoryEntity): Long

    @Query("SELECT * FROM translation_history ORDER BY translatedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 200): Flow<List<TranslationHistoryEntity>>
}
