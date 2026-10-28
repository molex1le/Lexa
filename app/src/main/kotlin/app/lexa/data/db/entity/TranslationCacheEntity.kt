package app.lexa.data.db.entity

import androidx.room.Entity

/** docs/ARCHITECTURE.md §6.3. `assembled` rows hold a ready entry for offline repeats. */
@Entity(tableName = "translation_cache", primaryKeys = ["queryKey", "sourceId"])
data class TranslationCacheEntity(
    val queryKey: String,
    val sourceId: String,
    val schemaVersion: Int,
    val payload: String,
    val status: CacheStatus,
    val fetchedAt: Long,
)
