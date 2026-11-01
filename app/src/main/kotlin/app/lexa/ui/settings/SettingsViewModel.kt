package app.lexa.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lexa.domain.model.Settings
import app.lexa.domain.repository.HistoryRepository
import app.lexa.domain.repository.SettingsRepository
import app.lexa.reminder.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val history: HistoryRepository,
    private val scheduler: ReminderScheduler,
) : ViewModel() {

    private val permissionDenied = MutableStateFlow(false)

    val state: StateFlow<SettingsUiState> = combine(settings.settings, permissionDenied) { s, denied ->
        SettingsUiState(settings = s, notificationsDenied = denied && !s.reminderEnabled)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    /** Any setting that needs nothing but to be stored: theme, fonts, text size, level, goals, switches. */
    fun change(transform: (Settings) -> Settings) {
        viewModelScope.launch { settings.update(transform) }
    }

    /** Called with notifications already allowed (or not needed below API 33) – or with false to turn off. */
    fun onReminderEnabledChange(enabled: Boolean) {
        permissionDenied.value = false
        viewModelScope.launch {
            settings.setReminderEnabled(enabled)
            val now = settings.settings.first()
            if (enabled) scheduler.schedule(now.reminderMinutes, now.reminderDays) else scheduler.cancel()
        }
    }

    /** The user said no to notifications: the switch stays off and the screen says where to allow them. */
    fun onNotificationsDenied() {
        permissionDenied.value = true
    }

    fun onReminderTimeChange(minutes: Int) {
        viewModelScope.launch {
            settings.setReminderMinutes(minutes)
            val now = settings.settings.first()
            if (now.reminderEnabled) scheduler.schedule(minutes, now.reminderDays)
        }
    }

    /** At least one weekday stays on: the last one cannot be switched off. */
    fun onReminderDaysChange(mask: Int) {
        if (mask and app.lexa.domain.model.ReminderDays.ALL == 0) return
        viewModelScope.launch {
            settings.update { it.copy(reminderDays = mask) }
            val now = settings.settings.first()
            if (now.reminderEnabled) scheduler.schedule(now.reminderMinutes, mask)
        }
    }

    fun onClearHistory() {
        viewModelScope.launch { history.clear() }
    }
}
