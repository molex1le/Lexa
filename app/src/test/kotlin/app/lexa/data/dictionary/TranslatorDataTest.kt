package app.lexa.data.dictionary

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.lexa.data.db.LexaDatabase
import app.lexa.data.repository.RoomTranslatedWordRepository
import app.lexa.domain.model.DictionaryData
import app.lexa.domain.model.Example
import app.lexa.domain.model.ExampleOrigin
import app.lexa.domain.model.PartOfSpeech
import app.lexa.domain.repository.DictionarySource
import app.lexa.domain.repository.ExampleSentenceSource
import app.lexa.domain.repository.LookupError
import app.lexa.domain.repository.LookupResult
import app.lexa.domain.repository.MachineTranslator
import app.lexa.domain.repository.NetworkStatus
import app.lexa.domain.repository.SourceResult
import app.lexa.domain.time.TimeProvider
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** Stand-in for ML Kit, which needs a device: a fixed EN→RU table, the model can be missing. */
private class FakeTranslator(var ready: Boolean = true, var downloadWorks: Boolean = true) : MachineTranslator {
    var downloads = 0
    val table = mutableMapOf(
        "to commute" to "Ездить на работу.",
        "a commute" to "поездка на работу",
        "run amok" to "взбеситься",
    )
    override suspend fun isModelReady() = ready
    override suspend fun downloadModel(): Boolean { downloads++; ready = downloadWorks; return downloadWorks }
    override suspend fun translate(text: String): String? = if (ready) table[text] ?: "перевод" else null
}

private class FakeDictionary(var result: SourceResult<DictionaryData>) : DictionarySource {
    var calls = 0
    override suspend fun fetch(term: String): SourceResult<DictionaryData> { calls++; return result }
}

private class FakeSentences(var result: SourceResult<List<Example>>) : ExampleSentenceSource {
    override suspend fun fetch(term: String) = result
}

private class FakeNetwork(var online: Boolean = true) : NetworkStatus {
    override fun isOnline() = online
}

private val fixedTime = object : TimeProvider {
    override val clock: Clock = Clock.fixed(Instant.parse("2026-10-29T10:00:00Z"), ZoneOffset.UTC)
}

/** CompositeDictionaryRepository and the auto-add on an in-memory Room database. */
@RunWith(AndroidJUnit4::class)
class TranslatorDataTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), LexaDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    private val commute = DictionaryData(
        term = "commute",
        ipa = "/kəˈmjuːt/",
        meanings = listOf(
            DictionaryData.Meaning(PartOfSpeech.VERB, listOf("To travel to work."), listOf("I commute by bike.")),
            DictionaryData.Meaning(PartOfSpeech.NOUN, listOf("A journey to work.")),
        ),
    )
    private val translator = FakeTranslator()
    private val dictionary = FakeDictionary(SourceResult.Ok(commute))
    private val sentences = FakeSentences(SourceResult.NotFound)
    private val network = FakeNetwork()
    private val repository = CompositeDictionaryRepository(
        dictionary, sentences, translator, network, db.translationCacheDao(), Json, fixedTime,
    )
    private val words = RoomTranslatedWordRepository(db, fixedTime)

    @After fun close() = db.close()

    @Test fun `merged result is cached and a repeat works offline`() = runTest {
        val first = assertIs<LookupResult.Found>(repository.lookup("Commute"))
        assertFalse(first.fromCache)
        assertEquals(listOf("ездить на работу", "поездка на работу"), first.translation.allTranslations)
        assertEquals(ExampleOrigin.MACHINE, first.translation.examples.single().origin)

        network.online = false
        dictionary.result = SourceResult.Unavailable
        val second = assertIs<LookupResult.Found>(repository.lookup("  commute "))
        assertTrue(second.fromCache)
        assertEquals(first.translation, second.translation)
        assertEquals(1, dictionary.calls)
    }

    @Test fun `unknown word still gets a machine translation`() = runTest {
        dictionary.result = SourceResult.NotFound
        val found = assertIs<LookupResult.Found>(repository.lookup("run amok"))
        assertEquals(listOf("взбеситься"), found.translation.allTranslations)
        assertNull(found.translation.senses.single().pos)
    }

    @Test fun `offline with nothing cached is an honest error`() = runTest {
        network.online = false
        assertEquals(LookupResult.Failed(LookupError.NO_NETWORK), repository.lookup("commute"))
    }

    @Test fun `missing model offline is explained, online it downloads`() = runTest {
        translator.ready = false
        network.online = false
        assertEquals(LookupResult.Failed(LookupError.MODEL_MISSING_OFFLINE), repository.lookup("commute"))

        network.online = true
        translator.downloadWorks = false
        assertEquals(LookupResult.Failed(LookupError.MODEL_DOWNLOAD_FAILED), repository.lookup("commute"))

        translator.downloadWorks = true
        assertIs<LookupResult.Found>(repository.lookup("commute"))
        assertEquals(2, translator.downloads)
    }

    @Test fun `result built without the dictionary is not cached`() = runTest {
        dictionary.result = SourceResult.Unavailable
        assertIs<LookupResult.Found>(repository.lookup("commute"))
        network.online = false
        assertEquals(LookupResult.Failed(LookupError.NO_NETWORK), repository.lookup("commute"))
    }

    @Test fun `auto-add creates one word, a repeat does not duplicate`() = runTest {
        val translation = assertIs<LookupResult.Found>(repository.lookup("commute")).translation
        val first = words.addFromTranslation(translation)
        val again = words.addFromTranslation(translation.copy(term = "Commute"))
        assertTrue(first.isNew)
        assertFalse(again.isNew)
        assertEquals(first.wordId, again.wordId)

        val stored = requireNotNull(db.wordDao().findByKey("commute"))
        assertEquals(1, stored.box)
        assertEquals(Instant.parse("2026-10-29T00:00:00Z").toEpochMilli(), stored.nextReviewAt)
        assertEquals(listOf("ездить на работу", "поездка на работу"), stored.translations.map { it.text })
        assertEquals(listOf("VERB", "NOUN"), stored.translations.map { it.pos })
    }

    @Test fun `undo deletes the fresh word, favorite is stored`() = runTest {
        val translation = assertIs<LookupResult.Found>(repository.lookup("commute")).translation
        val added = words.addFromTranslation(translation)
        words.setFavorite(added.wordId, true)
        assertTrue(requireNotNull(db.wordDao().findByKey("commute")).isFavorite)

        words.undoAdd(added.wordId)
        assertNull(db.wordDao().findByKey("commute"))
    }
}
