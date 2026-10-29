package app.lexa.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A built-in word already shown in training: [known] ones never come back, unknown ones are in «Новые». */
@Entity(tableName = "deck_seen")
data class DeckSeenEntity(
    @PrimaryKey val term: String,
    val known: Boolean,
    val seenAt: Long,
)
