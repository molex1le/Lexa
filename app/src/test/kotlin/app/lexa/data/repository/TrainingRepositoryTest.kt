package app.lexa.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.lexa.data.db.LexaDatabase
import app.lexa.data.db.entity.ReviewOutcome
import app.lexa.data.db.entity.TranslationJson
import app.lexa.data.db.entity.WordEntity
import app.lexa.data.db.entity.WordSource
import app.lexa.domain.leitner.LeitnerScheduler
import app.lexa.domain.time.TimeProvider
import app.lexa.domain.training.CardDirection
import app.lexa.domain.training.TrainingSession
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

private val NOW = Instant.parse("2026-10-29T10:00:00Z").toEpochMilli()

/** The training session on an in-memory Room database: queue order, log rows, override. */
@RunWith(AndroidJUnit4::class)
class TrainingRepositoryTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), LexaDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val time = object : TimeProvider {
        override val clock: Clock = Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC)
    }
    private val session = TrainingSession(
        RoomTrainingRepository(db), AssetDeckRepository({ "" }, db, time, kotlin.random.Random(1)), LeitnerScheduler(time), time,
    )

    @After fun close() = db.close()

    private suspend fun word(term: String, box: Int = 1, due: Long = NOW, added: Long = 0, learned: Long? = null, deleted: Long? = null) =
        db.wordDao().insert(
            WordEntity(
                term = term, termKey = term, translations = listOf(TranslationJson("перевод $term")),
                box = box, nextReviewAt = due, addedAt = added, learnedAt = learned, deletedAt = deleted,
                source = WordSource.TRANSLATOR, searchText = term,
            ),
        )

    @Test fun `queue is due words only, box 1 first, oldest first, capped`() = runTest {
        word("later", due = NOW + 1)
        word("learned", learned = 1)
        word("deleted", deleted = 1)
        word("box3", box = 3, added = 1)
        word("new-old", added = 2)
        word("new-older", added = 1)
        assertEquals(listOf("new-older", "new-old", "box3"), session.build().map { it.term })
        assertEquals(2, session.build(limit = 2).size)
    }

    @Test fun `answers write the word and the log, override rewrites the same row`() = runTest {
        word("commute", box = 3)
        val card = session.build().single()

        val right = session.answer(card, CardDirection.EN_RU, "перевод commute", wasDue = true)
        assertEquals(4, db.wordDao().find(right.transition.state.let { card.wordId })!!.box)

        val wrong = session.answer(card, CardDirection.EN_RU, "не то", wasDue = true)
        db.wordDao().find(card.wordId)!!.let {
            assertEquals(1, it.box)
            assertEquals(1, it.mistakesCount)
            assertEquals(NOW, it.nextReviewAt)
            assertEquals(NOW, it.lastReviewedAt)
        }

        session.override(card, wrong, wasDue = true)
        db.wordDao().find(card.wordId)!!.let {
            assertEquals(4, it.box)
            assertEquals(0, it.mistakesCount)
            assertNull(it.learnedAt)
        }
        val log = db.reviewLogDao().all()
        assertEquals(2, log.size)
        assertEquals(ReviewOutcome.OVERRIDDEN, log[1].outcome)
        assertTrue(log[1].isCorrect)
        assertEquals(3, log[1].fromBox)
        assertEquals(4, log[1].toBox)
    }
}
