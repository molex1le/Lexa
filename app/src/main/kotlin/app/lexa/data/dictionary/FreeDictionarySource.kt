package app.lexa.data.dictionary

import app.lexa.domain.model.DictionaryData
import app.lexa.domain.model.PartOfSpeech
import app.lexa.domain.repository.DictionarySource
import app.lexa.domain.repository.SourceResult
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.appendPathSegments
import io.ktor.http.takeFrom
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class FdEntry(
    val word: String,
    val phonetic: String? = null,
    val phonetics: List<FdPhonetic> = emptyList(),
    val meanings: List<FdMeaning> = emptyList(),
)

@Serializable
internal data class FdPhonetic(val text: String? = null, val audio: String? = null)

@Serializable
internal data class FdMeaning(
    val partOfSpeech: String? = null,
    val definitions: List<FdDefinition> = emptyList(),
    val synonyms: List<String> = emptyList(),
)

@Serializable
internal data class FdDefinition(
    val definition: String,
    val example: String? = null,
    val synonyms: List<String> = emptyList(),
)

/**
 * api.dictionaryapi.dev (ADR-0001). 404 and 502 both mean «unknown word»: the service answers an
 * unknown word with either. Any other failure is [SourceResult.Unavailable].
 */
class FreeDictionarySource @Inject constructor(
    private val client: HttpClient,
    private val json: Json,
) : DictionarySource {

    override suspend fun fetch(term: String): SourceResult<DictionaryData> {
        return try {
            val response = client.get { url { takeFrom(BASE_URL); appendPathSegments(term) } }
            when (response.status.value) {
                200 -> parse(response.bodyAsText())?.let { SourceResult.Ok(it) } ?: SourceResult.NotFound
                404, 502 -> SourceResult.NotFound
                else -> SourceResult.Unavailable
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            SourceResult.Unavailable
        }
    }

    internal fun parse(body: String): DictionaryData? {
        val entries = json.decodeFromString<List<FdEntry>>(body)
        if (entries.isEmpty()) return null
        val phonetics = entries.flatMap { it.phonetics }
        val audio = phonetics.mapNotNull { it.audio?.takeIf(String::isNotBlank) }
        val byPos = LinkedHashMap<PartOfSpeech, MutableList<FdMeaning>>()
        entries.flatMap { it.meanings }.forEach { byPos.getOrPut(PartOfSpeech.parse(it.partOfSpeech)) { mutableListOf() } += it }
        return DictionaryData(
            term = entries.first().word,
            ipa = entries.firstNotNullOfOrNull { it.phonetic?.takeIf(String::isNotBlank) }
                ?: phonetics.firstNotNullOfOrNull { it.text?.takeIf(String::isNotBlank) },
            audioUrl = audio.firstOrNull { it.endsWith("-us.mp3") } ?: audio.firstOrNull(),
            meanings = byPos.map { (pos, meanings) ->
                val definitions = meanings.flatMap { it.definitions }
                DictionaryData.Meaning(
                    pos = pos,
                    definitions = definitions.map { it.definition.trim() },
                    examples = definitions.mapNotNull { it.example?.trim()?.takeIf(String::isNotBlank) },
                    synonyms = (meanings.flatMap { it.synonyms } + definitions.flatMap { it.synonyms }).distinct(),
                )
            },
        )
    }

    private companion object {
        const val BASE_URL = "https://api.dictionaryapi.dev/api/v2/entries/en/"
    }
}
