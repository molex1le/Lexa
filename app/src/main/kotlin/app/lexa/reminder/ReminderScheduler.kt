package app.lexa.reminder

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import app.lexa.domain.reminder.ReminderTime
import app.lexa.domain.time.TimeProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** Plans the daily review reminder (docs/ARCHITECTURE.md §13). */
interface ReminderScheduler {
    /** Replaces any planned reminder with one at the next [minutesOfDay]. */
    fun schedule(minutesOfDay: Int)
    fun cancel()
}

/**
 * One unique one-time work per day, re-planned by the worker itself after it runs: the reminder stays at
 * the chosen wall-clock time across days and DST changes, which a 24-hour periodic work would drift from.
 * WorkManager keeps it across reboots.
 */
class WorkManagerReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val time: TimeProvider,
) : ReminderScheduler {

    override fun schedule(minutesOfDay: Int) {
        val delay = ReminderTime.delayUntilNext(time.clock, minutesOfDay)
        val request = OneTimeWorkRequestBuilder<ReviewReminderWorker>()
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    override fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    companion object {
        const val WORK_NAME = "review-reminder"
    }
}
