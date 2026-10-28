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

/**
 * Lexa's own theme on top of Material 3: paper, ink, one yellow marker, a red pen.
 * No dynamic colour. Composables read [LexaTheme.colors] and [LexaTheme.type], never raw colours.
 */
@Composable
fun LexaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkLexaColors else LightLexaColors
    val scheme = remember(colors) { colors.toColorScheme() }
    val selection = remember(colors) {
        TextSelectionColors(handleColor = colors.ink, backgroundColor = colors.marker.copy(alpha = 0.6f))
    }
    CompositionLocalProvider(
        LocalLexaColors provides colors,
        LocalLexaTypography provides DefaultLexaTypography,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = DefaultLexaTypography.toMaterial(),
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
