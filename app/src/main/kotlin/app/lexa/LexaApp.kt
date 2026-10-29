package app.lexa

import android.app.Application
import app.lexa.di.ApplicationScope
import app.lexa.domain.repository.WordRepository
import app.lexa.domain.time.TimeProvider
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@HiltAndroidApp
class LexaApp : Application() {
    @Inject lateinit var words: WordRepository
    @Inject lateinit var time: TimeProvider
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        // A word swiped away just before the process died is deleted for good now (docs/ARCHITECTURE.md §6.1).
        scope.launch { words.purgeDeletedBefore(time.nowMillis() - 60_000) }
    }
}
