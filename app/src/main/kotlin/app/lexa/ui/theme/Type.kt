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
import app.lexa.domain.model.UiFont
import app.lexa.domain.model.WordFont

/*
 * Two families, five sizes (docs/DESIGN_TOKENS.md «Шкала»):
 * word 56/60, figure 32/38, title 20/28, body 16/24, meta 13/18.
 * A serif carries the word and the figures, a sans carries the interface and the IPA; the learner picks
 * each from a short list in the settings. Every face is bundled in res/font (subset to Latin, Cyrillic and
 * the IPA signs), so it renders offline and in Robolectric.
 */

private fun variable(resId: Int, weight: Int, vararg extra: FontVariation.Setting) = Font(
    resId = resId,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight), *extra),
)

internal fun serif(font: WordFont, weight: Int, opticalSize: Float): FontFamily = FontFamily(
    when (font) {
        WordFont.LITERATA -> variable(R.font.literata, weight, FontVariation.Setting("opsz", opticalSize.coerceIn(7f, 72f)))
        WordFont.SOURCE_SERIF -> variable(R.font.source_serif_4, weight, FontVariation.Setting("opsz", opticalSize.coerceIn(8f, 60f)))
        // Lora has no optical-size axis, only weight (400..700).
        WordFont.LORA -> variable(R.font.lora, weight.coerceIn(400, 700))
    },
)

internal fun sans(font: UiFont): FontFamily = when (font) {
    UiFont.SOURCE_SANS -> FontFamily(listOf(400, 500, 600).map { variable(R.font.source_sans_3, it) })
    UiFont.INTER -> FontFamily(listOf(400, 500, 600).map { variable(R.font.inter, it) })
    UiFont.SYSTEM -> FontFamily.SansSerif
}

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

/** The five text styles for the chosen pair of faces. */
fun lexaTypography(wordFont: WordFont = WordFont.LITERATA, uiFont: UiFont = UiFont.SOURCE_SANS): LexaTypography {
    val display = serif(wordFont, 500, opticalSize = 56f)
    val figures = serif(wordFont, 500, opticalSize = 32f)
    val ui = sans(uiFont)
    return LexaTypography(
        word = TextStyle(
            fontFamily = display,
            fontWeight = FontWeight(500),
            fontSize = 56.sp,
            lineHeight = 60.sp,
            letterSpacing = (-0.02).em,
            lineHeightStyle = Trim,
        ),
        figure = TextStyle(
            fontFamily = figures,
            fontWeight = FontWeight(500),
            fontSize = 32.sp,
            lineHeight = 38.sp,
            fontFeatureSettings = "tnum",
            lineHeightStyle = Trim,
        ),
        heading = TextStyle(
            fontFamily = ui,
            fontWeight = FontWeight(600),
            fontSize = 32.sp,
            lineHeight = 38.sp,
            lineHeightStyle = Trim,
        ),
        title = TextStyle(
            fontFamily = ui,
            fontWeight = FontWeight(500),
            fontSize = 20.sp,
            lineHeight = 28.sp,
            lineHeightStyle = Trim,
        ),
        body = TextStyle(
            fontFamily = ui,
            fontWeight = FontWeight(400),
            fontSize = 16.sp,
            lineHeight = 24.sp,
            lineHeightStyle = Trim,
        ),
        meta = TextStyle(
            fontFamily = ui,
            fontWeight = FontWeight(400),
            fontSize = 13.sp,
            lineHeight = 18.sp,
            fontFeatureSettings = "tnum",
            lineHeightStyle = Trim,
        ),
    )
}

val DefaultLexaTypography = lexaTypography()

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
