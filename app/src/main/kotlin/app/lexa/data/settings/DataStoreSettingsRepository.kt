package app.lexa.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.lexa.domain.model.Direction
import app.lexa.domain.model.ReminderDays
import app.lexa.domain.model.Settings
import app.lexa.domain.model.ThemeMode
import app.lexa.domain.model.UiFont
import app.lexa.domain.model.WordFont
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

    override val settings: Flow<Settings> = store.data.map { prefs -> read(prefs) }

    private fun read(prefs: Preferences): Settings {
        return Settings(
            direction = prefs[Keys.direction]
                ?.let { name -> Direction.entries.firstOrNull { it.name == name } }
                ?: Direction.EN_RU,
            reminderMinutes = prefs[Keys.reminderTime] ?: Settings.DEFAULT_REMINDER_MINUTES,
            reminderEnabled = prefs[Keys.reminderEnabled] ?: false,
            wordSort = prefs[Keys.wordSort]
                ?.let { name -> WordSort.entries.firstOrNull { it.name == name } }
                ?: WordSort.ADDED,
            vocabLevel = prefs[Keys.vocabLevel]?.let { name -> VocabLevel.entries.firstOrNull { it.name == name } },
            sessionSize = prefs[Keys.sessionSize]?.coerceIn(Settings.SESSION_SIZES) ?: Settings.DEFAULT_SESSION_SIZE,
            dailyGoal = prefs[Keys.dailyGoal]?.coerceIn(Settings.DAILY_GOALS) ?: Settings.DEFAULT_DAILY_GOAL,
            themeMode = prefs[Keys.themeMode].asEnum(ThemeMode.SYSTEM),
            wordFont = prefs[Keys.wordFont].asEnum(WordFont.LITERATA),
            uiFont = prefs[Keys.uiFont].asEnum(UiFont.SOURCE_SANS),
            textScalePercent = prefs[Keys.textScale]?.takeIf { it in Settings.TEXT_SCALES } ?: Settings.DEFAULT_TEXT_SCALE,
            reminderDays = (prefs[Keys.reminderDays] ?: ReminderDays.ALL).and(ReminderDays.ALL),
            newWordsPerDay = prefs[Keys.newWordsPerDay]?.coerceIn(Settings.NEW_WORDS_PER_DAY) ?: Settings.DEFAULT_NEW_WORDS_PER_DAY,
            autoPronounce = prefs[Keys.autoPronounce] ?: false,
            haptics = prefs[Keys.haptics] ?: true,
            updateCheck = prefs[Keys.updateCheck] ?: true,
        )
    }

    private inline fun <reified E : Enum<E>> String?.asEnum(default: E): E =
        enumValues<E>().firstOrNull { it.name == this } ?: default

    /** Reads, changes and writes back every setting in one DataStore transaction. */
    override suspend fun update(transform: (Settings) -> Settings) {
        store.edit { prefs ->
            val next = transform(read(prefs))
            prefs[Keys.themeMode] = next.themeMode.name
            prefs[Keys.wordFont] = next.wordFont.name
            prefs[Keys.uiFont] = next.uiFont.name
            prefs[Keys.textScale] = next.textScalePercent
            prefs[Keys.reminderDays] = next.reminderDays and ReminderDays.ALL
            prefs[Keys.newWordsPerDay] = next.newWordsPerDay.coerceIn(Settings.NEW_WORDS_PER_DAY)
            prefs[Keys.autoPronounce] = next.autoPronounce
            prefs[Keys.haptics] = next.haptics
            prefs[Keys.updateCheck] = next.updateCheck
        }
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

    override suspend fun setDailyGoal(goal: Int) {
        store.edit { it[Keys.dailyGoal] = goal.coerceIn(Settings.DAILY_GOALS) }
    }

    override suspend fun setSessionSize(size: Int) {
        store.edit { it[Keys.sessionSize] = size.coerceIn(Settings.SESSION_SIZES) }
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
        val sessionSize = intPreferencesKey("sessionSize")
        val dailyGoal = intPreferencesKey("dailyGoal")
        val themeMode = stringPreferencesKey("themeMode")
        val wordFont = stringPreferencesKey("wordFont")
        val uiFont = stringPreferencesKey("uiFont")
        val textScale = intPreferencesKey("textScale")
        val reminderDays = intPreferencesKey("reminderDays")
        val newWordsPerDay = intPreferencesKey("newWordsPerDay")
        val autoPronounce = booleanPreferencesKey("autoPronounce")
        val haptics = booleanPreferencesKey("haptics")
        val updateCheck = booleanPreferencesKey("updateCheck")
    }
}
