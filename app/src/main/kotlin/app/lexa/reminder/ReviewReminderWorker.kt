package app.lexa.reminder

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.lexa.domain.repository.SettingsRepository
import app.lexa.domain.repository.WordRepository
import app.lexa.domain.time.TimeProvider
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/** Daily: if any words are due, one notification «Пора повторить: N слов»; then plans tomorrow's run. */
@HiltWorker
class ReviewReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val words: WordRepository,
    private val settings: SettingsRepository,
    private val scheduler: ReminderScheduler,
    private val notifier: ReminderNotifier,
    private val time: TimeProvider,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val current = settings.settings.first()
        if (!current.reminderEnabled) return Result.success()
        val due = words.observeDueCount(time.nowMillis()).first()
        if (due > 0) notifier.show(due)
        scheduler.schedule(current.reminderMinutes, current.reminderDays)
        return Result.success()
    }
}
