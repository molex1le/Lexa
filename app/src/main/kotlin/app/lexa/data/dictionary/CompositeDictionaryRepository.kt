package app.lexa.data.dictionary

import app.lexa.data.db.dao.TranslationCacheDao
import app.lexa.data.db.entity.CacheStatus
import app.lexa.data.db.entity.TranslationCacheEntity
import app.lexa.domain.model.Translation
import app.lexa.domain.repository.DictionaryRepository
import app.lexa.domain.repository.DictionarySource
import app.lexa.domain.repository.ExampleSentenceSource
import app.lexa.domain.repository.LookupError
import app.lexa.domain.repository.LookupProgress
import app.lexa.domain.repository.LookupResult
import app.lexa.domain.repository.MachineTranslator
import app.lexa.domain.repository.NetworkStatus
import app.lexa.domain.repository.SourceResult
import app.lexa.domain.text.normalizeKey
import app.lexa.domain.time.TimeProvider
import app.lexa.domain.translate.TranslationAssembler
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json

/**
 * docs/ARCHITECTURE.md §10: cache first, then the model, then Free Dictionary and Tatoeba in parallel
 * (4 s each), merged by [TranslationAssembler]. The merged entry is cached as the `assembled` row,
 * so a repeat works offline. A result built while the dictionary was unreachable is not cached.
 */
private val CYRILLIC = ('а'..'я') + ('А'..'Я') + 'ё' + 'Ё'

/** «To run.» → «run»: machine output as a dictionary headword. */
internal fun englishHeadword(text: String): String? =
    text.trim().trimEnd('.', '!', '?').trim().lowercase().removePrefix("to ").trim().takeIf { it.isNotEmpty() }

class CompositeDictionaryRepository @Inject constructor(
    private val dictionary: DictionarySource,
    private val sentences: ExampleSentenceSource,
    private val translator: MachineTranslator,
    private val network: NetworkStatus,
    private val cache: TranslationCacheDao,
    private val json: Json,
    private val time: TimeProvider,
) : DictionaryRepository {

    override suspend fun lookup(query: String, onProgress: (LookupProgress) -> Unit): LookupResult {
        val key = normalizeKey(query)
        if (key.isEmpty()) return LookupResult.Failed(LookupError.NOTHING_FOUND)
        cached(key)?.let { return LookupResult.Found(it, fromCache = true) }

        val online = network.isOnline()
        if (query.any { it in CYRILLIC }) {
            // A Russian word: find its English word first, then look that up as usual – the learner
            // gets the English entry, and that is what goes into «Новые».
            if (!translator.isModelReady()) {
                if (!online) return LookupResult.Failed(LookupError.MODEL_MISSING_OFFLINE)
                onProgress(LookupProgress.DOWNLOADING_MODEL)
                translator.downloadModel()
            }
            val english = translator.translateToEnglish(query.trim())?.let(::englishHeadword)
                ?: return LookupResult.Failed(if (online) LookupError.NOTHING_FOUND else LookupError.NO_NETWORK)
            return lookup(english, onProgress)
        }
        if (!translator.isModelReady()) {
            if (!online) return LookupResult.Failed(LookupError.MODEL_MISSING_OFFLINE)
            onProgress(LookupProgress.DOWNLOADING_MODEL)
            if (!translator.downloadModel()) return LookupResult.Failed(LookupError.MODEL_DOWNLOAD_FAILED)
        }
        if (!online) return LookupResult.Failed(LookupError.NO_NETWORK)

        onProgress(LookupProgress.FETCHING)
        val (dict, examples) = coroutineScope {
            val d = async { withTimeoutOrNull(SOURCE_TIMEOUT_MS) { dictionary.fetch(key) } ?: SourceResult.Unavailable }
            val e = async { withTimeoutOrNull(SOURCE_TIMEOUT_MS) { sentences.fetch(key) } ?: SourceResult.Unavailable }
            d.await() to e.await()
        }
        val translation = TranslationAssembler(translator::translate).assemble(
            query = query,
            dictionary = (dict as? SourceResult.Ok)?.data,
            sentences = (examples as? SourceResult.Ok)?.data.orEmpty(),
        )
        if (translation == null || (translation.senses.isEmpty() && dict !is SourceResult.Ok)) {
            return LookupResult.Failed(LookupError.NOTHING_FOUND)
        }
        if (dict !is SourceResult.Unavailable) {
            cache.upsert(
                TranslationCacheEntity(
                    queryKey = key,
                    sourceId = ASSEMBLED,
                    schemaVersion = SCHEMA_VERSION,
                    payload = json.encodeToString(Translation.serializer(), translation),
                    status = CacheStatus.OK,
                    fetchedAt = time.nowMillis(),
                ),
            )
        }
        return LookupResult.Found(translation, fromCache = false)
    }

    private suspend fun cached(key: String): Translation? {
        val row = cache.get(key, ASSEMBLED) ?: return null
        if (row.schemaVersion != SCHEMA_VERSION || row.status != CacheStatus.OK) return null
        return runCatching { json.decodeFromString(Translation.serializer(), row.payload) }.getOrNull()
    }

    companion object {
        const val ASSEMBLED = "assembled"
        const val SCHEMA_VERSION = 1
        const val SOURCE_TIMEOUT_MS = 4_000L
    }
}
