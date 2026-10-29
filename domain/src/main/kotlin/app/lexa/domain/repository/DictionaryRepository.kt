package app.lexa.domain.repository

import app.lexa.domain.model.DictionaryData
import app.lexa.domain.model.Example
import app.lexa.domain.model.Translation

/** The translator's single entry point (docs/ARCHITECTURE.md §10). Sources can change without touching UI. */
interface DictionaryRepository {
    /** [onProgress] reports a slow step, such as the one-time model download. */
    suspend fun lookup(query: String, onProgress: (LookupProgress) -> Unit = {}): LookupResult
}

enum class LookupProgress { FETCHING, DOWNLOADING_MODEL }

sealed interface LookupResult {
    data class Found(val translation: Translation, val fromCache: Boolean) : LookupResult
    data class Failed(val error: LookupError) : LookupResult
}

enum class LookupError {
    /** Offline and nothing cached for this query. */
    NO_NETWORK,
    /** The Russian model is not on the phone and there is no network to fetch it. */
    MODEL_MISSING_OFFLINE,
    /** Online, but the model download failed. */
    MODEL_DOWNLOAD_FAILED,
    /** Online, but no source gave anything usable. */
    NOTHING_FOUND,
}

/** Result of one network source: data, a definite «no such word», or the source could not be reached. */
sealed interface SourceResult<out T> {
    data class Ok<T>(val data: T) : SourceResult<T>
    data object NotFound : SourceResult<Nothing>
    data object Unavailable : SourceResult<Nothing>
}

/** IPA, audio, parts of speech, definitions, examples, synonyms (Free Dictionary API). */
interface DictionarySource {
    suspend fun fetch(term: String): SourceResult<DictionaryData>
}

/** English sentences with human Russian translations (Tatoeba). */
interface ExampleSentenceSource {
    suspend fun fetch(term: String): SourceResult<List<Example>>
}

/** On-device EN→RU translation (ML Kit). */
interface MachineTranslator {
    suspend fun isModelReady(): Boolean
    /** Downloads the Russian model over any network. Returns false if it failed. */
    suspend fun downloadModel(): Boolean
    suspend fun translate(text: String): String?

    /** RU→EN, for a Russian word typed into the translator. Null when it can't. */
    suspend fun translateToEnglish(text: String): String? = null
}

interface NetworkStatus {
    fun isOnline(): Boolean
}

/** Auto-add of translated words to «Новые слова» (box 1), without duplicates. */
interface TranslatedWordRepository {
    /** Inserts the word unless a word with the same normalized term exists. */
    suspend fun addFromTranslation(translation: Translation): AddedWord
    /** Undo of a fresh add: deletes the word. Callers only pass ids with [AddedWord.isNew]. */
    suspend fun undoAdd(wordId: Long)
    suspend fun setFavorite(wordId: Long, favorite: Boolean)
}

data class AddedWord(val wordId: Long, val isNew: Boolean, val isFavorite: Boolean)
