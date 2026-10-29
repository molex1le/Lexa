package app.lexa.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import app.lexa.ui.components.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lexa.R
import app.lexa.domain.leitner.LeitnerRules
import app.lexa.domain.model.AnswerTotals
import app.lexa.domain.model.WordCounts
import app.lexa.domain.model.WordSummary
import app.lexa.ui.components.ScreenHeader
import app.lexa.ui.theme.LexaTheme
import app.lexa.ui.theme.Spacing

const val CHART_DAYS = 30

data class StatsUiState(
    val counts: WordCounts = WordCounts(),
    val learnedToday: Int = 0,
    val learnedWeek: Int = 0,
    val learnedMonth: Int = 0,
    /** Learned per day, oldest first; the last element is today. */
    val learnedByDay: List<Int> = List(CHART_DAYS) { 0 },
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val weekAnswers: AnswerTotals = AnswerTotals(),
    val allAnswers: AnswerTotals = AnswerTotals(),
    val hardWords: List<WordSummary> = emptyList(),
    /** Words in learning per box 1..5. */
    val boxes: List<Int> = List(LeitnerRules.LAST_BOX) { 0 },
)

@Composable
fun StatsRoute(viewModel: StatsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    StatsScreen(state)
}

/**
 * Statistics as typography: big Literata figures with a caption, two Canvas charts.
 * Every block keeps its frame when empty, and zero is printed as 0.
 */
@Composable
fun StatsScreen(state: StatsUiState, modifier: Modifier = Modifier) {
    val colors = LexaTheme.colors
    val type = LexaTheme.type
    Column(
        modifier
            .fillMaxSize()
            .background(colors.paper)
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(title = stringResource(R.string.stats_title))
        Column(Modifier.padding(horizontal = Spacing.gutter)) {
            Spacer(Modifier.height(Spacing.l))
            Row(Modifier.fillMaxWidth()) {
                Figure(state.counts.learned.toString(), stringResource(R.string.stats_learned), Modifier.weight(1f))
                Figure(state.counts.learning.toString(), stringResource(R.string.stats_learning), Modifier.weight(1f))
                Figure(state.counts.new.toString(), stringResource(R.string.stats_new), Modifier.weight(1f))
            }

            Block(stringResource(R.string.stats_by_day_title)) {
                Text(
                    text = listOf(
                        stringResource(R.string.stats_today, state.learnedToday.toString()),
                        stringResource(R.string.stats_week, state.learnedWeek.toString()),
                        stringResource(R.string.stats_month, state.learnedMonth.toString()),
                    ).joinToString(" · "),
                    style = type.meta,
                    color = colors.pencil,
                )
                Spacer(Modifier.height(Spacing.m))
                DailyBars(state.learnedByDay)
                Row(Modifier.fillMaxWidth().padding(top = Spacing.xs)) {
                    Text(stringResource(R.string.stats_chart_start), style = type.meta, color = colors.pencil,
                        modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.stats_chart_end), style = type.meta, color = colors.pencil)
                }
                if (state.learnedByDay.sum() == 0) {
                    Spacer(Modifier.height(Spacing.s))
                    Text(stringResource(R.string.stats_chart_empty), style = type.body, color = colors.pencil)
                }
            }

            Block(stringResource(R.string.stats_streak_title)) {
                Figure(
                    value = state.streak.toString(),
                    caption = pluralStringResource(R.plurals.days_in_row, state.streak),
                )
                Spacer(Modifier.height(Spacing.s))
                Text(
                    stringResource(
                        R.string.stats_streak_best,
                        pluralStringResource(R.plurals.days, state.bestStreak, state.bestStreak),
                    ),
                    style = type.meta,
                    color = colors.pencil,
                )
            }

            Block(stringResource(R.string.stats_accuracy_title)) {
                Row(Modifier.fillMaxWidth()) {
                    AccuracyFigure(state.weekAnswers, stringResource(R.string.stats_accuracy_week), Modifier.weight(1f))
                    AccuracyFigure(state.allAnswers, stringResource(R.string.stats_accuracy_all), Modifier.weight(1f))
                }
                if (state.allAnswers.answers == 0) {
                    Spacer(Modifier.height(Spacing.s))
                    Text(stringResource(R.string.stats_accuracy_none), style = type.body, color = colors.pencil)
                }
            }

            Block(stringResource(R.string.stats_hard_title)) {
                if (state.hardWords.isEmpty()) {
                    Text(stringResource(R.string.stats_hard_empty), style = type.body, color = colors.pencil)
                } else {
                    state.hardWords.forEach { word ->
                        Row(Modifier.fillMaxWidth().padding(vertical = Spacing.s)) {
                            Text(word.term, style = type.body, color = colors.ink, modifier = Modifier.weight(1f))
                            Text(word.mistakesCount.toString(), style = type.meta, color = colors.redpen)
                        }
                    }
                }
            }

            Block(stringResource(R.string.stats_boxes_title)) {
                BoxBars(state.boxes)
            }
            Spacer(Modifier.height(Spacing.xl))
        }
    }
}

@Composable
private fun Block(title: String, content: @Composable () -> Unit) {
    Spacer(Modifier.height(Spacing.xl))
    Text(
        text = title,
        style = LexaTheme.type.title,
        color = LexaTheme.colors.ink,
        modifier = Modifier.semantics { heading() },
    )
    Spacer(Modifier.height(Spacing.s))
    content()
}

@Composable
private fun Figure(value: String, caption: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, style = LexaTheme.type.figure, color = LexaTheme.colors.ink)
        Text(caption, style = LexaTheme.type.meta, color = LexaTheme.colors.pencil)
    }
}

@Composable
private fun AccuracyFigure(totals: AnswerTotals, period: String, modifier: Modifier = Modifier) {
    val percent = totals.accuracyPercent
    val caption = "$period · " + pluralStringResource(R.plurals.answers, totals.answers, totals.answers)
    Column(modifier) {
        Text(
            text = percent?.let { stringResource(R.string.stats_accuracy_percent, it) }
                ?: stringResource(R.string.stats_accuracy_undefined),
            style = LexaTheme.type.figure,
            color = if (percent == null) LexaTheme.colors.pencil else LexaTheme.colors.ink,
        )
        Text(caption, style = LexaTheme.type.meta, color = LexaTheme.colors.pencil)
    }
}

/**
 * 30 bars, oldest on the left. Empty days are a 2dp stub on the baseline, so the frame of the chart
 * is drawn even with no data. Today's bar is the one place the marker appears on this screen.
 */
@Composable
private fun DailyBars(values: List<Int>) {
    val colors = LexaTheme.colors
    val total = values.sum()
    val description = stringResource(R.string.stats_chart_description, total.toString())
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(120.dp)
            .semantics { contentDescription = description },
    ) {
        val gap = 2.dp.toPx()
        val stub = 2.dp.toPx()
        val barWidth = (size.width - gap * (values.size - 1)) / values.size
        val max = values.maxOrNull()?.takeIf { it > 0 } ?: 0
        val grid = colors.pencil.copy(alpha = 0.16f)
        if (max > 0) {
            for (i in 1..3) {
                val y = size.height * i / 4f
                drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            }
        }
        drawLine(colors.pencil.copy(alpha = 0.4f), Offset(0f, size.height), Offset(size.width, size.height),
            strokeWidth = 1.dp.toPx())
        values.forEachIndexed { index, value ->
            val isToday = index == values.lastIndex
            val h = if (max == 0 || value == 0) stub else size.height * value / max
            val color = when {
                isToday -> colors.marker
                value == 0 -> colors.pencil.copy(alpha = 0.3f)
                else -> colors.ink
            }
            drawRect(color, Offset(index * (barWidth + gap), size.height - h), Size(barWidth, h))
        }
    }
}

/** Distribution of words in learning over boxes 1..5: count above, box number below. */
@Composable
private fun BoxBars(values: List<Int>) {
    val colors = LexaTheme.colors
    val type = LexaTheme.type
    val max = values.maxOrNull()?.takeIf { it > 0 } ?: 0
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        values.forEachIndexed { index, value ->
            val box = index + 1
            val description = stringResource(R.string.stats_boxes_description, box, value)
            Column(
                Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) { contentDescription = description },
                horizontalAlignment = Alignment.Start,
            ) {
                Text(value.toString(), style = type.title, color = colors.ink)
                Spacer(Modifier.height(Spacing.xs))
                Canvas(Modifier.fillMaxWidth().height(64.dp)) {
                    val stub = 2.dp.toPx()
                    val h = if (max == 0 || value == 0) stub else size.height * value / max
                    drawRect(
                        if (value == 0) colors.pencil.copy(alpha = 0.3f) else colors.ink,
                        Offset(0f, size.height - h),
                        Size(size.width, h),
                    )
                }
                Spacer(Modifier.height(Spacing.xs))
                Text(box.toString(), style = type.meta, color = colors.pencil, textAlign = TextAlign.Start,
                    modifier = Modifier.width(24.dp))
            }
        }
    }
}
