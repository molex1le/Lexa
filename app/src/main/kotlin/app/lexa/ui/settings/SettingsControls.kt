package app.lexa.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.lexa.ui.theme.LexaTheme
import app.lexa.ui.theme.Spacing

private val Corner = RoundedCornerShape(6.dp)

/** A group of settings: «Внешний вид», «Тренировка»… */
@Composable
internal fun GroupTitle(text: String) {
    Text(
        text,
        style = LexaTheme.type.title,
        color = LexaTheme.colors.ink,
        modifier = Modifier.padding(top = Spacing.xl, bottom = Spacing.s).semantics { heading() },
    )
}

/** The name of one setting, above its control. */
@Composable
internal fun SettingLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = LexaTheme.type.body.copy(fontWeight = FontWeight(600)),
        color = LexaTheme.colors.ink,
        modifier = modifier.padding(top = Spacing.m, bottom = Spacing.s),
    )
}

@Composable
internal fun SettingHint(text: String, modifier: Modifier = Modifier) {
    Text(text, style = LexaTheme.type.meta, color = LexaTheme.colors.pencil, modifier = modifier.padding(top = Spacing.xs))
}

/**
 * A few choices side by side, one picked (or none, [selected] null): the picked one is ink with a paper label, the rest sit on a tone
 * step. [textStyle] lets a cell show its own font (the font pickers). Cells are at least 48dp tall.
 */
@Composable
internal fun <T> Segmented(
    options: List<T>,
    selected: T?,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    textStyle: @Composable (T) -> TextStyle = { LexaTheme.type.body },
) {
    val colors = LexaTheme.colors
    Row(modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(Corner)
                    .background(if (isSelected) colors.ink else colors.sheet)
                    .selectable(selected = isSelected, role = Role.RadioButton) { onSelect(option) }
                    .padding(horizontal = Spacing.xs, vertical = Spacing.s),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(option),
                    style = textStyle(option).copy(fontWeight = if (isSelected) FontWeight(600) else textStyle(option).fontWeight),
                    color = if (isSelected) colors.paper else colors.ink,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** The presets as [Segmented], and below them a field for any number in [range] when none of them fits. */
@Composable
internal fun NumberSetting(
    presets: List<Int>,
    value: Int,
    range: IntRange,
    customLabel: String,
    format: @Composable (Int) -> String = { it.toString() },
    onChange: (Int) -> Unit,
) {
    val colors = LexaTheme.colors
    val type = LexaTheme.type
    Segmented(presets, value.takeIf { it in presets }, { format(it) }, onChange)
    var text by rememberSaveable(value) { mutableStateOf(if (value in presets) "" else value.toString()) }
    fun apply() {
        text.toIntOrNull()?.let { onChange(it.coerceIn(range)) }
    }
    Spacer(Modifier.height(Spacing.s))
    BasicTextField(
        value = text,
        onValueChange = { input -> text = input.filter(Char::isDigit).take(3) },
        singleLine = true,
        textStyle = type.body.copy(color = colors.ink),
        cursorBrush = SolidColor(colors.ink),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { apply() }),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { if (!it.isFocused) apply() }
            .semantics { contentDescription = customLabel },
        decorationBox = { field ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .clip(Corner)
                    .background(colors.sheet)
                    .padding(horizontal = Spacing.m),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (text.isEmpty()) Text(customLabel, style = type.body, color = colors.pencil)
                field()
            }
        },
    )
}

/** A switch row: ink when on, never the marker (that is for new words and the next action). */
@Composable
internal fun SwitchRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    val colors = LexaTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onToggle),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = LexaTheme.type.body, color = colors.ink, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
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

/** A secondary action as underlined ink text with a 48dp target, like those on the training card. */
@Composable
internal fun TextAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(label, style = LexaTheme.type.body.copy(textDecoration = TextDecoration.Underline), color = LexaTheme.colors.ink)
    }
}

/** Seven weekday toggles; several can be on. [days] and the callback use the bit mask of `ReminderDays`. */
@Composable
internal fun WeekdayRow(labels: List<String>, mask: Int, onToggle: (Int) -> Unit) {
    val colors = LexaTheme.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        labels.forEachIndexed { index, label ->
            val on = mask and (1 shl index) != 0
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(Corner)
                    .background(if (on) colors.ink else colors.sheet)
                    .toggleable(value = on, role = Role.Checkbox) { onToggle(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = LexaTheme.type.body.copy(fontWeight = if (on) FontWeight(600) else FontWeight(400)),
                    color = if (on) colors.paper else colors.ink,
                )
            }
        }
    }
}

/**
 * A list of faces, each row set in its own face with a short name of its character: the picked one is ink
 * with a paper label, the rest sit on a tone step. The difference between the faces is seen, not read.
 */
@Composable
internal fun <T> FontList(
    options: List<T>,
    selected: T,
    name: @Composable (T) -> String,
    sample: String,
    family: (T) -> FontFamily,
    sampleSize: TextUnit,
    onSelect: (T) -> Unit,
) {
    val colors = LexaTheme.colors
    Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        options.forEach { option ->
            val isSelected = option == selected
            val ink = if (isSelected) colors.paper else colors.ink
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .clip(Corner)
                    .background(if (isSelected) colors.ink else colors.sheet)
                    .selectable(selected = isSelected, role = Role.RadioButton) { onSelect(option) }
                    .padding(horizontal = Spacing.m, vertical = Spacing.s),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    sample,
                    style = TextStyle(fontFamily = family(option), fontSize = sampleSize, fontWeight = FontWeight(500)),
                    color = ink,
                    maxLines = 1,
                )
                Text(name(option), style = LexaTheme.type.meta, color = if (isSelected) colors.paper else colors.pencil)
            }
        }
    }
}
