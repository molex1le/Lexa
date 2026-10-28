package app.lexa.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.lexa.R

/*
 * Two families, five sizes (docs/DESIGN_TOKENS.md «Шкала»):
 * word 56/60, figure 32/38, title 20/28, body 16/24, meta 13/18.
 * Literata carries the word and the figures; Source Sans 3 carries the interface and the IPA.
 * Both are variable fonts bundled in res/font, so they render offline and in Robolectric.
 */

private fun literata(weight: Int, opticalSize: Float) = Font(
    resId = R.font.literata,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight),
        FontVariation.Setting("opsz", opticalSize),
    ),
)

private fun sourceSans(weight: Int) = Font(
    resId = R.font.source_sans_3,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Literata cut for display size (the word on the card). */
val LiterataDisplay = FontFamily(literata(500, opticalSize = 56f))

/** Literata cut for figures (statistics numbers). */
val LiterataFigure = FontFamily(literata(500, opticalSize = 32f))

val SourceSans = FontFamily(sourceSans(400), sourceSans(500), sourceSans(600))

private val Trim = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

@Immutable
data class LexaTypography(
    /** The word on a card or in the translator. 56sp, Literata 500, tracking −0.02em. */
    val word: TextStyle,
    /** Statistics figures. 32sp Literata 500, tabular. */
    val figure: TextStyle,
    /** Screen heading. Same 32sp size, Source Sans 600. */
    val heading: TextStyle,
    /** Translation, block heading, answer input. 20sp Source Sans 500. */
    val title: TextStyle,
    /** Text, examples, list rows. 16sp Source Sans 400. */
    val body: TextStyle,
    /** Captions, IPA, chart axes. 13sp Source Sans 400. */
    val meta: TextStyle,
)

val DefaultLexaTypography = LexaTypography(
    word = TextStyle(
        fontFamily = LiterataDisplay,
        fontWeight = FontWeight(500),
        fontSize = 56.sp,
        lineHeight = 60.sp,
        letterSpacing = (-0.02).em,
        lineHeightStyle = Trim,
    ),
    figure = TextStyle(
        fontFamily = LiterataFigure,
        fontWeight = FontWeight(500),
        fontSize = 32.sp,
        lineHeight = 38.sp,
        fontFeatureSettings = "tnum",
        lineHeightStyle = Trim,
    ),
    heading = TextStyle(
        fontFamily = SourceSans,
        fontWeight = FontWeight(600),
        fontSize = 32.sp,
        lineHeight = 38.sp,
        lineHeightStyle = Trim,
    ),
    title = TextStyle(
        fontFamily = SourceSans,
        fontWeight = FontWeight(500),
        fontSize = 20.sp,
        lineHeight = 28.sp,
        lineHeightStyle = Trim,
    ),
    body = TextStyle(
        fontFamily = SourceSans,
        fontWeight = FontWeight(400),
        fontSize = 16.sp,
        lineHeight = 24.sp,
        lineHeightStyle = Trim,
    ),
    meta = TextStyle(
        fontFamily = SourceSans,
        fontWeight = FontWeight(400),
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontFeatureSettings = "tnum",
        lineHeightStyle = Trim,
    ),
)

val LocalLexaTypography = staticCompositionLocalOf { DefaultLexaTypography }

/** Material slots mapped onto the same five sizes, so Material components never bring their own. */
internal fun LexaTypography.toMaterial(): Typography = Typography(
    displayLarge = word,
    displayMedium = word,
    displaySmall = figure,
    headlineLarge = heading,
    headlineMedium = heading,
    headlineSmall = title,
    titleLarge = title,
    titleMedium = title,
    titleSmall = body,
    bodyLarge = body,
    bodyMedium = body,
    bodySmall = meta,
    labelLarge = body.copy(fontWeight = FontWeight(600)),
    labelMedium = meta,
    labelSmall = meta,
)
