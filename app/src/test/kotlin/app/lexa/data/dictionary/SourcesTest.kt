package app.lexa.data.dictionary

import app.lexa.domain.model.Example
import app.lexa.domain.model.ExampleOrigin
import app.lexa.domain.model.PartOfSpeech
import app.lexa.domain.repository.SourceResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Test

/** Free Dictionary and Tatoeba against recorded answers (29.10.2026). No real network. */
class SourcesTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun fixture(name: String) =
        requireNotNull(javaClass.getResource("/fixtures/$name")).readText()

    private fun client(status: HttpStatusCode, body: String = "", onUrl: (String) -> Unit = {}) =
        HttpClient(MockEngine { request ->
            onUrl(request.url.toString())
            respond(body, status, headersOf("Content-Type", "application/json"))
        })

    @Test fun `free dictionary entries merge by part of speech`() = runTest {
        var url = ""
        val source = FreeDictionarySource(client(HttpStatusCode.OK, fixture("free_dictionary_commute.json")) { url = it }, json)
        val data = assertIs<SourceResult.Ok<app.lexa.domain.model.DictionaryData>>(source.fetch("commute")).data

        assertEquals("https://api.dictionaryapi.dev/api/v2/entries/en/commute", url)
        assertEquals("commute", data.term)
        assertEquals("/kəˈmjuːt/", data.ipa)
        assertEquals("https://api.dictionaryapi.dev/media/pronunciations/en/commute-uk.mp3", data.audioUrl)
        // Two entries: verb, then noun + verb. Verbs are merged into one meaning, order of first appearance kept.
        assertEquals(listOf(PartOfSpeech.VERB, PartOfSpeech.NOUN), data.meanings.map { it.pos })
        assertEquals(4, data.meanings[0].definitions.size)
        assertEquals("I commute from Brooklyn to Manhattan by bicycle.", data.meanings[0].examples.last())
        assertEquals("A regular journey to or from a place of employment, such as work or school.", data.meanings[1].definitions[0])
    }

    @Test fun `phrase is sent as one path segment`() = runTest {
        var url = ""
        FreeDictionarySource(client(HttpStatusCode.NotFound) { url = it }, json).fetch("give up")
        assertEquals("https://api.dictionaryapi.dev/api/v2/entries/en/give%20up", url)
    }

    @Test fun `404 and 502 mean unknown word, other failures mean unavailable`() = runTest {
        assertEquals(SourceResult.NotFound, FreeDictionarySource(client(HttpStatusCode.NotFound), json).fetch("qwzx"))
        assertEquals(SourceResult.NotFound, FreeDictionarySource(client(HttpStatusCode.BadGateway), json).fetch("qwzx"))
        assertEquals(SourceResult.Unavailable, FreeDictionarySource(client(HttpStatusCode.TooManyRequests), json).fetch("run"))
        val broken = HttpClient(MockEngine { throw IOException("offline") })
        assertEquals(SourceResult.Unavailable, FreeDictionarySource(broken, json).fetch("run"))
    }

    @Test fun `tatoeba gives human pairs that contain the term`() = runTest {
        val source = TatoebaSource(client(HttpStatusCode.OK, fixture("tatoeba_commute.json")), json)
        val examples = assertIs<SourceResult.Ok<List<Example>>>(source.fetch("commute")).data
        assertEquals(
            listOf(
                Example("I commute to work by bicycle.", "Я езжу на работу на велосипеде.", ExampleOrigin.TATOEBA),
                // «commutes» contains «commute»
                Example("Tom commutes to work by bicycle.", "Том ездит на работу на велосипеде.", ExampleOrigin.TATOEBA),
                Example("Tom commutes to work by bus.", "Том ездит на работу на автобусе.", ExampleOrigin.TATOEBA),
            ),
            examples,
        )
    }

    @Test fun `tatoeba with no pairs is not found`() = runTest {
        val source = TatoebaSource(client(HttpStatusCode.OK, """{"data":[]}"""), json)
        assertEquals(SourceResult.NotFound, source.fetch("qwzx"))
    }
}
