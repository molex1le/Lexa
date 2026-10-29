package app.lexa.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * One word at the word size, shrunk in 2sp steps until it fits on one line, but never below
 * [minFontSize]: a long term like «incomprehensibilities» stays whole instead of breaking mid-word.
 * [textModifier] goes on the text itself (for the marker, which must follow the letters).
 * [fitAll] sizes by the widest of several texts, so a row of labels shares one size.
 */
@Composable
fun FitWordText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    textModifier: Modifier = Modifier,
    minFontSize: TextUnit = 32.sp,
    fitAll: List<String> = listOf(text),
    measureStyle: TextStyle = style,
) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier) {
        val maxWidth = constraints.maxWidth
        val fitted = remember(fitAll, style, measureStyle, maxWidth, measurer) {
            if (maxWidth == Constraints.Infinity) return@remember style
            var size = style.fontSize.value
            while (size > minFontSize.value) {
                val sized = measureStyle.copy(fontSize = size.sp)
                val width = fitAll.maxOf { measurer.measure(it, sized, softWrap = false, maxLines = 1).size.width }
                if (width <= maxWidth) break
                size -= 2f
            }
            size = size.coerceAtLeast(minFontSize.value)
            val ratio = size / style.fontSize.value
            style.copy(
                fontSize = size.sp,
                lineHeight = if (style.lineHeight.isSp) (style.lineHeight.value * ratio).sp else style.lineHeight,
            )
        }
        Text(text, style = fitted, color = color, modifier = textModifier)
    }
}
