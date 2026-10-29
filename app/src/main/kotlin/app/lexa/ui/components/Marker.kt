package app.lexa.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.lexa.ui.theme.LexaTheme
import app.lexa.ui.theme.Spacing

/** How far the band runs past the text on each side. */
private val MarkerOverhang = 4.dp

/** Horizontal run of the chisel cut at each end of the band. */
private val MarkerSlant = 6.dp

/**
 * Draws the marker band of the given [size] at the origin: a strip with both ends cut at the same
 * angle, like the stroke of a chisel-tip highlighter. Top-left and bottom-right corners are sharp.
 */
private fun DrawScope.drawMarkerBand(
    left: Float,
    top: Float,
    size: Size,
    slant: Float,
    color: Color,
) {
    val right = left + size.width
    val bottom = top + size.height
    val path = Path().apply {
        moveTo(left + slant, top)
        lineTo(right, top)
        lineTo(right - slant, bottom)
        lineTo(left, bottom)
        close()
    }
    drawPath(path, color)
}

/**
 * The marker stroke behind a line of text (docs/DESIGN_TOKENS.md «Фирменный элемент»). By default it
 * covers the lower part of the letters; [fullHeight] covers the whole line, for the dark theme, where
 * the text on it must switch to dark ink to stay readable. [progress] 0..1 reveals the band from left
 * to right; the animation itself belongs to the caller (phase 6).
 */
fun Modifier.markerHighlight(progress: Float, color: Color, fullHeight: Boolean = false): Modifier = drawBehind {
    if (progress <= 0f) return@drawBehind
    val overhang = MarkerOverhang.toPx()
    val top = size.height * if (fullHeight) 0.1f else 0.55f
    val bottom = size.height * if (fullHeight) 0.98f else 0.9f
    val band = Size(size.width + overhang * 2, bottom - top)
    val left = -overhang
    clipRect(left = left, top = top, right = left + band.width * progress.coerceIn(0f, 1f), bottom = bottom) {
        drawMarkerBand(left, top, band, MarkerSlant.toPx(), color)
    }
}

/**
 * [markerHighlight] in the theme's marker colour. Light ink reads on the lower band; on the dark
 * theme the band covers the whole line and the text must use [markedTextColor].
 */
@Composable
fun Modifier.markerHighlight(progress: Float = 1f): Modifier =
    markerHighlight(progress, LexaTheme.colors.marker, fullHeight = LexaTheme.colors.isDark)

/** Text colour for a word under [markerHighlight]: dark ink on the marker in both themes. */
@Composable
fun markedTextColor(): Color = with(LexaTheme.colors) { if (isDark) onMarker else ink }

/**
 * The one primary action of a screen: a marker stroke across the whole button with slanted ends,
 * labelled with a verb in dark ink (same ink in both themes). 52dp tall. Disabled, it keeps its place
 * but shows only the label in pencil.
 */
@Composable
fun MarkerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 52.dp,
) {
    val colors = LexaTheme.colors
    // Disabled is just the pencil label: a grey slab would be a second heavy shape next to the one marker.
    val fill = if (enabled) colors.marker else Color.Transparent
    val label = if (enabled) colors.onMarker else colors.pencil
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = height)
            .drawBehind {
                drawMarkerBand(0f, 0f, size, MarkerSlant.toPx(), fill)
            }
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = colors.onMarker),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = Spacing.l + MarkerSlant),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = LexaTheme.type.body.copy(fontWeight = FontWeight(600)),
            color = label,
        )
    }
}
