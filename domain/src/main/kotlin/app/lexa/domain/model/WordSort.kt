package app.lexa.domain.model

import app.lexa.domain.text.normalizeKey

/** How the word lists are ordered; the choice is kept in settings. */
enum class WordSort { ADDED, ALPHABET, MISTAKES }

/**
 * Search and order for the lists, in memory: a personal dictionary is small, and the filter must
 * forgive case and ё/е exactly like answer checking does. Search covers the term and every translation.
 */
fun List<WordSummary>.searchAndSort(query: String, sort: WordSort): List<WordSummary> {
    val key = normalizeKey(query)
    val found = if (key.isEmpty()) this else filter { normalizeKey(it.term + " " + it.translation).contains(key) }
    return when (sort) {
        WordSort.ADDED -> found.sortedWith(compareByDescending<WordSummary> { it.addedAt }.thenBy { it.id })
        WordSort.ALPHABET -> found.sortedWith(compareBy<WordSummary> { normalizeKey(it.term) }.thenBy { it.id })
        WordSort.MISTAKES -> found.sortedWith(
            compareByDescending<WordSummary> { it.mistakesCount }.thenByDescending { it.addedAt }.thenBy { it.id },
        )
    }
}
