package app.lexa.domain.model

/** Status counts, derived from `words` (docs/ARCHITECTURE.md §6.1): never stored separately. */
data class WordCounts(
    val learned: Int = 0,
    val learning: Int = 0,
    val new: Int = 0,
)

/** Answer totals from `review_log`. Accuracy is undefined while [answers] is 0. */
data class AnswerTotals(
    val answers: Int = 0,
    val correct: Int = 0,
) {
    /** Percent of correct answers, or null when nothing has been answered yet. */
    val accuracyPercent: Int?
        get() = if (answers == 0) null else (correct * 100 + answers / 2) / answers
}
