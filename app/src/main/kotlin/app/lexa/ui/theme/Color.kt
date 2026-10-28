package app.lexa.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The six colour tokens of docs/DESIGN_TOKENS.md. The only place in the app where colours are written.
 * [onMarker] is the ink of the light theme: text on the marker stays dark in both themes.
 */
@Immutable
data class LexaColors(
    val paper: Color,
    val sheet: Color,
    val ink: Color,
    val pencil: Color,
    val marker: Color,
    val redpen: Color,
    val onMarker: Color,
    val isDark: Boolean,
)

private val InkOnMarker = Color(0xFF1C2030)

val LightLexaColors = LexaColors(
    paper = Color(0xFFF5F5F2),
    sheet = Color(0xFFEAEAE5),
    ink = Color(0xFF1C2030),
    pencil = Color(0xFF565B6B),
    marker = Color(0xFFFFDD3C),
    redpen = Color(0xFFB8261C),
    onMarker = InkOnMarker,
    isDark = false,
)

val DarkLexaColors = LexaColors(
    paper = Color(0xFF141413),
    sheet = Color(0xFF22221F),
    ink = Color(0xFFECEAE2),
    pencil = Color(0xFFA6A49B),
    marker = Color(0xFFF0CF34),
    redpen = Color(0xFFFF7A6B),
    onMarker = InkOnMarker,
    isDark = true,
)

val LocalLexaColors = staticCompositionLocalOf { LightLexaColors }

/**
 * Every Material slot is filled from the tokens, so no default (purple) colour can leak through
 * a Material component. Containers are one tone step (sheet); tonal elevation tint is off.
 */
internal fun LexaColors.toColorScheme(): ColorScheme {
    val scrim = ink.copy(alpha = 0.32f)
    val transparent = Color.Transparent
    return ColorScheme(
        primary = ink,
        onPrimary = paper,
        primaryContainer = sheet,
        onPrimaryContainer = ink,
        inversePrimary = paper,
        secondary = pencil,
        onSecondary = paper,
        secondaryContainer = sheet,
        onSecondaryContainer = ink,
        tertiary = ink,
        onTertiary = paper,
        tertiaryContainer = sheet,
        onTertiaryContainer = ink,
        background = paper,
        onBackground = ink,
        surface = paper,
        onSurface = ink,
        surfaceVariant = sheet,
        onSurfaceVariant = pencil,
        surfaceTint = transparent,
        inverseSurface = ink,
        inverseOnSurface = paper,
        error = redpen,
        onError = paper,
        errorContainer = sheet,
        onErrorContainer = redpen,
        outline = pencil,
        outlineVariant = sheet,
        scrim = scrim,
        surfaceBright = paper,
        surfaceContainer = sheet,
        surfaceContainerHigh = sheet,
        surfaceContainerHighest = sheet,
        surfaceContainerLow = sheet,
        surfaceContainerLowest = paper,
        surfaceDim = sheet,
        primaryFixed = sheet,
        primaryFixedDim = sheet,
        onPrimaryFixed = ink,
        onPrimaryFixedVariant = pencil,
        secondaryFixed = sheet,
        secondaryFixedDim = sheet,
        onSecondaryFixed = ink,
        onSecondaryFixedVariant = pencil,
        tertiaryFixed = sheet,
        tertiaryFixedDim = sheet,
        onTertiaryFixed = ink,
        onTertiaryFixedVariant = pencil,
    )
}
