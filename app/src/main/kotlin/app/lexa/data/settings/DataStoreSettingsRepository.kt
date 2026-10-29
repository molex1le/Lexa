package app.lexa.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.lexa.domain.model.Direction
import app.lexa.domain.model.Settings
import app.lexa.domain.model.VocabLevel
import app.lexa.domain.model.WordSort
import app.lexa.domain.repository.SettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Settings in DataStore Preferences (docs/ARCHITECTURE.md §11). Keys added as phases need them. */
class DataStoreSettingsRepository @Inject constructor(
    private val store: DataStore<Preferences>,
) : SettingsRepository {

    override val settings: Flow<Settings> = store.data.map { prefs ->
        Settings(
            direction = prefs[Keys.direction]
                ?.let { name -> Direction.entries.firstOrNull { it.name == name } }
                ?: Direction.EN_RU,
            reminderMinutes = prefs[Keys.reminderTime] ?: Settings.DEFAULT_REMINDER_MINUTES,
            reminderEnabled = prefs[Keys.reminderEnabled] ?: false,
            wordSort = prefs[Keys.wordSort]
                ?.let { name -> WordSort.entries.firstOrNull { it.name == name } }
                ?: WordSort.ADDED,
            vocabLevel = prefs[Keys.vocabLevel]?.let { name -> VocabLevel.entries.firstOrNull { it.name == name } },
        )
    }

    override suspend fun setDirection(direction: Direction) {
        store.edit { it[Keys.direction] = direction.name }
    }

    override suspend fun setReminderMinutes(minutes: Int) {
        store.edit { it[Keys.reminderTime] = minutes.coerceIn(0, 24 * 60 - 1) }
    }

    override suspend fun setReminderEnabled(enabled: Boolean) {
        store.edit { it[Keys.reminderEnabled] = enabled }
    }

    override suspend fun setWordSort(sort: WordSort) {
        store.edit { it[Keys.wordSort] = sort.name }
    }

    override suspend fun setVocabLevel(level: VocabLevel) {
        store.edit { it[Keys.vocabLevel] = level.name }
    }

    private object Keys {
        val direction = stringPreferencesKey("direction")
        val reminderTime = intPreferencesKey("reminderTime")
        val reminderEnabled = booleanPreferencesKey("reminderEnabled")
        val wordSort = stringPreferencesKey("wordSort")
        val vocabLevel = stringPreferencesKey("vocabLevel")
    }
}
