package app.lexa.domain.training

import java.text.Normalizer
import java.util.Locale

/** Which language the typed answer is in: «to» is cut only on the English side (ARCHITECTURE §8.2). */
enum class AnswerLanguage { ENGLISH, RUSSIAN }

/** Verdict on one typed answer (ARCHITECTURE §8.3). */
sealed interface CheckResult {
    val isCorrect: Boolean

    /** Matches [matched] after normalization. */
    data class Exact(val matched: String) : CheckResult {
        override val isCorrect get() = true
    }

    /** One letter off in a long word; the UI shows the correct spelling [expected]. */
    data class AcceptedWithTypo(val expected: String) : CheckResult {
        override val isCorrect get() = true
    }

    /** Nothing matched; [expected] are all candidates, for display. */
    data class Wrong(val expected: List<String>) : CheckResult {
        override val isCorrect get() = false
    }
}

/**
 * Forgiving answer check (docs/ARCHITECTURE.md §8): case, whitespace, ё/е, typographic quotes and
 * dashes, punctuation at the edges, a leading «to » on the English side, and one Levenshtein edit in
 * a word of 6+ letters. Transpositions cost 2 and articles are kept (owner decision, §17).
 */
object AnswerChecker {
    /** Words shorter than this are never forgiven a typo. */
    const val MIN_TYPO_WORD_LENGTH = 6

    private val whitespace = Regex("\\s+")
    private val parenthesised = Regex("\\([^)]*\\)")
    private val separators = Regex("[,;/]")
    private const val EDGE_PUNCTUATION = ".,!?;:\"«»()"

    /**
     * Splits stored translations into separate candidates on `,` `;` `/` and drops text in
     * parentheses: «ключ (от замка)» → «ключ». Blank pieces and duplicates are dropped.
     */
    fun candidates(stored: List<String>): List<String> =
        stored.asSequence()
            .map { it.replace(parenthesised, " ") }
            .flatMap { it.split(separators) }
            .map { it.replace(whitespace, " ").trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()

    /** Normalization applied to both the answer and each candidate (§8.2). */
    fun normalize(text: String, language: AnswerLanguage): String {
        var s = Normalizer.normalize(text, Normalizer.Form.NFC).lowercase(Locale.ROOT)
            .replace('ё', 'е')
            .replace('’', '\'').replace('‘', '\'').replace('ʼ', '\'').replace('`', '\'')
            .replace('‑', '-').replace('–', '-').replace('—', '-')
            .replace(whitespace, " ")
            .trim()
            .trim { it in EDGE_PUNCTUATION || it.isWhitespace() }
            .replace(whitespace, " ")
        if (language == AnswerLanguage.ENGLISH && s.startsWith("to ")) s = s.removePrefix("to ").trim()
        return s
    }

    /**
     * Checks [answer] against [expected] (already split into candidates, see [candidates]).
     * An exact match with any candidate wins over a typo match with another one.
     */
    fun check(answer: String, expected: List<String>, language: AnswerLanguage): CheckResult {
        val given = normalize(answer, language)
        if (given.isEmpty()) return CheckResult.Wrong(expected)
        val normalized = expected.map { it to normalize(it, language) }.filter { it.second.isNotEmpty() }
        normalized.firstOrNull { it.second == given }?.let { return CheckResult.Exact(it.first) }
        normalized.firstOrNull { isTypo(given, it.second) }?.let { return CheckResult.AcceptedWithTypo(it.first) }
        return CheckResult.Wrong(expected)
    }

    /** Same word count, one edit in total, and the edited candidate word is 6+ characters long. */
    private fun isTypo(given: String, candidate: String): Boolean {
        val a = given.split(' ')
        val b = candidate.split(' ')
        if (a.size != b.size) return false
        var edits = 0
        for (i in a.indices) {
            val d = levenshtein(a[i], b[i], limit = 2)
            if (d == 0) continue
            edits += d
            if (edits > 1 || b[i].length < MIN_TYPO_WORD_LENGTH) return false
        }
        return edits == 1
    }

    /** Classic Levenshtein distance, stops early once it exceeds [limit]. */
    internal fun levenshtein(a: String, b: String, limit: Int = Int.MAX_VALUE): Int {
        if (kotlin.math.abs(a.length - b.length) > limit) return limit + 1
        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            var rowMin = curr[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(prev[j] + 1, curr[j - 1] + 1, prev[j - 1] + cost)
                rowMin = minOf(rowMin, curr[j])
            }
            if (rowMin > limit) return limit + 1
            val t = prev; prev = curr; curr = t
        }
        return prev[b.length]
    }
}
