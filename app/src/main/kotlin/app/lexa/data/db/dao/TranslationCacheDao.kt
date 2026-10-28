package app.lexa.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import app.lexa.data.db.entity.TranslationCacheEntity

/** Used by the translator from phase 2. */
@Dao
interface TranslationCacheDao {
    @Query("SELECT * FROM translation_cache WHERE queryKey = :queryKey AND sourceId = :sourceId")
    suspend fun get(queryKey: String, sourceId: String): TranslationCacheEntity?

    @Upsert
    suspend fun upsert(entry: TranslationCacheEntity)
}
