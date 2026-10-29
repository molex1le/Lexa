package app.lexa.domain.training

import app.lexa.domain.leitner.FixedTime
import app.lexa.domain.leitner.LeitnerScheduler
import app.lexa.domain.leitner.ReviewState
import app.lexa.domain.model.VocabLevel
import kotlin.test.Test
import kotlin.test.assertNull
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

    val listTranslations = mutableMapOf<String, List<String>>()
    val removed = mutableListOf<Long>()
    val learned = mutableListOf<Long>()
    override suspend fun translationsOf(term: String) = listTranslations[term].orEmpty()
    override suspend fun remove(wordId: Long) { removed += wordId }
    override suspend fun markLearned(wordId: Long, nowMillis: Long) { learned += wordId }
}

private class FakeDeck : DeckRepository {
    var words = emptyList<DeckWord>()
    val known = mutableListOf<String>()
    val added = mutableListOf<String>()
    val undone = mutableListOf<Long>()

    override suspend fun randomUnseen(level: VocabLevel, count: Int) = words.filter { it.level == level }.take(count)
    override suspend fun sample(perLevel: Int) = words.groupBy { it.level }
    override suspend fun markKnown(term: String) { known += term }
    override suspend fun addAsNew(word: DeckWord, record: ReviewRecord): Pair<Long, Long> {
        added += word.term
        return 100L + added.size to 500L + added.size
    }
    override suspend fun undoAddAsNew(wordId: Long, term: String) {
        undone += wordId
        known += term
    }

    var addedToday = 0
    val deckTranslations = mutableMapOf<String, String>()
    override suspend fun addedSince(millis: Long) = addedToday
    override suspend fun enrich(wordId: Long, term: String) = Unit
    override suspend fun translationsOf(term: String) = deckTranslations[term]
}

class TrainingSessionTest {
    private val time = FixedTime("2026-10-29T10:00:00Z")
    private val repo = FakeTrainingRepository()
    private val deck = FakeDeck()
    private val session = TrainingSession(repo, deck, LeitnerScheduler(time), time, kotlin.random.Random(1))

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

        val t = session.override(c, r, wasDue = true)
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

    private val reluctant = DeckWord(VocabLevel.B2, "reluctant", "неохотный, вынужденный")

    @Test fun `a session tops up due words with deck words of the level`() = runTest {
        repo.due = List(3) { card() }
        deck.words = List(30) { DeckWord(VocabLevel.B1, "w$it", "с$it") }
        val cards = session.build(level = VocabLevel.B1)
        assertEquals(TrainingSession.SESSION_SIZE, cards.size)
        assertEquals(3, cards.count { it.deck == null })
        assertEquals(TrainingSession.maxDue(TrainingSession.SESSION_SIZE), repo.askedLimit)
    }

    @Test fun `without a level only due words are trained`() = runTest {
        repo.due = List(3) { card() }
        deck.words = List(30) { DeckWord(VocabLevel.B1, "w$it", "с$it") }
        assertTrue(session.build().all { it.deck == null })
    }

    @Test fun `practice ignores the deck`() = runTest {
        repo.hard = List(2) { card() }
        deck.words = List(30) { DeckWord(VocabLevel.B1, "w$it", "с$it") }
        assertEquals(2, session.build(practice = true, level = VocabLevel.B1).size)
    }

    @Test fun `a known deck word is marked known and enters no box`() = runTest {
        val c = with(TrainingSession) { reluctant.toCard() }
        val r = session.answer(c, CardDirection.EN_RU, "неохотный", wasDue = false)
        assertTrue(r.check.isCorrect)
        assertEquals(listOf("reluctant"), deck.known)
        assertEquals(TrainingSession.DECK_KNOWN, r.transition.toBox)
        assertNull(r.newWordId)
        assertTrue(repo.log.isEmpty())
    }

    @Test fun `an unknown deck word joins box 1 with one mistake and override removes it`() = runTest {
        val c = with(TrainingSession) { reluctant.toCard() }
        val r = session.answer(c, CardDirection.EN_RU, "медленный", wasDue = false)
        assertIs<CheckResult.Wrong>(r.check)
        assertEquals(listOf("reluctant"), deck.added)
        assertEquals(1, r.transition.state.box)
        assertEquals(1, r.transition.state.mistakesCount)
        assertEquals(101L, r.newWordId)

        val t = session.override(c, r, wasDue = false)
        assertEquals(listOf(101L), deck.undone)
        assertEquals(listOf("reluctant"), deck.known)
        assertEquals(TrainingSession.DECK_KNOWN, t.toBox)
    }

    @Test fun `deck word answered in the other direction expects the term`() = runTest {
        val c = with(TrainingSession) { DeckWord(VocabLevel.A1, "to eat", "есть, кушать").toCard() }
        assertTrue(session.answer(c, CardDirection.RU_EN, "eat", wasDue = false).check.isCorrect)
    }

    @Test fun `no new built-in words once ten joined today`() = runTest {
        repo.due = List(3) { card() }
        deck.words = List(30) { DeckWord(VocabLevel.B1, "w$it", "с$it") }
        deck.addedToday = TrainingSession.DAILY_NEW_LIMIT
        assertTrue(session.build(level = VocabLevel.B1).all { it.deck == null })
    }

    @Test fun `session size sets the number of cards and due words take at most 60 percent`() = runTest {
        repo.due = List(30) { card() }
        deck.words = List(30) { DeckWord(VocabLevel.B1, "w$it", "с$it") }
        val cards = session.build(level = VocabLevel.B1, limit = 10)
        assertEquals(10, cards.size)
        assertEquals(6, cards.count { it.deck == null })
    }

    @Test fun `give up is wrong without looking at the text`() = runTest {
        val r = session.answer(card(box = 3), CardDirection.EN_RU, "", wasDue = true, mode = AnswerMode.GAVE_UP)
        assertIs<CheckResult.Wrong>(r.check)
        assertEquals(1, r.transition.state.box)
    }

    @Test fun `a right answer after a hint does not move the word up`() = runTest {
        val r = session.answer(card(box = 2), CardDirection.EN_RU, "поездка", wasDue = true, mode = AnswerMode.HINTED)
        assertTrue(r.check.isCorrect)
        assertEquals(2, r.transition.state.box)
    }

    @Test fun `a hinted built-in word joins the new words`() = runTest {
        val c = with(TrainingSession) { reluctant.toCard() }
        session.answer(c, CardDirection.EN_RU, "неохотный", wasDue = false, mode = AnswerMode.HINTED)
        assertEquals(listOf("reluctant"), deck.added)
    }

    @Test fun `hint shows the first letter and a slot per letter`() =
        assertEquals("j _ _ _ _ _ _", TrainingSession.hint(card().copy(term = "journey"), CardDirection.RU_EN))

    @Test fun `another English word with the same translation is right without credit`() = runTest {
        val c = card(box = 2).copy(term = "journey", translations = listOf("поездка, путешествие"))
        deck.deckTranslations["trip"] = "поездка, путешествие"
        val r = session.answer(c, CardDirection.RU_EN, "trip", wasDue = true)
        assertEquals("journey", r.alsoRight)
        assertEquals(2, r.transition.state.box)
        assertTrue(repo.log.isEmpty())
    }

    @Test fun `a dictionary synonym is right without credit`() = runTest {
        val c = card(box = 2).copy(term = "journey", synonyms = listOf("voyage"))
        assertEquals("journey", session.answer(c, CardDirection.RU_EN, "Voyage", wasDue = true).alsoRight)
    }

    @Test fun `an unrelated English word is still wrong`() = runTest {
        val c = card(box = 2).copy(term = "journey", translations = listOf("поездка"))
        deck.deckTranslations["apple"] = "яблоко"
        val r = session.answer(c, CardDirection.RU_EN, "apple", wasDue = true)
        assertNull(r.alsoRight)
        assertIs<CheckResult.Wrong>(r.check)
    }

    @Test fun `remove and mark known reach the right store`() = runTest {
        session.remove(card(), null)
        session.markKnown(card(), null)
        assertEquals(listOf(7L), repo.removed)
        assertEquals(listOf(7L), repo.learned)
        val c = with(TrainingSession) { reluctant.toCard() }
        session.markKnown(c, null)
        assertTrue("reluctant" in deck.known)
    }
}
