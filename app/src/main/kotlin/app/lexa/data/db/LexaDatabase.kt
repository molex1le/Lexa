package app.lexa.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import app.lexa.data.db.converter.JsonConverters
import app.lexa.data.db.dao.DeckDao
import app.lexa.data.db.dao.HistoryDao
import app.lexa.data.db.dao.ReviewLogDao
import app.lexa.data.db.dao.TranslationCacheDao
import app.lexa.data.db.dao.WordDao
import app.lexa.data.db.entity.DeckSeenEntity
import app.lexa.data.db.entity.ReviewLogEntity
import app.lexa.data.db.entity.TranslationCacheEntity
import app.lexa.data.db.entity.TranslationHistoryEntity
import app.lexa.data.db.entity.WordEntity

@Database(
    entities = [
        WordEntity::class, ReviewLogEntity::class, TranslationCacheEntity::class, TranslationHistoryEntity::class,
        DeckSeenEntity::class,
    ],
    version = 2,
    exportSchema = true,
    // 2: deck_seen for the built-in words in training.
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
@TypeConverters(JsonConverters::class)
abstract class LexaDatabase : RoomDatabase() {
    abstract fun wordDao(): WordDao
    abstract fun reviewLogDao(): ReviewLogDao
    abstract fun translationCacheDao(): TranslationCacheDao
    abstract fun historyDao(): HistoryDao
    abstract fun deckDao(): DeckDao

    companion object {
        const val NAME = "lexa.db"
    }
}
