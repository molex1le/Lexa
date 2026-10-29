package app.lexa.domain.model

/** The word card: list facts plus the stored dictionary entry, in the translator's own model. */
data class WordDetails(val summary: WordSummary, val entry: Translation)
