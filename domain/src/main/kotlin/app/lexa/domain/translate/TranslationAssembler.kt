package app.lexa.domain.translate

import app.lexa.domain.model.DictionaryData
import app.lexa.domain.model.Example
import app.lexa.domain.model.ExampleOrigin
import app.lexa.domain.model.PartOfSpeech
import app.lexa.domain.model.Sense
import app.lexa.domain.model.Translation
import java.util.Locale

/**
 * Merges the sources into one [Translation] (docs/adr/0001-dictionary-sources.md «Решение»):
 * per-POS translation through a frame («to run», «a run»), a Russian gloss from the first definition,
 * Tatoeba examples first, then dictionary examples translated by the machine and marked as such.
 */
class TranslationAssembler(private val translator: suspend (String) -> String?) {

    suspend fun assemble(query: String, dictionary: DictionaryData?, sentences: List<Example>): Translation? {
        val term = dictionary?.term?.takeIf { it.isNotBlank() } ?: query.trim()
        val senses = if (dictionary == null || dictionary.meanings.isEmpty()) {
            listOfNotNull(translator(term)?.let(::clean)?.takeIf { it.isNotBlank() }?.let { Sense(null, listOf(it)) })
        } else {
            sensesFor(term, dictionary)
        }
        if (senses.isEmpty() && dictionary == null && sentences.isEmpty()) return null
        return Translation(
            term = term,
            ipa = dictionary?.ipa,
            audioUrl = dictionary?.audioUrl,
            senses = senses,
            definition = dictionary?.meanings?.firstNotNullOfOrNull { it.definitions.firstOrNull() },
            examples = examplesFor(sentences, dictionary),
            synonyms = dictionary?.meanings.orEmpty()
                .flatMap { it.synonyms }
                .filterNot { it.equals(term, ignoreCase = true) }
                .distinctBy { it.lowercase(Locale.ROOT) }
                .take(MAX_SYNONYMS),
        )
    }

    private suspend fun sensesFor(term: String, dictionary: DictionaryData): List<Sense> {
        val seen = mutableSetOf<String>()
        return dictionary.meanings.take(MAX_SENSES).mapNotNull { meaning ->
            val framed = when (meaning.pos) {
                PartOfSpeech.VERB -> "to $term"
                PartOfSpeech.NOUN -> "a $term"
                else -> term
            }
            val translated = translator(framed)?.let(::clean)?.takeIf { it.isNotBlank() && seen.add(it) }
            val gloss = meaning.definitions.firstOrNull()?.let { translator(it) }?.trim()?.takeIf { it.isNotBlank() }
            if (translated == null && gloss == null) null
            else Sense(meaning.pos, listOfNotNull(translated), gloss)
        }
    }

    private suspend fun examplesFor(sentences: List<Example>, dictionary: DictionaryData?): List<Example> {
        val result = sentences.take(MAX_EXAMPLES).toMutableList()
        if (result.size >= MIN_HUMAN_EXAMPLES) return result
        val extra = dictionary?.meanings.orEmpty().flatMap { it.examples }
            .filter { en -> result.none { it.en.equals(en, ignoreCase = true) } }
        for (en in extra) {
            if (result.size >= MAX_EXAMPLES) break
            val ru = translator(en)?.trim()?.takeIf { it.isNotBlank() }
            result += Example(en, ru, if (ru != null) ExampleOrigin.MACHINE else ExampleOrigin.DICTIONARY)
        }
        return result
    }

    companion object {
        const val MAX_SENSES = 4
        const val MAX_EXAMPLES = 3
        const val MIN_HUMAN_EXAMPLES = 2
        const val MAX_SYNONYMS = 8

        private val trailing = Regex("[.!?…;:,]+$")

        /** Lower case, no full stop, no «чтобы» the verb frame sometimes brings. */
        fun clean(text: String): String = text.trim()
            .replace(trailing, "")
            .lowercase(Locale.forLanguageTag("ru"))
            .removePrefix("чтобы ")
            .trim()
    }
}
