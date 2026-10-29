package app.lexa.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lexa.domain.model.Direction
import app.lexa.domain.model.VocabLevel
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
    private val scheduler: ReminderScheduler,
) : ViewModel() {

    private val permissionDenied = MutableStateFlow(false)

    val state: StateFlow<SettingsUiState> = combine(settings.settings, permissionDenied) { s, denied ->
        SettingsUiState(
            direction = s.direction,
            vocabLevel = s.vocabLevel,
            sessionSize = s.sessionSize,
            reminderMinutes = s.reminderMinutes,
            reminderEnabled = s.reminderEnabled,
            notificationsDenied = denied && !s.reminderEnabled,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun onSessionSizeChange(size: Int) {
        viewModelScope.launch { settings.setSessionSize(size) }
    }

    fun onLevelChange(level: VocabLevel) {
        viewModelScope.launch { settings.setVocabLevel(level) }
    }

    fun onDirectionChange(direction: Direction) {
        viewModelScope.launch { settings.setDirection(direction) }
    }

    /** Called with notifications already allowed (or not needed below API 33) – or with false to turn off. */
    fun onReminderEnabledChange(enabled: Boolean) {
        permissionDenied.value = false
        viewModelScope.launch {
            settings.setReminderEnabled(enabled)
            if (enabled) scheduler.schedule(settings.settings.first().reminderMinutes) else scheduler.cancel()
        }
    }

    /** The user said no to notifications: the switch stays off and the screen says where to allow them. */
    fun onNotificationsDenied() {
        permissionDenied.value = true
    }

    fun onReminderTimeChange(minutes: Int) {
        viewModelScope.launch {
            settings.setReminderMinutes(minutes)
            if (settings.settings.first().reminderEnabled) scheduler.schedule(minutes)
        }
    }
}
