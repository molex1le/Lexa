package app.lexa.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.lexa.data.db.LexaDatabase
import app.lexa.data.db.entity.TranslationJson
import app.lexa.data.db.entity.WordEntity
import app.lexa.data.db.entity.WordSource
import app.lexa.domain.model.VocabLevel
import app.lexa.domain.time.TimeProvider
import app.lexa.domain.training.AnswerOutcome
import app.lexa.domain.training.CardDirection
import app.lexa.domain.training.ReviewRecord
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

private val NOW = Instant.parse("2026-10-29T10:00:00Z").toEpochMilli()

/** The built-in deck file and its progress table. */
@RunWith(AndroidJUnit4::class)
class DeckRepositoryTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), LexaDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val time = object : TimeProvider {
        override val clock: Clock = Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC)
    }
    private val tsv = "A1\tapple\tяблоко\nA1\tbook\tкнига\nB1\topinion\tмнение\nbad line\nC1\tfrugal\tбережливый, экономный\n"
    private val deck = AssetDeckRepository({ tsv }, db, time, Random(1))

    @After fun close() = db.close()

    @Test fun `the shipped deck parses completely, covers every level and has no duplicate terms`() {
        val text = File("src/main/assets/deck_words.tsv").readText()
        val words = parseDeck(text)
        assertEquals(text.lines().count { it.isNotBlank() }, words.size)
        VocabLevel.entries.forEach { level -> assertTrue(words.count { it.level == level } >= 100, "$level") }
        assertEquals(words.size, words.map { it.term }.toSet().size)
    }

    @Test fun `malformed lines are skipped`() = assertEquals(4, parseDeck(tsv).size)

    @Test fun `random unseen skips known words and words already in the list, then moves up a level`() = runTest {
        deck.markKnown("apple")
        db.wordDao().insert(
            WordEntity(
                term = "book", termKey = "book", translations = listOf(TranslationJson("книга")),
                nextReviewAt = NOW, addedAt = NOW, source = WordSource.TRANSLATOR, searchText = "book книга",
            ),
        )
        assertEquals(listOf("opinion", "frugal"), deck.randomUnseen(VocabLevel.A1, 5).map { it.term })
    }

    @Test fun `an unknown word joins the list in box 1 and undo removes it and marks it known`() = runTest {
        val word = parseDeck(tsv).first { it.term == "opinion" }
        val record = ReviewRecord(0, NOW, 0, CardDirection.EN_RU, "мысль", AnswerOutcome.WRONG, 1, 1, false)
        val (wordId, _) = deck.addAsNew(word, record)
        val saved = db.wordDao().find(wordId)!!
        assertEquals(1, saved.box)
        assertEquals(1, saved.mistakesCount)
        assertEquals(WordSource.DECK, saved.source)
        assertTrue(deck.randomUnseen(VocabLevel.B1, 5).none { it.term == "opinion" })

        deck.undoAddAsNew(wordId, "opinion")
        assertNull(db.wordDao().find(wordId))
        assertTrue(deck.randomUnseen(VocabLevel.B1, 5).none { it.term == "opinion" })
    }
}
