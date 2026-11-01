package app.lexa.ui.theme

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lexa.domain.model.Settings
import app.lexa.domain.model.ThemeMode
import app.lexa.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking

/**
 * The settings every screen needs before it draws: theme, fonts, text size. The first value is read
 * synchronously (one small DataStore read), so the app never flashes the default look before the chosen one.
 */
@HiltViewModel
class AppearanceViewModel @Inject constructor(repository: SettingsRepository) : ViewModel() {
    val settings: StateFlow<Settings> = repository.settings.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        runBlocking { repository.settings.first() },
    )
}

/** [LexaTheme] with the learner's choices: light, dark or the phone's; the two fonts; the text size. */
@Composable
fun LexaAppTheme(settings: Settings, content: @Composable () -> Unit) {
    LexaTheme(
        darkTheme = settings.themeMode.isDark(isSystemInDarkTheme()),
        wordFont = settings.wordFont,
        uiFont = settings.uiFont,
        textScale = settings.textScalePercent / 100f,
        content = content,
    )
}

/** Transparent system bars whose icons match the chosen theme, not only the phone's. */
fun ComponentActivity.applySystemBars(mode: ThemeMode) {
    val transparent = android.graphics.Color.TRANSPARENT
    val style = when (mode) {
        ThemeMode.SYSTEM -> SystemBarStyle.auto(transparent, transparent)
        ThemeMode.LIGHT -> SystemBarStyle.light(transparent, transparent)
        ThemeMode.DARK -> SystemBarStyle.dark(transparent)
    }
    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
}
