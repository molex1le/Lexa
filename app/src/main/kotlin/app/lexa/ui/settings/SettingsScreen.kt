package app.lexa.ui.settings

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lexa.R
import app.lexa.domain.model.Direction
import app.lexa.domain.model.ReminderDays
import app.lexa.domain.model.Settings
import app.lexa.domain.model.ThemeMode
import app.lexa.domain.model.UiFont
import app.lexa.domain.model.VocabLevel
import app.lexa.domain.model.WordFont
import app.lexa.ui.components.ScreenHeader
import app.lexa.ui.home.levelLabel
import app.lexa.ui.theme.LexaCorner
import app.lexa.ui.theme.LexaTheme
import app.lexa.ui.theme.Spacing
import app.lexa.ui.theme.sans
import app.lexa.ui.theme.serif
import app.lexa.update.UpdateViewModel

data class SettingsUiState(
    val settings: Settings = Settings(),
    /** Notifications were refused when the reminder was turned on. */
    val notificationsDenied: Boolean = false,
    val appVersion: String = "",
) {
    val reminderTime: String get() = "%d:%02d".format(settings.reminderMinutes / 60, settings.reminderMinutes % 60)
}

/** Where a manual «Проверить обновления» stands. A newer version opens the update dialog by itself. */
enum class UpdateStatus { IDLE, CHECKING, UP_TO_DATE }

/** What the screen can ask for; every default does nothing, so a screenshot test builds the screen alone. */
class SettingsActions(
    val onChange: ((Settings) -> Settings) -> Unit = {},
    val onReminderToggle: (Boolean) -> Unit = {},
    val onReminderTimeChange: (Int) -> Unit = {},
    val onReminderDaysChange: (Int) -> Unit = {},
    val onOpenSystemNotifications: () -> Unit = {},
    val onClearHistory: () -> Unit = {},
    val onCheckUpdates: () -> Unit = {},
    val onOpenGitHub: () -> Unit = {},
)

private tailrec fun Context.activity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

@Composable
fun SettingsRoute(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // The same update view model as the dialog in MainActivity, so a check from here opens that dialog.
    val updates: UpdateViewModel? = context.activity()?.let { hiltViewModel(viewModelStoreOwner = it) }
    val updateState = updates?.state?.collectAsStateWithLifecycle()?.value
    val status = when {
        updateState?.checking == true -> UpdateStatus.CHECKING
        updateState?.upToDate == true -> UpdateStatus.UP_TO_DATE
        else -> UpdateStatus.IDLE
    }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.onReminderEnabledChange(true) else viewModel.onNotificationsDenied()
    }
    val version = updates?.installedVersion.orEmpty()
    SettingsScreen(
        state.copy(appVersion = version),
        onBack = onBack,
        updateStatus = status,
        actions = SettingsActions(
            onChange = viewModel::change,
            onReminderToggle = { enabled ->
                val needsPermission = enabled && Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                if (needsPermission) askPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else viewModel.onReminderEnabledChange(enabled)
            },
            onReminderTimeChange = viewModel::onReminderTimeChange,
            onReminderDaysChange = viewModel::onReminderDaysChange,
            onOpenSystemNotifications = {
                context.startActivity(
                    Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            },
            onClearHistory = viewModel::onClearHistory,
            onCheckUpdates = { updates?.checkNow() },
            onOpenGitHub = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            },
        ),
    )
}

private const val GITHUB_URL = "https://github.com/molex1le/Lexa"

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    updateStatus: UpdateStatus = UpdateStatus.IDLE,
    actions: SettingsActions = SettingsActions(),
) {
    var pickingTime by rememberSaveable { mutableStateOf(false) }
    var confirmingClear by rememberSaveable { mutableStateOf(false) }
    val s = state.settings
    val colors = LexaTheme.colors
    val type = LexaTheme.type
    val change = actions.onChange
    Column(
        modifier
            .fillMaxSize()
            .background(colors.paper)
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(title = stringResource(R.string.settings_title), onBack = onBack)
        Column(Modifier.padding(horizontal = Spacing.gutter)) {
            // ---- Внешний вид
            GroupTitle(stringResource(R.string.settings_group_appearance))
            SettingLabel(stringResource(R.string.settings_theme_title))
            Segmented(
                ThemeMode.entries, s.themeMode,
                label = { stringResource(when (it) {
                    ThemeMode.SYSTEM -> R.string.theme_system
                    ThemeMode.LIGHT -> R.string.theme_light
                    ThemeMode.DARK -> R.string.theme_dark
                }) },
                onSelect = { mode -> change { it.copy(themeMode = mode) } },
            )
            SettingLabel(stringResource(R.string.settings_word_font_title))
            Segmented(
                WordFont.entries, s.wordFont,
                label = { when (it) {
                    WordFont.LITERATA -> "Literata"
                    WordFont.LORA -> "Lora"
                    WordFont.SOURCE_SERIF -> "Source Serif"
                } },
                onSelect = { font -> change { it.copy(wordFont = font) } },
                textStyle = { LexaTheme.type.body.copy(fontFamily = serif(it, 500, 20f)) },
            )
            SettingLabel(stringResource(R.string.settings_ui_font_title))
            Segmented(
                UiFont.entries, s.uiFont,
                label = { when (it) {
                    UiFont.SOURCE_SANS -> "Source Sans"
                    UiFont.INTER -> "Inter"
                    UiFont.SYSTEM -> stringResource(R.string.font_system)
                } },
                onSelect = { font -> change { it.copy(uiFont = font) } },
                textStyle = { LexaTheme.type.body.copy(fontFamily = sans(it)) },
            )
            Spacer(Modifier.height(Spacing.m))
            FontPreview(
                word = stringResource(R.string.font_preview_word),
                ipa = stringResource(R.string.font_preview_ipa),
                translation = stringResource(R.string.font_preview_translation),
            )
            SettingLabel(stringResource(R.string.settings_text_size_title))
            Segmented(
                Settings.TEXT_SCALES, s.textScalePercent,
                label = { stringResource(R.string.text_size_percent, it) },
                onSelect = { size -> change { it.copy(textScalePercent = size) } },
            )
            SettingHint(stringResource(R.string.settings_text_size_hint))

            // ---- Тренировка
            GroupTitle(stringResource(R.string.settings_group_training))
            SettingLabel(stringResource(R.string.settings_direction_title))
            Segmented(
                listOf(Direction.EN_RU, Direction.RU_EN), s.direction,
                label = { stringResource(if (it == Direction.EN_RU) R.string.direction_en_ru_short else R.string.direction_ru_en_short) },
                onSelect = { direction -> change { it.copy(direction = direction) } },
            )
            SettingHint(
                stringResource(if (s.direction == Direction.RU_EN) R.string.settings_direction_ru_en else R.string.settings_direction_en_ru),
            )
            SettingLabel(stringResource(R.string.settings_level_title))
            Segmented(
                VocabLevel.entries, s.vocabLevel,
                label = { it.name },
                onSelect = { level -> change { it.copy(vocabLevel = level) } },
            )
            s.vocabLevel?.let { SettingHint(levelLabel(it)) }
            SettingLabel(stringResource(R.string.settings_session_title))
            NumberSetting(PRESET_SIZES, s.sessionSize, Settings.SESSION_SIZES, stringResource(R.string.settings_session_custom)) { size ->
                change { it.copy(sessionSize = size) }
            }
            SettingLabel(stringResource(R.string.settings_goal_title))
            NumberSetting(PRESET_GOALS, s.dailyGoal, Settings.DAILY_GOALS, stringResource(R.string.settings_goal_custom)) { goal ->
                change { it.copy(dailyGoal = goal) }
            }
            SettingLabel(stringResource(R.string.settings_new_title))
            NumberSetting(
                PRESET_NEW, s.newWordsPerDay, Settings.NEW_WORDS_PER_DAY, stringResource(R.string.settings_new_custom),
                format = { if (it == 0) stringResource(R.string.new_words_none) else it.toString() },
                onChange = { count -> change { it.copy(newWordsPerDay = count) } },
            )
            SettingHint(stringResource(R.string.settings_new_hint))
            Spacer(Modifier.height(Spacing.s))
            SwitchRow(stringResource(R.string.settings_auto_sound), s.autoPronounce) { on -> change { it.copy(autoPronounce = on) } }
            SwitchRow(stringResource(R.string.settings_haptics), s.haptics) { on -> change { it.copy(haptics = on) } }

            // ---- Напоминания и уведомления
            GroupTitle(stringResource(R.string.settings_group_notifications))
            SwitchRow(stringResource(R.string.settings_reminder_switch), s.reminderEnabled, actions.onReminderToggle)
            when {
                s.reminderEnabled -> {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clickable(onClickLabel = stringResource(R.string.settings_reminder_change_time)) { pickingTime = true },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.settings_reminder_value, state.reminderTime),
                            style = type.body,
                            color = colors.ink,
                            modifier = Modifier.weight(1f),
                        )
                        Text(stringResource(R.string.settings_reminder_change_time), style = type.meta, color = colors.pencil)
                    }
                    SettingLabel(stringResource(R.string.settings_days_title))
                    WeekdayRow(stringArrayResource(R.array.weekdays_short).toList(), s.reminderDays) { index ->
                        actions.onReminderDaysChange(s.reminderDays xor (1 shl index))
                    }
                    SettingHint(stringResource(R.string.settings_days_hint))
                }
                state.notificationsDenied -> SettingHint(stringResource(R.string.settings_reminder_denied), Modifier.padding(top = Spacing.xs))
                else -> SettingHint(stringResource(R.string.settings_reminder_off))
            }
            TextAction(stringResource(R.string.settings_system_notifications), actions.onOpenSystemNotifications)
            SwitchRow(stringResource(R.string.settings_update_check), s.updateCheck) { on -> change { it.copy(updateCheck = on) } }

            // ---- Данные
            GroupTitle(stringResource(R.string.settings_group_data))
            TextAction(stringResource(R.string.settings_clear_history), { confirmingClear = true })

            // ---- О приложении
            GroupTitle(stringResource(R.string.settings_group_about))
            Text(stringResource(R.string.settings_version, state.appVersion), style = type.body, color = colors.ink)
            TextAction(stringResource(R.string.settings_check_updates), actions.onCheckUpdates)
            when (updateStatus) {
                UpdateStatus.CHECKING -> SettingHint(stringResource(R.string.update_status_checking))
                UpdateStatus.UP_TO_DATE -> SettingHint(stringResource(R.string.update_status_latest))
                UpdateStatus.IDLE -> Unit
            }
            TextAction(stringResource(R.string.settings_github), actions.onOpenGitHub)
            Spacer(Modifier.height(Spacing.xl))
        }
    }
    if (pickingTime) {
        ReminderTimeDialog(
            initialMinutes = s.reminderMinutes,
            onConfirm = { minutes ->
                pickingTime = false
                actions.onReminderTimeChange(minutes)
            },
            onDismiss = { pickingTime = false },
        )
    }
    if (confirmingClear) {
        AlertDialog(
            onDismissRequest = { confirmingClear = false },
            containerColor = colors.paper,
            shape = LexaCorner,
            title = { Text(stringResource(R.string.clear_history_title), style = type.title, color = colors.ink) },
            text = { Text(stringResource(R.string.clear_history_text), style = type.body, color = colors.ink) },
            confirmButton = {
                TextButton(onClick = { confirmingClear = false; actions.onClearHistory() }) {
                    Text(stringResource(R.string.settings_clear_confirm), style = type.body, color = colors.ink)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingClear = false }) {
                    Text(stringResource(R.string.action_cancel), style = type.body, color = colors.pencil)
                }
            },
        )
    }
}

private val PRESET_SIZES = listOf(10, 20, 30)
private val PRESET_GOALS = listOf(10, 20, 30, 50)
private val PRESET_NEW = listOf(0, 5, 10, 20)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimeDialog(initialMinutes: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    val colors = LexaTheme.colors
    val picker = rememberTimePickerState(initialHour = initialMinutes / 60, initialMinute = initialMinutes % 60, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.paper,
        shape = LexaCorner,
        title = { Text(stringResource(R.string.settings_time_title), style = LexaTheme.type.title, color = colors.ink) },
        text = {
            TimePicker(
                state = picker,
                colors = TimePickerDefaults.colors(
                    clockDialColor = colors.sheet,
                    selectorColor = colors.marker,
                    clockDialSelectedContentColor = colors.onMarker,
                    clockDialUnselectedContentColor = colors.ink,
                    timeSelectorSelectedContainerColor = colors.marker,
                    timeSelectorSelectedContentColor = colors.onMarker,
                    timeSelectorUnselectedContainerColor = colors.sheet,
                    timeSelectorUnselectedContentColor = colors.ink,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(picker.hour * 60 + picker.minute) }) {
                Text(stringResource(R.string.settings_time_save), style = LexaTheme.type.body, color = colors.ink)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), style = LexaTheme.type.body, color = colors.pencil)
            }
        },
    )
}

