package app.lexa.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import app.lexa.ui.theme.LexaCorner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lexa.R
import app.lexa.domain.model.Direction
import app.lexa.domain.model.VocabLevel
import app.lexa.ui.home.levelLabel
import app.lexa.domain.model.Settings
import app.lexa.ui.components.ScreenHeader
import app.lexa.ui.theme.LexaTheme
import app.lexa.ui.theme.Spacing

data class SettingsUiState(
    val direction: Direction = Direction.EN_RU,
    val vocabLevel: VocabLevel? = null,
    val reminderMinutes: Int = Settings.DEFAULT_REMINDER_MINUTES,
    val reminderEnabled: Boolean = false,
    /** Notifications were refused when the reminder was turned on. */
    val notificationsDenied: Boolean = false,
) {
    val reminderTime: String get() = "%d:%02d".format(reminderMinutes / 60, reminderMinutes % 60)
}

@Composable
fun SettingsRoute(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.onReminderEnabledChange(true) else viewModel.onNotificationsDenied()
    }
    SettingsScreen(
        state,
        onBack = onBack,
        onDirectionChange = viewModel::onDirectionChange,
        onLevelChange = viewModel::onLevelChange,
        onReminderToggle = { enabled ->
            val needsPermission = enabled && Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            if (needsPermission) askPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else viewModel.onReminderEnabledChange(enabled)
        },
        onReminderTimeChange = viewModel::onReminderTimeChange,
    )
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onDirectionChange: (Direction) -> Unit,
    modifier: Modifier = Modifier,
    onReminderToggle: (Boolean) -> Unit = {},
    onReminderTimeChange: (Int) -> Unit = {},
    onLevelChange: (VocabLevel) -> Unit = {},
) {
    var pickingTime by rememberSaveable { mutableStateOf(false) }
    val colors = LexaTheme.colors
    val type = LexaTheme.type
    Column(
        modifier
            .fillMaxSize()
            .background(colors.paper)
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(title = stringResource(R.string.settings_title), onBack = onBack)
        Column(Modifier.padding(horizontal = Spacing.gutter)) {
            Spacer(Modifier.height(Spacing.l))
            SectionTitle(R.string.settings_direction_title)
            Column(Modifier.selectableGroup()) {
                DirectionOption(R.string.settings_direction_en_ru, state.direction == Direction.EN_RU) {
                    onDirectionChange(Direction.EN_RU)
                }
                DirectionOption(R.string.settings_direction_ru_en, state.direction == Direction.RU_EN) {
                    onDirectionChange(Direction.RU_EN)
                }
            }
            Spacer(Modifier.height(Spacing.xl))
            SectionTitle(R.string.settings_level_title)
            Column(Modifier.selectableGroup()) {
                VocabLevel.entries.forEach { level ->
                    OptionRow(levelLabel(level), state.vocabLevel == level) { onLevelChange(level) }
                }
            }
            Spacer(Modifier.height(Spacing.xl))
            SectionTitle(R.string.settings_reminder_title)
            ReminderSwitch(state.reminderEnabled, onReminderToggle)
            when {
                state.reminderEnabled -> Row(
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
                state.notificationsDenied -> Text(
                    stringResource(R.string.settings_reminder_denied),
                    style = type.meta,
                    color = colors.ink,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
                else -> Text(
                    stringResource(R.string.settings_reminder_off),
                    style = type.meta,
                    color = colors.pencil,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
            Spacer(Modifier.height(Spacing.m))
        }
    }
    if (pickingTime) {
        ReminderTimeDialog(
            initialMinutes = state.reminderMinutes,
            onConfirm = { minutes ->
                pickingTime = false
                onReminderTimeChange(minutes)
            },
            onDismiss = { pickingTime = false },
        )
    }
}

@Composable
private fun ReminderSwitch(checked: Boolean, onToggle: (Boolean) -> Unit) {
    val colors = LexaTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onToggle),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.settings_reminder_switch),
            style = LexaTheme.type.body,
            color = colors.ink,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                // Ink, not marker: a switch is chrome, and the marker is kept for new words and the next action.
                checkedThumbColor = colors.paper,
                checkedTrackColor = colors.ink,
                checkedBorderColor = colors.ink,
                uncheckedThumbColor = colors.pencil,
                uncheckedTrackColor = colors.sheet,
                uncheckedBorderColor = colors.pencil,
            ),
        )
    }
}

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

@Composable
private fun SectionTitle(@StringRes text: Int) {
    Text(
        stringResource(text),
        style = LexaTheme.type.title,
        color = LexaTheme.colors.ink,
        modifier = Modifier.padding(bottom = Spacing.s).semantics { heading() },
    )
}

@Composable
private fun DirectionOption(@StringRes label: Int, selected: Boolean, onClick: () -> Unit) =
    OptionRow(stringResource(label), selected, onClick)

@Composable
private fun OptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LexaTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            // Keeps two-line labels (large system font) apart from the next option.
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = colors.ink, unselectedColor = colors.pencil),
        )
        Spacer(Modifier.width(Spacing.m))
        Text(label, style = LexaTheme.type.body, color = colors.ink)
    }
}
