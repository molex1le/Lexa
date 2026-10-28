package app.lexa.domain.model

data class HistoryItem(
    val id: Long,
    val query: String,
    val translatedAt: Long,
)
