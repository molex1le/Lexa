package app.lexa.domain.repository

import app.lexa.domain.model.Direction
import app.lexa.domain.model.Settings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<Settings>
    suspend fun setDirection(direction: Direction)
    suspend fun setReminderMinutes(minutes: Int)
}
