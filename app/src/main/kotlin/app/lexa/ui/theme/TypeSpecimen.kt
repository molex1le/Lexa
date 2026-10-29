package app.lexa.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.lexa.ui.components.BoxIndicator
import app.lexa.ui.components.MarkerButton
import app.lexa.ui.components.markedTextColor
import app.lexa.ui.components.markerHighlight

/**
 * Type and token specimen for screenshot tests: proves Literata and Source Sans 3 render (with IPA
 * glyphs and Cyrillic) instead of a Roboto fallback. Not reachable from the app.
 */
@Composable
fun TypeSpecimen(modifier: Modifier = Modifier) {
    val colors = LexaTheme.colors
    val type = LexaTheme.type
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.paper)
            .padding(Spacing.gutter),
    ) {
        Spacer(Modifier.height(Spacing.l))
        Text("thoughtful", style = type.word, color = markedTextColor(), modifier = Modifier.markerHighlight())
        Spacer(Modifier.height(Spacing.s))
        Text("/ˈθɔːtfəl ˌʌndəˈstændɪŋ/", style = type.meta, color = colors.pencil)
        Spacer(Modifier.height(Spacing.s))
        Text("вдумчивый", style = type.title, color = colors.ink)
        Spacer(Modifier.height(Spacing.m))
        BoxIndicator(box = 3)
        Spacer(Modifier.height(Spacing.l))
        Text("128", style = type.figure, color = colors.ink)
        Text("выучено", style = type.meta, color = colors.pencil)
        Spacer(Modifier.height(Spacing.m))
        Text("Повторить сегодня", style = type.heading, color = colors.ink)
        Text("Пример: She gave a thoughtful answer – Она дала вдумчивый ответ.", style = type.body, color = colors.ink)
        Text("Ошибка: задумчиво", style = type.body, color = colors.redpen)
        Spacer(Modifier.height(Spacing.l))
        MarkerButton(text = "Проверить", onClick = {}, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(Spacing.s))
        MarkerButton(text = "Перевести", onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth())
    }
}
