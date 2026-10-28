package app.lexa.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** docs/ARCHITECTURE.md §6.4. History is queries, not words: a repeat adds a row, never a word. */
@Entity(
    tableName = "translation_history",
    foreignKeys = [
        ForeignKey(
            entity = WordEntity::class,
            parentColumns = ["id"],
            childColumns = ["wordId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index(value = ["translatedAt"]), Index(value = ["wordId"])],
)
data class TranslationHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val query: String,
    val queryKey: String,
    val wordId: Long? = null,
    val translatedAt: Long,
    val entryPoint: EntryPoint,
)
