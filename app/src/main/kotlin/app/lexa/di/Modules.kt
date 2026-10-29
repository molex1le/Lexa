package app.lexa.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import app.lexa.data.db.LexaDatabase
import app.lexa.data.db.dao.HistoryDao
import app.lexa.data.db.dao.ReviewLogDao
import app.lexa.data.db.dao.TranslationCacheDao
import app.lexa.data.db.dao.WordDao
import app.lexa.data.dictionary.AndroidNetworkStatus
import app.lexa.data.dictionary.CompositeDictionaryRepository
import app.lexa.data.dictionary.FreeDictionarySource
import app.lexa.data.dictionary.ResilientMachineTranslator
import app.lexa.data.dictionary.TatoebaSource
import app.lexa.data.repository.RoomHistoryRepository
import app.lexa.data.repository.RoomTranslatedWordRepository
import app.lexa.data.repository.RoomTrainingRepository
import app.lexa.domain.training.TrainingRepository
import app.lexa.domain.repository.DictionaryRepository
import app.lexa.domain.repository.DictionarySource
import app.lexa.domain.repository.ExampleSentenceSource
import app.lexa.domain.repository.MachineTranslator
import app.lexa.domain.repository.NetworkStatus
import app.lexa.domain.repository.TranslatedWordRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import kotlinx.serialization.json.Json
import app.lexa.data.repository.RoomReviewRepository
import app.lexa.data.repository.RoomStatsRepository
import app.lexa.data.repository.RoomWordRepository
import app.lexa.data.settings.DataStoreSettingsRepository
import app.lexa.domain.repository.HistoryRepository
import app.lexa.domain.repository.ReviewRepository
import app.lexa.domain.repository.StatsRepository
import app.lexa.domain.repository.SettingsRepository
import app.lexa.domain.repository.WordRepository
import app.lexa.domain.time.TimeProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class ApplicationScope
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class IoDispatcher
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class DefaultDispatcher

private class SystemTimeProvider : TimeProvider {
    // Read on every access: a time-zone change on the device is picked up without a restart.
    override val clock: Clock get() = Clock.systemDefaultZone()
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton @ApplicationScope
    fun applicationScope(@DefaultDispatcher dispatcher: CoroutineDispatcher): CoroutineScope =
        CoroutineScope(SupervisorJob() + dispatcher)

    /** Session shuffling and the built-in deck; tests pass a seeded one. */
    @Provides fun random(): kotlin.random.Random = kotlin.random.Random.Default

    @Provides @IoDispatcher
    fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides @DefaultDispatcher
    fun defaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides @Singleton
    fun timeProvider(): TimeProvider = SystemTimeProvider()
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun database(@ApplicationContext context: Context): LexaDatabase =
        Room.databaseBuilder(context, LexaDatabase::class.java, LexaDatabase.NAME).build()

    @Provides fun wordDao(db: LexaDatabase): WordDao = db.wordDao()
    @Provides fun reviewLogDao(db: LexaDatabase): ReviewLogDao = db.reviewLogDao()
    @Provides fun translationCacheDao(db: LexaDatabase): TranslationCacheDao = db.translationCacheDao()
    @Provides fun historyDao(db: LexaDatabase): HistoryDao = db.historyDao()
}

@Module
@InstallIn(SingletonComponent::class)
object SettingsModule {
    @Provides @Singleton
    fun dataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun wordRepository(impl: RoomWordRepository): WordRepository
    @Binds abstract fun reviewRepository(impl: RoomReviewRepository): ReviewRepository
    @Binds abstract fun statsRepository(impl: RoomStatsRepository): StatsRepository
    @Binds abstract fun historyRepository(impl: RoomHistoryRepository): HistoryRepository
    @Binds abstract fun settingsRepository(impl: DataStoreSettingsRepository): SettingsRepository
    @Binds abstract fun translatedWordRepository(impl: RoomTranslatedWordRepository): TranslatedWordRepository
    @Binds abstract fun trainingRepository(impl: RoomTrainingRepository): TrainingRepository
    @Binds abstract fun deckRepository(impl: app.lexa.data.repository.AssetDeckRepository): app.lexa.domain.training.DeckRepository
    @Binds abstract fun reminderScheduler(impl: app.lexa.reminder.WorkManagerReminderScheduler): app.lexa.reminder.ReminderScheduler
}

/** Translator sources (ADR-0001). Swap a source by binding another implementation here. */
@Module
@InstallIn(SingletonComponent::class)
abstract class DictionaryModule {
    @Binds abstract fun dictionaryRepository(impl: CompositeDictionaryRepository): DictionaryRepository
    @Binds abstract fun dictionarySource(impl: FreeDictionarySource): DictionarySource
    @Binds abstract fun exampleSource(impl: TatoebaSource): ExampleSentenceSource
    @Binds abstract fun machineTranslator(impl: ResilientMachineTranslator): MachineTranslator
    @Binds abstract fun networkStatus(impl: AndroidNetworkStatus): NetworkStatus

    companion object {
        @Provides @Singleton
        fun json(): Json = Json { ignoreUnknownKeys = true }

        @Provides @Singleton
        fun httpClient(): HttpClient = HttpClient(OkHttp) {
            expectSuccess = false
            install(HttpTimeout) { requestTimeoutMillis = CompositeDictionaryRepository.SOURCE_TIMEOUT_MS }
        }
    }
}
