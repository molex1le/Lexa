package app.lexa.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.lexa.data.db.entity.DeckSeenEntity

@Dao
interface DeckDao {
    @Query("SELECT term FROM deck_seen")
    suspend fun seenTerms(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(seen: DeckSeenEntity)
}
