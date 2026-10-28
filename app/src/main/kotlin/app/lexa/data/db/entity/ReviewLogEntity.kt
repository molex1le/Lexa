package app.lexa.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** docs/ARCHITECTURE.md §6.2. `toBox = 0` means the word left box 5 and is learned. */
@Entity(
    tableName = "review_log",
    foreignKeys = [
        ForeignKey(
            entity = WordEntity::class,
            parentColumns = ["id"],
            childColumns = ["wordId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["localDay"]), Index(value = ["wordId", "answeredAt"])],
)
data class ReviewLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val wordId: Long,
    val answeredAt: Long,
    /** LocalDate.toEpochDay() in the user's zone at answer time. */
    val localDay: Long,
    val direction: ReviewDirection,
    val userAnswer: String,
    val outcome: ReviewOutcome,
    val isCorrect: Boolean,
    val fromBox: Int,
    val toBox: Int,
    val wasDue: Boolean,
)
