package app.lexa.domain.training

import app.lexa.domain.model.VocabLevel

/** One built-in word: the term and its Russian translations, comma-separated. */
data class DeckWord(val level: VocabLevel, val term: String, val translations: String)

/**
 * The built-in words by level (assets/deck_words.tsv) and what the learner already did with them.
 * A deck word is shown once: known → never again; unknown → it becomes a word in «Новые» and goes
 * through the boxes like a translated one.
 */
interface DeckRepository {
    /** Up to [count] random words of [level] (then harder levels) never shown and not already in the word list. */
    suspend fun randomUnseen(level: VocabLevel, count: Int): List<DeckWord>

    /** [count] random words of each level, for the placement test. */
    suspend fun sample(perLevel: Int): Map<VocabLevel, List<DeckWord>>

    suspend fun markKnown(term: String)

    /**
     * The learner did not know it: inserts it into «Новые» (box 1, due now, one mistake) with the
     * wrong answer's log row. Returns the new word id and the log row id.
     */
    suspend fun addAsNew(word: DeckWord, record: ReviewRecord): Pair<Long, Long>

    /** «Я знал, это опечатка» on a deck word: removes the word [addAsNew] created and marks it known. */
    suspend fun undoAddAsNew(wordId: Long, term: String)
}

/**
 * Placement test: the learner says «знаю / не знаю» for a few words of each level. The level is the
 * highest one where at least [PASS] of the words were known, counting up from A1 without gaps.
 */
object PlacementTest {
    const val PER_LEVEL = 5
    const val PASS = 3

    fun level(known: Map<VocabLevel, Int>): VocabLevel {
        var result = VocabLevel.A1
        for (level in VocabLevel.entries) {
            if ((known[level] ?: 0) >= PASS) result = level else break
        }
        return result
    }
}
