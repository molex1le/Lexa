package app.lexa.domain.training

import app.lexa.domain.leitner.FixedTime
import app.lexa.domain.leitner.LeitnerScheduler
import app.lexa.domain.leitner.ReviewState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

private class FakeTrainingRepository : TrainingRepository {
    val states = mutableMapOf<Long, ReviewState>()
    val log = mutableListOf<ReviewRecord>()
    var due = emptyList<TrainingCard>()
    var hard = emptyList<TrainingCard>()
    var askedLimit = 0

    override suspend fun dueCards(nowMillis: Long, limit: Int) = due.take(limit).also { askedLimit = limit }
    override suspend fun hardCards(limit: Int) = hard.take(limit)

    override suspend fun saveAnswer(state: ReviewState, reviewedAt: Long, record: ReviewRecord): Long {
        states[record.wordId] = state
        log += record
        return log.size.toLong()
    }

    override suspend fun overrideAnswer(wordId: Long, state: ReviewState, logId: Long, toBox: Int) {
        states[wordId] = state
        val i = (logId - 1).toInt()
        log[i] = log[i].copy(outcome = AnswerOutcome.OVERRIDDEN, toBox = toBox)
    }
}

class TrainingSessionTest {
    private val time = FixedTime("2026-10-29T10:00:00Z")
    private val repo = FakeTrainingRepository()
    private val session = TrainingSession(repo, LeitnerScheduler(time), time)

    private fun card(box: Int = 1, mistakes: Int = 0) = TrainingCard(
        wordId = 7,
        term = "to commute",
        translations = listOf("ездить на работу", "поездка; дорога"),
        state = ReviewState(box = box, nextReviewAt = 0, mistakesCount = mistakes),
    )

    @Test fun `session asks for 20 due words`() = runTest {
        repo.due = List(25) { card() }
        assertEquals(20, session.build().size)
        assertEquals(TrainingSession.SESSION_SIZE, repo.askedLimit)
    }

    @Test fun `practice takes hard words`() = runTest {
        repo.hard = listOf(card(mistakes = 9))
        assertEquals(repo.hard, session.build(practice = true))
    }

    @Test fun `correct answer writes state and one log row`() = runTest {
        val r = session.answer(card(box = 2), CardDirection.EN_RU, " Дорога ", wasDue = true)
        assertIs<CheckResult.Exact>(r.check)
        assertEquals(3, repo.states.getValue(7).box)
        val row = repo.log.single()
        assertEquals(AnswerOutcome.CORRECT, row.outcome)
        assertEquals(2, row.fromBox)
        assertEquals(3, row.toBox)
        assertEquals("Дорога", row.userAnswer)
        assertEquals(time.today().toEpochDay(), row.localDay)
        assertTrue(row.isCorrect && row.wasDue)
    }

    @Test fun `typo is logged as CORRECT_TYPO`() = runTest {
        val r = session.answer(card(), CardDirection.EN_RU, "поезтка", wasDue = true)
        assertEquals(CheckResult.AcceptedWithTypo("поездка"), r.check)
        assertEquals(AnswerOutcome.CORRECT_TYPO, repo.log.single().outcome)
    }

    @Test fun `russian to english expects the term, to is optional`() = runTest {
        assertIs<CheckResult.Exact>(session.answer(card(), CardDirection.RU_EN, "commute", true).check)
        assertIs<CheckResult.Wrong>(session.answer(card(), CardDirection.RU_EN, "ездить на работу", true).check)
    }

    @Test fun `wrong then override rewrites the same row and undoes the mistake`() = runTest {
        val c = card(box = 4, mistakes = 1)
        val r = session.answer(c, CardDirection.EN_RU, "автобус", wasDue = true)
        assertIs<CheckResult.Wrong>(r.check)
        assertEquals(ReviewState(1, time.nowMillis(), 2), repo.states.getValue(7))
        assertEquals(AnswerOutcome.WRONG, repo.log.single().outcome)

        val t = session.override(c, r.logId, wasDue = true)
        assertEquals(5, t.toBox)
        assertEquals(5, repo.states.getValue(7).box)
        assertEquals(1, repo.states.getValue(7).mistakesCount)
        val row = repo.log.single()
        assertEquals(AnswerOutcome.OVERRIDDEN, row.outcome)
        assertEquals(5, row.toBox)
        assertTrue(row.isCorrect)
    }

    @Test fun `practice correct does not promote but is logged`() = runTest {
        session.answer(card(box = 3), CardDirection.EN_RU, "дорога", wasDue = false)
        assertEquals(3, repo.states.getValue(7).box)
        assertEquals(false, repo.log.single().wasDue)
        assertEquals(3, repo.log.single().toBox)
    }

    @Test fun `blank answer is wrong`() = runTest {
        assertIs<CheckResult.Wrong>(session.answer(card(), CardDirection.EN_RU, "  ", true).check)
    }
}
