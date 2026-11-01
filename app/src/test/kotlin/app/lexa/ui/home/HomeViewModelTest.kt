package app.lexa.ui.home

import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.lexa.data.db.LexaDatabase
import app.lexa.data.db.entity.TranslationJson
import app.lexa.data.db.entity.WordEntity
import app.lexa.data.db.entity.WordSource
import app.lexa.data.repository.RoomStatsRepository
import app.lexa.data.repository.RoomTrainingRepository
import app.lexa.data.repository.RoomWordRepository
import app.lexa.domain.leitner.LeitnerScheduler
import app.lexa.domain.model.Settings
import app.lexa.domain.model.WordSort
import app.lexa.domain.model.Direction
import app.lexa.domain.repository.SettingsRepository
import app.lexa.domain.time.TimeProvider
import app.lexa.domain.training.TrainingSession
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.Executor
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

private val NOW = Instant.parse("2026-10-29T10:00:00Z").toEpochMilli()

private object FixedSettings : SettingsRepository {
    override val settings: Flow<Settings> = flowOf(Settings(direction = Direction.EN_RU))
    override suspend fun setDirection(direction: Direction) = Unit
    override suspend fun setReminderMinutes(minutes: Int) = Unit
    override suspend fun setReminderEnabled(enabled: Boolean) = Unit
    override suspend fun setWordSort(sort: WordSort) = Unit
    override suspend fun setVocabLevel(level: app.lexa.domain.model.VocabLevel) = Unit
    override suspend fun setSessionSize(size: Int) = Unit
    override suspend fun setDailyGoal(goal: Int) = Unit
}

private class FakeProgress : app.lexa.domain.training.DailyProgressRepository {
    val state = kotlinx.coroutines.flow.MutableStateFlow(app.lexa.domain.training.DayCount(-1, 0))
    override val count: Flow<app.lexa.domain.training.DayCount> = state
    override suspend fun add(epochDay: Long) {
        val now = state.value
        state.value = app.lexa.domain.training.DayCount(epochDay, if (now.epochDay == epochDay) now.count + 1 else 1)
    }
}

private class CountingWidgets : app.lexa.widget.WidgetRefresher {
    var refreshes = 0
    override fun refresh() { refreshes++ }
}

/** The session state machine against an in-memory database whose queries run inline. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class HomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val inline = Executor { it.run() }
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), LexaDatabase::class.java)
        .allowMainThreadQueries()
        .setQueryExecutor(inline)
        .setTransactionExecutor(inline)
        .build()
    private val time = object : TimeProvider {
        override val clock: Clock = Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC)
    }

    @Before fun main() = Dispatchers.setMain(dispatcher)

    @After fun close() {
        db.close()
        Dispatchers.resetMain()
    }

    private suspend fun word(term: String, added: Long) = db.wordDao().insert(
        WordEntity(
            term = term, termKey = term, translations = listOf(TranslationJson("перевод $term")),
            box = 1, nextReviewAt = NOW, addedAt = added, source = WordSource.TRANSLATOR, searchText = term,
        ),
    )

    private val deck by lazy { app.lexa.data.repository.AssetDeckRepository({ "" }, db, time, kotlin.random.Random(1)) }

    private val progress = FakeProgress()
    private val widgets = CountingWidgets()

    private fun viewModel() = HomeViewModel(
        SavedStateHandle(),
        RoomWordRepository(db.wordDao(), time),
        RoomStatsRepository(db.wordDao(), db.reviewLogDao(), time),
        FixedSettings,
        TrainingSession(RoomTrainingRepository(db), deck, LeitnerScheduler(time), time),
        deck,
        time,
        app.lexa.ui.translator.Pronouncer(androidx.test.core.app.ApplicationProvider.getApplicationContext()),
        progress,
        widgets,
    )

    /** Runs what is queued now, never the midnight ticker's day-long delay. */
    private fun TestScope.settle() = repeat(5) { runCurrent() }

    @Test fun `next during an override waits instead of skipping a card`() = runTest(dispatcher) {
        word("commute", added = 1)
        word("eager", added = 2)
        val vm = viewModel()
        vm.start()
        settle()
        assertEquals(1, assertIs<TrainingPhase.Question>(vm.phase.value).position)

        vm.onAnswerChange("не то")
        vm.check()
        settle()
        assertEquals(Verdict.WRONG, assertIs<TrainingPhase.Feedback>(vm.phase.value).verdict)

        vm.overrideAsTypo()
        vm.next() // tapped while the override is still being written: ignored
        settle()
        val overridden = assertIs<TrainingPhase.Feedback>(vm.phase.value)
        assertEquals(Verdict.OVERRIDDEN, overridden.verdict)
        assertEquals(1, overridden.position)

        vm.next()
        assertEquals(2, assertIs<TrainingPhase.Question>(vm.phase.value).position)
    }

    private fun TestScope.answerWrong(vm: HomeViewModel) {
        vm.onAnswerChange("не то")
        vm.check()
        settle()
    }

    private fun TestScope.answerRight(vm: HomeViewModel, text: String) {
        vm.onAnswerChange(text)
        vm.check()
        settle()
    }

    @Test fun `a wrong answer comes back at the end of the session`() = runTest(dispatcher) {
        word("commute", added = 1)
        word("eager", added = 2)
        val vm = viewModel()
        vm.start()
        settle()
        answerWrong(vm)
        vm.next()
        // The second card is a first answer; the retry of the first one follows it.
        val second = assertIs<TrainingPhase.Question>(vm.phase.value)
        assertEquals(2, second.position)
        assertEquals(3, second.total)
        answerRight(vm, "перевод eager")
        vm.next()
        val retry = assertIs<TrainingPhase.Question>(vm.phase.value)
        assertEquals(3, retry.position)
        assertEquals(true, retry.retry)
        assertEquals("commute", retry.card.prompt)
        assertEquals(1, retry.card.box)
    }

    @Test fun `a retry writes nothing and the summary counts the first answers only`() = runTest(dispatcher) {
        word("commute", added = 1)
        val vm = viewModel()
        vm.start()
        settle()
        answerWrong(vm)
        vm.next()
        val before = db.reviewLogDao().let { dao -> db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM review_log").use { it.moveToFirst(); it.getInt(0) } }
        answerRight(vm, "перевод commute")
        val feedback = assertIs<TrainingPhase.Feedback>(vm.phase.value)
        assertEquals(Verdict.CORRECT, feedback.verdict)
        assertEquals(true, feedback.retry)
        val after = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM review_log").use { it.moveToFirst(); it.getInt(0) }
        assertEquals(before, after)
        vm.next()
        val done = assertIs<TrainingPhase.Done>(vm.phase.value)
        assertEquals(0, done.correct)
        assertEquals(1, done.total)
    }

    @Test fun `a wrong retry comes round at most twice`() = runTest(dispatcher) {
        word("commute", added = 1)
        val vm = viewModel()
        vm.start()
        settle()
        answerWrong(vm) // first answer
        repeat(2) {
            vm.next()
            assertEquals(true, assertIs<TrainingPhase.Question>(vm.phase.value).retry)
            answerWrong(vm)
        }
        vm.next()
        assertIs<TrainingPhase.Done>(vm.phase.value)
    }

    @Test fun `typo override takes the queued retry away`() = runTest(dispatcher) {
        word("commute", added = 1)
        val vm = viewModel()
        vm.start()
        settle()
        answerWrong(vm)
        vm.overrideAsTypo()
        settle()
        vm.next()
        assertIs<TrainingPhase.Done>(vm.phase.value)
    }

    @Test fun `removing the word takes the queued retry away`() = runTest(dispatcher) {
        word("commute", added = 1)
        val vm = viewModel()
        vm.start()
        settle()
        answerWrong(vm)
        vm.removeWord()
        settle()
        vm.next()
        assertIs<TrainingPhase.Done>(vm.phase.value)
    }

    @Test fun `each first answer counts towards the daily goal, retries do not`() = runTest(dispatcher) {
        word("commute", added = 1)
        word("eager", added = 2)
        val vm = viewModel()
        vm.start()
        settle()
        answerWrong(vm)
        vm.next()
        answerRight(vm, "перевод eager")
        vm.next()
        answerRight(vm, "перевод commute") // the retry
        assertEquals(2, progress.state.value.count)
        assertEquals(time.today().toEpochDay(), progress.state.value.epochDay)
        assertEquals(true, widgets.refreshes > 0)
    }
}
