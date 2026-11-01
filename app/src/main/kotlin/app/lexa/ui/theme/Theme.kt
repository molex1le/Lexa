package app.lexa.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import app.lexa.domain.model.UiFont
import app.lexa.domain.model.WordFont

/**
 * Lexa's own theme on top of Material 3: paper, ink, one yellow marker, a red pen.
 * No dynamic colour. Composables read [LexaTheme.colors] and [LexaTheme.type], never raw colours.
 */
@Composable
fun LexaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    wordFont: WordFont = WordFont.LITERATA,
    uiFont: UiFont = UiFont.SOURCE_SANS,
    /** Text size on top of the phone's own: 0.9, 1.0, 1.15 or 1.3. */
    textScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkLexaColors else LightLexaColors
    val scheme = remember(colors) { colors.toColorScheme() }
    val selection = remember(colors) {
        TextSelectionColors(handleColor = colors.ink, backgroundColor = colors.marker.copy(alpha = 0.6f))
    }
    val typography = remember(wordFont, uiFont) { lexaTypography(wordFont, uiFont) }
    val density = LocalDensity.current
    val scaled = remember(density, textScale) { Density(density.density, density.fontScale * textScale) }
    CompositionLocalProvider(
        LocalLexaColors provides colors,
        LocalLexaTypography provides typography,
        LocalDensity provides scaled,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = typography.toMaterial(),
            shapes = LexaShapes,
        ) {
            CompositionLocalProvider(
                LocalContentColor provides colors.ink,
                LocalTextSelectionColors provides selection,
                content = content,
            )
        }
    }
}

object LexaTheme {
    val colors: LexaColors
        @Composable @ReadOnlyComposable get() = LocalLexaColors.current

    val type: LexaTypography
        @Composable @ReadOnlyComposable get() = LocalLexaTypography.current
}
