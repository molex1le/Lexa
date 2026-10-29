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

    private fun viewModel() = HomeViewModel(
        SavedStateHandle(),
        RoomWordRepository(db.wordDao(), time),
        RoomStatsRepository(db.wordDao(), db.reviewLogDao(), time),
        FixedSettings,
        TrainingSession(RoomTrainingRepository(db), deck, LeitnerScheduler(time), time),
        deck,
        time,
        app.lexa.ui.translator.Pronouncer(androidx.test.core.app.ApplicationProvider.getApplicationContext()),
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
}
