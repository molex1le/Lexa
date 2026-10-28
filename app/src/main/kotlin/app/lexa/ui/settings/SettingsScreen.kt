package app.lexa.ui.settings

import androidx.annotation.StringRes
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
import app.lexa.domain.model.Settings
import app.lexa.ui.components.ScreenHeader
import app.lexa.ui.theme.LexaTheme
import app.lexa.ui.theme.Spacing

data class SettingsUiState(
    val direction: Direction = Direction.EN_RU,
    val reminderMinutes: Int = Settings.DEFAULT_REMINDER_MINUTES,
) {
    val reminderTime: String get() = "%d:%02d".format(reminderMinutes / 60, reminderMinutes % 60)
}

@Composable
fun SettingsRoute(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsScreen(state, onBack = onBack, onDirectionChange = viewModel::onDirectionChange)
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onDirectionChange: (Direction) -> Unit,
    modifier: Modifier = Modifier,
) {
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
            SectionTitle(R.string.settings_reminder_title)
            Text(stringResource(R.string.settings_reminder_value, state.reminderTime), style = type.body, color = colors.ink)
            Spacer(Modifier.height(Spacing.xs))
            Text(stringResource(R.string.settings_reminder_unavailable), style = type.meta, color = colors.pencil)
            Spacer(Modifier.height(Spacing.m))
        }
    }
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
private fun DirectionOption(@StringRes label: Int, selected: Boolean, onClick: () -> Unit) {
    val colors = LexaTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = colors.ink, unselectedColor = colors.pencil),
        )
        Spacer(Modifier.width(Spacing.m))
        Text(stringResource(label), style = LexaTheme.type.body, color = colors.ink)
    }
}
