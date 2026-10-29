package app.lexa.data.dictionary

import app.lexa.domain.model.Example
import app.lexa.domain.model.ExampleOrigin
import app.lexa.domain.repository.ExampleSentenceSource
import app.lexa.domain.repository.SourceResult
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Tatoeba API v1: English sentences with Russian translations made by people (ADR-0001). */
class TatoebaSource @Inject constructor(
    private val client: HttpClient,
    private val json: Json,
) : ExampleSentenceSource {

    override suspend fun fetch(term: String): SourceResult<List<Example>> {
        return try {
            val response = client.get(BASE_URL) {
                parameter("lang", "eng")
                parameter("q", term)
                parameter("trans:lang", "rus")
                parameter("sort", "relevance")
                parameter("showtrans", "matching")
                parameter("limit", 10)
            }
            if (response.status.value != 200) return SourceResult.Unavailable
            val examples = parse(response.bodyAsText(), term)
            if (examples.isEmpty()) SourceResult.NotFound else SourceResult.Ok(examples)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            SourceResult.Unavailable
        }
    }

    /** Short sentences that contain the term and have a Russian translation, at most three. */
    internal fun parse(body: String, term: String): List<Example> {
        val data = json.parseToJsonElement(body).jsonObject["data"]?.jsonArray ?: return emptyList()
        return data.mapNotNull { element ->
            val sentence = element.jsonObject
            val en = sentence["text"]?.jsonPrimitive?.content?.trim() ?: return@mapNotNull null
            if (en.length > MAX_LENGTH || !en.contains(term, ignoreCase = true)) return@mapNotNull null
            val ru = sentence["translations"]?.flattenObjects()
                ?.firstOrNull { it["lang"]?.jsonPrimitive?.content == "rus" }
                ?.get("text")?.jsonPrimitive?.content?.trim()
                ?: return@mapNotNull null
            Example(en, ru, ExampleOrigin.TATOEBA)
        }.distinctBy { it.en }.take(3)
    }

    /** `translations` is a flat list in v1 answers, but older shapes nest it; accept both. */
    private fun JsonElement.flattenObjects(): List<JsonObject> = when (this) {
        is JsonObject -> listOf(this)
        is JsonArray -> flatMap { it.flattenObjects() }
        else -> emptyList()
    }

    private companion object {
        const val BASE_URL = "https://api.tatoeba.org/v1/sentences"
        const val MAX_LENGTH = 90
    }
}
