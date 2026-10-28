package app.lexa.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
data class TranslationJson(
    val text: String,
    val pos: String? = null,
    val isUserEdited: Boolean = false,
)

@Serializable
data class ExampleJson(
    val en: String,
    val ru: String? = null,
    val source: ExampleSource,
)

/**
 * docs/ARCHITECTURE.md §6.1. Status is derived, never stored:
 * new = learnedAt IS NULL AND box = 1, learning = learnedAt IS NULL AND box ≥ 2, learned = learnedAt IS NOT NULL.
 */
@Entity(
    tableName = "words",
    indices = [
        Index(value = ["termKey"], unique = true),
        Index(value = ["learnedAt", "deletedAt", "nextReviewAt"]),
        Index(value = ["isFavorite"]),
        Index(value = ["addedAt"]),
        Index(value = ["mistakesCount"]),
    ],
)
data class WordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val term: String,
    val termKey: String,
    val translations: List<TranslationJson>,
    val definition: String? = null,
    val ipa: String? = null,
    val audioUrl: String? = null,
    val audioLocalPath: String? = null,
    val examples: List<ExampleJson> = emptyList(),
    val synonyms: List<String> = emptyList(),
    @ColumnInfo(defaultValue = "0") val isFavorite: Boolean = false,
    @ColumnInfo(defaultValue = "1") val box: Int = 1,
    val nextReviewAt: Long,
    @ColumnInfo(defaultValue = "0") val mistakesCount: Int = 0,
    val addedAt: Long,
    val learnedAt: Long? = null,
    val lastReviewedAt: Long? = null,
    val source: WordSource,
    val searchText: String,
    val deletedAt: Long? = null,
)
