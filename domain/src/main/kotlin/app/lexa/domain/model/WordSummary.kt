package app.lexa.domain.model

/** A word as lists show it: one line of identity and the facts lists sort by. */
data class WordSummary(
    val id: Long,
    val term: String,
    val translation: String,
    val isFavorite: Boolean,
    val box: Int,
    val mistakesCount: Int,
    val addedAt: Long,
    val learnedAt: Long?,
)
