package app.lexa.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import app.lexa.domain.training.DailyProgressRepository
import app.lexa.domain.training.DayCount
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Today's answer count in the settings DataStore (two keys: the day and its count). */
class DataStoreDailyProgress @Inject constructor(
    private val store: DataStore<Preferences>,
) : DailyProgressRepository {

    override val count: Flow<DayCount> = store.data.map { prefs ->
        DayCount(epochDay = prefs[DAY] ?: -1L, count = prefs[COUNT] ?: 0)
    }

    override suspend fun add(epochDay: Long) {
        store.edit { prefs ->
            val sameDay = prefs[DAY] == epochDay
            prefs[DAY] = epochDay
            prefs[COUNT] = if (sameDay) (prefs[COUNT] ?: 0) + 1 else 1
        }
    }

    private companion object {
        val DAY = longPreferencesKey("progressDay")
        val COUNT = intPreferencesKey("progressCount")
    }
}
