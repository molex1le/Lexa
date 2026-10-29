package app.lexa

import app.lexa.update.UpdateCheckWorker

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import app.lexa.reminder.ReminderNotifier
import app.lexa.di.ApplicationScope
import app.lexa.domain.repository.WordRepository
import app.lexa.domain.time.TimeProvider
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@HiltAndroidApp
class LexaApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var words: WordRepository
    @Inject lateinit var time: TimeProvider
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        ReminderNotifier.createChannel(this)
        UpdateCheckWorker.schedule(this)
        // A word swiped away just before the process died is deleted for good now (docs/ARCHITECTURE.md §6.1).
        scope.launch { words.purgeDeletedBefore(time.nowMillis() - 60_000) }
    }

    // WorkManager starts on demand with Hilt's factory; its default initializer is removed in the manifest.
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
}
