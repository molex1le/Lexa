package app.lexa.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import app.lexa.ui.components.formatDay
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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import app.lexa.ui.components.rememberReducedMotion
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
import androidx.lifecycle.compose.LifecycleResumeEffect
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
    /** Learned words, most recent first. */
    val learnedWords: List<WordSummary> = emptyList(),
)

data class StatsActions(
    val onOpenWord: (Long) -> Unit = {},
    val onPracticeHard: () -> Unit = {},
    val onOpenLearned: () -> Unit = {},
)

/** How many learned words the statistics list before «Все выученные». */
const val LEARNED_PREVIEW = 5

@Composable
fun StatsRoute(actions: StatsActions, viewModel: StatsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    StatsScreen(state, actions)
}

/**
 * Statistics as typography: big Literata figures with a caption, two Canvas charts.
 * Every block keeps its frame when empty, and zero is printed as 0.
 */
@Composable
fun StatsScreen(state: StatsUiState, actions: StatsActions = StatsActions(), modifier: Modifier = Modifier) {
    val colors = LexaTheme.colors
    val type = LexaTheme.type
    // Figures count up and bars grow once, the first time this screen shows data; coming back to the tab
    // (state saved by navigation) or a later data change shows the numbers as they are.
    val reduced = rememberReducedMotion()
    var shown by rememberSaveable { mutableStateOf(false) }
    val animateIn = remember { !shown && !reduced }
    LaunchedEffect(state) { if (state != StatsUiState()) shown = true }
    CompositionLocalProvider(LocalAnimateIn provides animateIn) {
        StatsColumn(state, actions, modifier)
    }
}

/** Whether figures and bars on this screen animate in from zero. */
private val LocalAnimateIn = staticCompositionLocalOf { false }

/** [value] as shown: counting up from 0 over ~400 ms when the screen animates in, else as it is. */
@Composable
private fun countUp(value: Int): Int {
    val animate = LocalAnimateIn.current
    val shown = remember { Animatable(if (animate) 0f else value.toFloat()) }
    LaunchedEffect(value) {
        if (animate && value > 0) shown.animateTo(value.toFloat(), tween(durationMillis = 400, easing = FastOutSlowInEasing))
        else shown.snapTo(value.toFloat())
    }
    return shown.value.roundToInt()
}

@Composable
private fun StatsColumn(state: StatsUiState, actions: StatsActions, modifier: Modifier) {
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
                Figure(countUp(state.counts.learned).toString(), stringResource(R.string.stats_learned), Modifier.weight(1f))
                Figure(countUp(state.counts.learning).toString(), stringResource(R.string.stats_learning), Modifier.weight(1f))
                Figure(countUp(state.counts.new).toString(), stringResource(R.string.stats_new), Modifier.weight(1f))
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
                    value = countUp(state.streak).toString(),
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

            Block(stringResource(R.string.stats_learned_list_title)) {
                if (state.learnedWords.isEmpty()) {
                    Text(stringResource(R.string.words_empty_learned), style = type.body, color = colors.pencil)
                } else {
                    state.learnedWords.take(LEARNED_PREVIEW).forEach { word ->
                        WordLine(word.term, formatDay(word.learnedAt ?: 0), colors.pencil) { actions.onOpenWord(word.id) }
                    }
                    if (state.learnedWords.size > LEARNED_PREVIEW) {
                        TextAction(stringResource(R.string.stats_learned_all), actions.onOpenLearned)
                    }
                }
            }

            Block(stringResource(R.string.stats_hard_title)) {
                if (state.hardWords.isEmpty()) {
                    Text(stringResource(R.string.stats_hard_empty), style = type.body, color = colors.pencil)
                } else {
                    state.hardWords.forEach { word ->
                        WordLine(
                            word.term,
                            pluralStringResource(R.plurals.word_mistakes, word.mistakesCount, word.mistakesCount),
                            colors.redpen,
                        ) { actions.onOpenWord(word.id) }
                    }
                    // A text action: today's bar already carries the screen's one marker.
                    TextAction(stringResource(R.string.stats_hard_practice), actions.onPracticeHard)
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

/** One word per line, with a fact on the right; the whole line opens the word card. */
@Composable
private fun WordLine(term: String, fact: String, factColor: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.touch)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            term,
            style = LexaTheme.type.body,
            color = LexaTheme.colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(Spacing.s))
        Text(fact, style = LexaTheme.type.meta, color = factColor)
    }
}

/** A secondary action in ink: a verb, 48dp tall, no container. */
@Composable
private fun TextAction(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = Spacing.touch)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(label, style = LexaTheme.type.body.copy(fontWeight = FontWeight(600)), color = LexaTheme.colors.ink)
    }
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
            text = percent?.let { stringResource(R.string.stats_accuracy_percent, countUp(it)) }
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
private const val ChartFloor = 4
private const val BarStaggerMillis = 45L

@Composable
private fun DailyBars(values: List<Int>) {
    val colors = LexaTheme.colors
    val total = values.sum()
    val description = stringResource(R.string.stats_chart_description, total.toString(), values.lastOrNull()?.toString() ?: "0")
    // Bars grow one after another, 45 ms apart, the first time data arrives.
    val animate = LocalAnimateIn.current
    val growth = remember(values.size) { List(values.size) { Animatable(if (animate) 0f else 1f) } }
    LaunchedEffect(total > 0) {
        if (total == 0) return@LaunchedEffect
        growth.forEachIndexed { index, bar ->
            launch {
                delay(index * BarStaggerMillis)
                bar.animateTo(1f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow))
            }
        }
    }
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(120.dp)
            .semantics { contentDescription = description },
    ) {
        val gap = 2.dp.toPx()
        val stub = 2.dp.toPx()
        val barWidth = (size.width - gap * (values.size - 1)) / values.size
        // A floor of 4 keeps a single learned word from filling the chart; the three gridlines then mark
        // whole counts (1, 2, 3 at the floor) instead of arbitrary fractions.
        val max = values.maxOrNull()?.takeIf { it > 0 }?.coerceAtLeast(ChartFloor) ?: 0
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
            val h = if (max == 0 || value == 0) stub else stub + (size.height * value / max - stub) * growth[index].value
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
