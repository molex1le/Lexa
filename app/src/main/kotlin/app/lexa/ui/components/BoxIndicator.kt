package app.lexa.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.lexa.R
import app.lexa.domain.leitner.LeitnerRules
import app.lexa.ui.theme.LexaTheme

private val SlotWidth = 6.dp
private val SlotHeight = 20.dp
private val SlotGap = 6.dp

/**
 * Five card-index compartments under the word (docs/DESIGN_TOKENS.md «Коробки Лейтнера»):
 * boxes already passed are ink, the current box is ink with a marker band, the rest are sheet.
 * [markerProgress] 0..1 grows the marker band up from the bottom, to show the word landing in its box.
 */
@Composable
fun BoxIndicator(box: Int, modifier: Modifier = Modifier, markerProgress: Float = 1f) {
    val colors = LexaTheme.colors
    val count = LeitnerRules.LAST_BOX
    val description = stringResource(R.string.word_boxes_description, box)
    Canvas(
        modifier = modifier
            .size(width = SlotWidth * count + SlotGap * (count - 1), height = SlotHeight)
            .semantics { contentDescription = description },
    ) {
        val w = SlotWidth.toPx()
        val step = w + SlotGap.toPx()
        for (slot in LeitnerRules.boxes) {
            val x = (slot - 1) * step
            when {
                slot < box -> drawRect(colors.ink, Offset(x, 0f), Size(w, size.height))
                slot == box -> {
                    drawRect(colors.ink, Offset(x, 0f), Size(w, size.height))
                    val band = size.height * 0.4f * markerProgress.coerceIn(0f, 1.2f)
                    drawRect(colors.marker, Offset(x, size.height - band), Size(w, band))
                }
                else -> drawRect(colors.sheet, Offset(x, 0f), Size(w, size.height))
            }
        }
    }
}
