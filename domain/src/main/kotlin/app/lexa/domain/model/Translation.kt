package app.lexa.domain.model

import kotlinx.serialization.Serializable

/** Parts of speech as Free Dictionary names them; anything else is [OTHER]. */
@Serializable
enum class PartOfSpeech {
    NOUN, VERB, ADJECTIVE, ADVERB, PRONOUN, PREPOSITION, CONJUNCTION, INTERJECTION, OTHER;

    companion object {
        fun parse(name: String?): PartOfSpeech =
            entries.firstOrNull { it.name.equals(name?.trim(), ignoreCase = true) } ?: OTHER
    }
}

/** Where an example's Russian text came from. [MACHINE] is marked «машинный перевод» on screen. */
@Serializable
enum class ExampleOrigin { TATOEBA, DICTIONARY, MACHINE }

@Serializable
data class Example(val en: String, val ru: String? = null, val origin: ExampleOrigin)

/**
 * Russian translations for one part of speech. [pos] is null for a phrase the dictionary does not know.
 * [gloss] is the first English definition translated to Russian, to tell the senses apart.
 */
@Serializable
data class Sense(
    val pos: PartOfSpeech? = null,
    val translations: List<String>,
    val gloss: String? = null,
)

/** One merged translator entry (docs/ARCHITECTURE.md §10): what the result screen shows and the cache keeps. */
@Serializable
data class Translation(
    val term: String,
    val ipa: String? = null,
    val audioUrl: String? = null,
    val senses: List<Sense> = emptyList(),
    /** Short English definition. */
    val definition: String? = null,
    val examples: List<Example> = emptyList(),
    val synonyms: List<String> = emptyList(),
) {
    val allTranslations: List<String> get() = senses.flatMap { it.translations }.distinct()
}

/** What Free Dictionary knows about a word, already merged across its entries by part of speech. */
data class DictionaryData(
    val term: String,
    val ipa: String? = null,
    val audioUrl: String? = null,
    val meanings: List<Meaning>,
) {
    data class Meaning(
        val pos: PartOfSpeech,
        val definitions: List<String>,
        val examples: List<String> = emptyList(),
        val synonyms: List<String> = emptyList(),
    )
}
