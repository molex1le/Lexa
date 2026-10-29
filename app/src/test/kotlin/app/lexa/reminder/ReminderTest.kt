package app.lexa.reminder

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import app.lexa.domain.model.Direction
import app.lexa.domain.model.Settings
import app.lexa.domain.model.WordCounts
import app.lexa.domain.model.WordDetails
import app.lexa.domain.model.WordSort
import app.lexa.domain.model.WordSummary
import app.lexa.domain.repository.SettingsRepository
import app.lexa.domain.repository.WordRepository
import app.lexa.domain.time.TimeProvider
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

private class FakeWords(var due: Int) : WordRepository {
    var askedAt: Long? = null
    override fun observeDueCount(nowMillis: Long): Flow<Int> {
        askedAt = nowMillis
        return flowOf(due)
    }
    override fun observeNotLearned(): Flow<List<WordSummary>> = TODO()
    override fun observeFavorites(): Flow<List<WordSummary>> = TODO()
    override fun observeLearned(): Flow<List<WordSummary>> = TODO()
    override fun observeWord(id: Long): Flow<WordSummary?> = TODO()
    override fun observeDetails(id: Long): Flow<WordDetails?> = TODO()
    override fun observeCounts(): Flow<WordCounts> = TODO()
    override fun observeBoxDistribution(): Flow<List<Int>> = TODO()
    override suspend fun setFavorite(id: Long, favorite: Boolean) = TODO()
    override suspend fun softDelete(id: Long) = TODO()
    override suspend fun restore(id: Long) = TODO()
    override suspend fun purge(id: Long) = TODO()
    override suspend fun purgeDeletedBefore(beforeMillis: Long) = TODO()
    override suspend fun returnToLearning(id: Long) = TODO()
    override suspend fun markLearned(id: Long) = TODO()
}

private class FakeSettings(initial: Settings) : SettingsRepository {
    val flow = MutableStateFlow(initial)
    override val settings: Flow<Settings> = flow
    override suspend fun setDirection(direction: Direction) { flow.value = flow.value.copy(direction = direction) }
    override suspend fun setReminderMinutes(minutes: Int) { flow.value = flow.value.copy(reminderMinutes = minutes) }
    override suspend fun setReminderEnabled(enabled: Boolean) { flow.value = flow.value.copy(reminderEnabled = enabled) }
    override suspend fun setWordSort(sort: WordSort) { flow.value = flow.value.copy(wordSort = sort) }
    override suspend fun setVocabLevel(level: app.lexa.domain.model.VocabLevel) { flow.value = flow.value.copy(vocabLevel = level) }
    override suspend fun setSessionSize(size: Int) { flow.value = flow.value.copy(sessionSize = size) }
}

private class FakeScheduler : ReminderScheduler {
    val scheduled = mutableListOf<Int>()
    override fun schedule(minutesOfDay: Int) { scheduled += minutesOfDay }
    override fun cancel() = Unit
}

private val Minsk: ZoneId = ZoneId.of("Europe/Minsk")

private class FixedTime(dateTime: String) : TimeProvider {
    override val clock: Clock = Clock.fixed(LocalDateTime.parse(dateTime).atZone(Minsk).toInstant(), Minsk)
}

@RunWith(AndroidJUnit4::class)
class ReminderTest {
    private val context: Application = ApplicationProvider.getApplicationContext()
    private val notifications get() = shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications
    private val time = FixedTime("2026-10-29T19:00:00")

    @Before fun setUp() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ReminderNotifier.createChannel(context)
    }

    private fun runWorker(words: WordRepository, settings: SettingsRepository, scheduler: ReminderScheduler): ListenableWorker.Result {
        val factory = object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters) =
                ReviewReminderWorker(appContext, workerParameters, words, settings, scheduler, ReminderNotifier(appContext), time)
        }
        val worker = TestListenableWorkerBuilder<ReviewReminderWorker>(context).setWorkerFactory(factory).build()
        return runBlocking { worker.doWork() }
    }

    @Test fun noDueWordsNoNotificationButNextDayPlanned() {
        val scheduler = FakeScheduler()
        val result = runWorker(FakeWords(due = 0), FakeSettings(Settings(reminderEnabled = true)), scheduler)
        assertEquals(ListenableWorker.Result.success(), result)
        assertTrue(notifications.isEmpty())
        assertEquals(listOf(Settings.DEFAULT_REMINDER_MINUTES), scheduler.scheduled)
    }

    @Test fun dueWordsPostOneNotificationWithRussianPlural() {
        val words = FakeWords(due = 12)
        val scheduler = FakeScheduler()
        runWorker(words, FakeSettings(Settings(reminderEnabled = true, reminderMinutes = 8 * 60)), scheduler)
        assertEquals(1, notifications.size)
        val n = notifications.single()
        assertEquals("Пора повторить: 12 слов", n.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(ReminderNotifier.CHANNEL_ID, n.channelId)
        assertEquals(time.nowMillis(), words.askedAt)
        assertEquals(listOf(8 * 60), scheduler.scheduled)
    }

    @Test fun pluralForms() {
        val notifier = ReminderNotifier(context)
        assertEquals("Пора повторить: 1 слово", notifier.text(1))
        assertEquals("Пора повторить: 3 слова", notifier.text(3))
        assertEquals("Пора повторить: 5 слов", notifier.text(5))
        assertEquals("Пора повторить: 21 слово", notifier.text(21))
    }

    @Test fun reminderTurnedOffDoesNothing() {
        val scheduler = FakeScheduler()
        runWorker(FakeWords(due = 4), FakeSettings(Settings(reminderEnabled = false)), scheduler)
        assertTrue(notifications.isEmpty())
        assertTrue(scheduler.scheduled.isEmpty())
    }

    @Test fun schedulerEnqueuesUniqueWorkUntilChosenTimeAndCancels() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        val scheduler = WorkManagerReminderScheduler(context, FixedTime("2026-10-29T20:30:00"))
        val workManager = WorkManager.getInstance(context)

        scheduler.schedule(19 * 60)
        scheduler.schedule(19 * 60) // replaces, never doubles
        val infos = workManager.getWorkInfosForUniqueWork(WorkManagerReminderScheduler.WORK_NAME).get()
        val active = infos.filter { it.state == WorkInfo.State.ENQUEUED }
        assertEquals(1, active.size)
        // 20:30 is past 19:00: the next reminder is tomorrow, 22.5 hours away.
        assertEquals(TimeUnit.MINUTES.toMillis(22 * 60 + 30), active.single().initialDelayMillis)

        scheduler.cancel()
        val after = workManager.getWorkInfosForUniqueWork(WorkManagerReminderScheduler.WORK_NAME).get()
        assertTrue(after.none { it.state == WorkInfo.State.ENQUEUED })
    }
}
