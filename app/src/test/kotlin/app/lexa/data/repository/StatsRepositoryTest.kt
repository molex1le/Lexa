package app.lexa.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.lexa.data.db.LexaDatabase
import app.lexa.data.db.entity.ReviewDirection
import app.lexa.data.db.entity.ReviewLogEntity
import app.lexa.data.db.entity.ReviewOutcome
import app.lexa.data.db.entity.TranslationJson
import app.lexa.data.db.entity.WordEntity
import app.lexa.data.db.entity.WordSource
import app.lexa.domain.model.AnswerTotals
import app.lexa.domain.stats.LearnedPeriods
import app.lexa.domain.stats.Streak
import app.lexa.domain.time.TimeProvider
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

private val ZONE: ZoneId = ZoneId.of("Europe/Minsk")

/** Thursday 29.10.2026, noon in Minsk: the week starts on Monday 26.10, the month on 1.10. */
private val TODAY: LocalDate = LocalDate.of(2026, 10, 29)

private fun at(date: String, time: String) =
    LocalDateTime.parse("${date}T$time").atZone(ZONE).toInstant().toEpochMilli()

/** Statistics queries and soft delete on an in-memory Room database with a fixed clock. */
@RunWith(AndroidJUnit4::class)
class StatsRepositoryTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), LexaDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val time = object : TimeProvider {
        override val clock: Clock = Clock.fixed(TODAY.atTime(12, 0).atZone(ZONE).toInstant(), ZONE)
    }
    private val stats = RoomStatsRepository(db.wordDao(), db.reviewLogDao(), time)
    private val words = RoomWordRepository(db.wordDao(), time)

    @After fun close() = db.close()

    private suspend fun word(
        term: String,
        box: Int = 1,
        learned: Long? = null,
        mistakes: Int = 0,
        added: Long = 0,
        deleted: Long? = null,
    ) = db.wordDao().insert(
        WordEntity(
            term = term, termKey = term, translations = listOf(TranslationJson("перевод $term")),
            box = box, nextReviewAt = 0, addedAt = added, learnedAt = learned, mistakesCount = mistakes,
            deletedAt = deleted, source = WordSource.TRANSLATOR, searchText = term,
        ),
    )

    private suspend fun answer(wordId: Long, day: LocalDate, correct: Boolean) = db.reviewLogDao().insert(
        ReviewLogEntity(
            wordId = wordId, answeredAt = day.atTime(10, 0).atZone(ZONE).toInstant().toEpochMilli(),
            localDay = day.toEpochDay(), direction = ReviewDirection.EN_RU, userAnswer = "",
            outcome = if (correct) ReviewOutcome.CORRECT else ReviewOutcome.WRONG,
            isCorrect = correct, fromBox = 1, toBox = if (correct) 2 else 1, wasDue = true,
        ),
    )

    private suspend fun learnedFixture() {
        word("today", learned = at("2026-10-29", "09:00"))
        word("monday", learned = at("2026-10-26", "00:30"))
        word("sunday", learned = at("2026-10-25", "23:30"))
        word("first-bin", learned = at("2026-09-30", "23:59"))
        word("too-old", learned = at("2026-09-29", "12:00"))
        word("deleted", learned = at("2026-10-29", "08:00"), deleted = 1)
        word("learning", box = 3)
    }

    @Test fun `learned today, week and month use local day, Monday and the 1st as boundaries`() = runTest {
        learnedFixture()
        assertEquals(LearnedPeriods(today = 1, week = 2, month = 3), stats.observeLearnedPeriods(TODAY).first())
    }

    @Test fun `30 day bins end today, oldest first, empty days are zero`() = runTest {
        learnedFixture()
        val bins = stats.observeLearnedByDay(TODAY, 30).first()
        assertEquals(30, bins.size)
        assertEquals(1, bins[0]) // 30.09
        assertEquals(1, bins[25]) // 25.10
        assertEquals(1, bins[26]) // 26.10
        assertEquals(1, bins[29]) // today
        assertEquals(4, bins.sum())
    }

    @Test fun `nothing learned is thirty zeros and zero periods`() = runTest {
        word("learning", box = 2)
        assertEquals(List(30) { 0 }, stats.observeLearnedByDay(TODAY, 30).first())
        assertEquals(LearnedPeriods(), stats.observeLearnedPeriods(TODAY).first())
    }

    @Test fun `accuracy for the week and all time`() = runTest {
        val id = word("commute", box = 2)
        answer(id, TODAY, correct = true)
        answer(id, TODAY.minusDays(3), correct = false) // Monday: inside the week
        answer(id, TODAY.minusDays(4), correct = true) // Sunday: last week
        val accuracy = stats.observeAccuracy(TODAY).first()
        assertEquals(AnswerTotals(answers = 2, correct = 1), accuracy.week)
        assertEquals(AnswerTotals(answers = 3, correct = 2), accuracy.all)
        assertEquals(67, accuracy.all.accuracyPercent)
    }

    @Test fun `no answers leaves accuracy undefined`() = runTest {
        val accuracy = stats.observeAccuracy(TODAY).first()
        assertNull(accuracy.week.accuracyPercent)
        assertNull(accuracy.all.accuracyPercent)
    }

    @Test fun `streak from practice days`() = runTest {
        val id = word("commute", box = 2)
        answer(id, TODAY.minusDays(1), correct = true)
        answer(id, TODAY.minusDays(2), correct = false)
        answer(id, TODAY.minusDays(2), correct = true)
        answer(id, TODAY.minusDays(9), correct = true)
        assertEquals(Streak(current = 2, best = 2), stats.observeStreak(TODAY).first())
    }

    @Test fun `hard words are words in learning, most mistakes first, then oldest`() = runTest {
        word("a", box = 2, mistakes = 3, added = 2)
        word("b", box = 1, mistakes = 5, added = 3)
        word("c", box = 4, mistakes = 3, added = 1)
        word("learned", learned = 1, mistakes = 9)
        word("deleted", mistakes = 7, deleted = 1)
        word("clean", box = 2)
        assertEquals(listOf("b", "c", "a"), stats.observeHardWords(5).first().map { it.term })
        assertEquals(listOf("b", "c"), stats.observeHardWords(2).first().map { it.term })
    }

    @Test fun `box distribution covers words in learning and sums to them`() = runTest {
        word("one-a", box = 1)
        word("one-b", box = 1)
        word("three", box = 3)
        word("five", box = 5)
        word("learned", box = 5, learned = 1)
        word("deleted", box = 2, deleted = 1)
        val boxes = words.observeBoxDistribution().first()
        assertEquals(listOf(2, 0, 1, 0, 1), boxes)
        val counts = words.observeCounts().first()
        assertEquals(counts.new + counts.learning, boxes.sum())
        assertEquals(1, counts.learned)
    }

    @Test fun `soft delete hides the word, undo brings it back`() = runTest {
        val id = word("commute", box = 2)
        words.softDelete(id)
        assertEquals(emptyList(), words.observeNotLearned().first())
        assertEquals(0, words.observeCounts().first().learning)
        assertNull(words.observeDetails(id).first())
        words.restore(id)
        assertEquals(listOf("commute"), words.observeNotLearned().first().map { it.term })
    }

    @Test fun `purge deletes a soft-deleted word with its history, never a live one`() = runTest {
        val live = word("live", box = 2)
        val gone = word("gone", box = 2)
        answer(gone, TODAY, correct = true)
        answer(live, TODAY, correct = true)
        words.purge(live)
        words.softDelete(gone)
        words.purge(gone)
        assertEquals(listOf(live), db.reviewLogDao().all().map { it.wordId })
        assertNull(db.wordDao().find(gone))
        assertEquals("live", db.wordDao().find(live)!!.term)
    }

    @Test fun `purge on start removes only words deleted before the cutoff`() = runTest {
        word("old", deleted = 100)
        word("recent", deleted = 500)
        word("live")
        words.purgeDeletedBefore(200)
        assertNull(db.wordDao().findByKey("old"))
        assertEquals(500L, db.wordDao().findByKey("recent")!!.deletedAt)
        assertEquals(null, db.wordDao().findByKey("live")!!.deletedAt)
    }

    @Test fun `return to learning puts a learned word in box 1, due today`() = runTest {
        val id = word("commute", box = 5, learned = at("2026-10-20", "10:00"))
        words.returnToLearning(id)
        db.wordDao().find(id)!!.let {
            assertNull(it.learnedAt)
            assertEquals(1, it.box)
            assertEquals(TODAY.atStartOfDay(ZONE).toInstant().toEpochMilli(), it.nextReviewAt)
        }
        assertEquals(listOf("commute"), words.observeNotLearned().first().map { it.term })
    }

    @Test fun `details group translations by part of speech`() = runTest {
        val id = db.wordDao().insert(
            WordEntity(
                term = "commute", termKey = "commute",
                translations = listOf(
                    TranslationJson("ездить на работу", "VERB"),
                    TranslationJson("поездка на работу", "NOUN"),
                    TranslationJson("добираться", "VERB"),
                ),
                nextReviewAt = 0, addedAt = 0, source = WordSource.TRANSLATOR, searchText = "commute",
            ),
        )
        val senses = words.observeDetails(id).first()!!.entry.senses
        assertEquals(listOf(listOf("ездить на работу", "добираться"), listOf("поездка на работу")), senses.map { it.translations })
    }
}
