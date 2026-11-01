package app.lexa.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.lexa.domain.model.ReminderDays
import app.lexa.domain.model.Settings
import app.lexa.domain.model.ThemeMode
import app.lexa.domain.model.UiFont
import app.lexa.domain.model.WordFont
import java.io.File
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The settings on a real DataStore file: what is written is what comes back, and bad values fall back. */
class SettingsStoreTest {
    @get:Rule val folder = TemporaryFolder()

    private fun store(name: String) = PreferenceDataStoreFactory.create { File(folder.root, "$name.preferences_pb") }

    @Test fun `nothing stored gives the defaults`() = runTest {
        assertEquals(Settings(), DataStoreSettingsRepository(store("empty")).settings.first())
    }

    @Test fun `every new setting survives a write and a read`() = runTest {
        val repository = DataStoreSettingsRepository(store("roundtrip"))
        repository.update {
            it.copy(
                themeMode = ThemeMode.DARK,
                wordFont = WordFont.CAVEAT,
                uiFont = UiFont.NUNITO,
                textScalePercent = 115,
                reminderDays = 0b0011111,
                newWordsPerDay = 0,
                autoPronounce = true,
                haptics = false,
                updateCheck = false,
            )
        }
        val read = repository.settings.first()
        assertEquals(ThemeMode.DARK, read.themeMode)
        assertEquals(WordFont.CAVEAT, read.wordFont)
        assertEquals(UiFont.NUNITO, read.uiFont)
        assertEquals(115, read.textScalePercent)
        assertEquals(0b0011111, read.reminderDays)
        assertEquals(0, read.newWordsPerDay)
        assertEquals(true, read.autoPronounce)
        assertEquals(false, read.haptics)
        assertEquals(false, read.updateCheck)
    }

    @Test fun `an update leaves the other settings alone`() = runTest {
        val repository = DataStoreSettingsRepository(store("partial"))
        repository.setDailyGoal(35)
        repository.update { it.copy(themeMode = ThemeMode.LIGHT) }
        assertEquals(35, repository.settings.first().dailyGoal)
        assertEquals(ThemeMode.LIGHT, repository.settings.first().themeMode)
    }

    @Test fun `a font removed in an update falls back to the default`() = runTest {
        val dataStore = store("old")
        dataStore.edit {
            it[stringPreferencesKey("wordFont")] = "LORA"
            it[stringPreferencesKey("uiFont")] = "INTER"
        }
        val read = DataStoreSettingsRepository(dataStore).settings.first()
        assertEquals(WordFont.LITERATA, read.wordFont)
        assertEquals(UiFont.SOURCE_SANS, read.uiFont)
    }

    @Test fun `an unknown font name, a wrong text size and weekdays out of range fall back`() = runTest {
        val dataStore = store("garbage")
        dataStore.edit { it[stringPreferencesKey("wordFont")] = "COMIC_SANS" }
        val repository = DataStoreSettingsRepository(dataStore)
        repository.update { it.copy(textScalePercent = 77, reminderDays = 0b11111111111, newWordsPerDay = 500) }
        val read = repository.settings.first()
        assertEquals(WordFont.LITERATA, read.wordFont)
        assertEquals(Settings.DEFAULT_TEXT_SCALE, read.textScalePercent)
        assertEquals(ReminderDays.ALL, read.reminderDays)
        assertEquals(100, read.newWordsPerDay)
    }
}
