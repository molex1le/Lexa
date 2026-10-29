package app.lexa.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class WordSortTest {
    private fun w(id: Long, term: String, translation: String, added: Long, mistakes: Int = 0) =
        WordSummary(id, term, translation, false, 1, mistakes, added, null)

    private val words = listOf(
        w(1, "Hedgehog", "ёж", added = 10, mistakes = 1),
        w(2, "apple", "яблоко", added = 30),
        w(3, "Give up", "сдаваться, бросать", added = 20, mistakes = 4),
    )

    @Test fun `empty query keeps everything`() =
        assertEquals(3, words.searchAndSort("  ", WordSort.ADDED).size)

    @Test fun `search ignores case and spaces`() =
        assertEquals(listOf(3L), words.searchAndSort("  GIVE   up ", WordSort.ADDED).map { it.id })

    @Test fun `search forgives yo and e both ways`() {
        assertEquals(listOf(1L), words.searchAndSort("еж", WordSort.ADDED).map { it.id })
        assertEquals(listOf(1L), words.searchAndSort("ЁЖ", WordSort.ADDED).map { it.id })
    }

    @Test fun `search covers every translation`() =
        assertEquals(listOf(3L), words.searchAndSort("брос", WordSort.ADDED).map { it.id })

    @Test fun `nothing found`() = assertEquals(emptyList(), words.searchAndSort("zebra", WordSort.ADDED))

    @Test fun `sort by date added, newest first`() =
        assertEquals(listOf(2L, 3L, 1L), words.searchAndSort("", WordSort.ADDED).map { it.id })

    @Test fun `sort alphabetically ignores case`() =
        assertEquals(listOf(2L, 3L, 1L), words.searchAndSort("", WordSort.ALPHABET).map { it.id })

    @Test fun `sort by mistakes, most first`() =
        assertEquals(listOf(3L, 1L, 2L), words.searchAndSort("", WordSort.MISTAKES).map { it.id })

    @Test fun `fresh means box 1 and never answered`() {
        assertEquals(true, words[0].isFresh)
        assertEquals(false, words[0].copy(lastReviewedAt = 5).isFresh)
        assertEquals(false, words[0].copy(box = 2).isFresh)
    }
}
