package app.lexa.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import app.lexa.data.db.converter.JsonConverters
import app.lexa.data.db.dao.HistoryDao
import app.lexa.data.db.dao.ReviewLogDao
import app.lexa.data.db.dao.TranslationCacheDao
import app.lexa.data.db.dao.WordDao
import app.lexa.data.db.entity.ReviewLogEntity
import app.lexa.data.db.entity.TranslationCacheEntity
import app.lexa.data.db.entity.TranslationHistoryEntity
import app.lexa.data.db.entity.WordEntity

@Database(
    entities = [WordEntity::class, ReviewLogEntity::class, TranslationCacheEntity::class, TranslationHistoryEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(JsonConverters::class)
abstract class LexaDatabase : RoomDatabase() {
    abstract fun wordDao(): WordDao
    abstract fun reviewLogDao(): ReviewLogDao
    abstract fun translationCacheDao(): TranslationCacheDao
    abstract fun historyDao(): HistoryDao

    companion object {
        const val NAME = "lexa.db"
    }
}
