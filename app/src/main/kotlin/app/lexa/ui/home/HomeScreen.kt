package app.lexa.ui.home

import androidx.compose.ui.semantics.contentDescription

import androidx.compose.ui.semantics.semantics

import androidx.compose.ui.unit.dp

import androidx.compose.foundation.layout.fillMaxWidth

import androidx.compose.foundation.layout.Box

import app.lexa.domain.model.Settings

import app.lexa.domain.training.DailyProgress

import app.lexa.domain.model.VocabLevel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import app.lexa.ui.components.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.lexa.R
import app.lexa.domain.model.WordCounts
import app.lexa.ui.components.EmptyState
import app.lexa.ui.components.LexaIconButton
import app.lexa.ui.components.MarkerButton
import app.lexa.ui.components.ScreenHeader
import app.lexa.ui.theme.LexaTheme
import app.lexa.ui.theme.Spacing

data class HomeUiState(
    val isLoading: Boolean = true,
    val dueCount: Int = 0,
    val counts: WordCounts = WordCounts(),
    val streakDays: Int = 0,
    /** A running training session; null shows the tab's overview. */
    val session: TrainingPhase? = null,
    /** Null until the learner picks a level or takes the test: the tab asks for it first. */
    val level: VocabLevel? = null,
    val placement: Placement? = null,
    /** «12 из 20» for today. */
    val daily: DailyProgress = DailyProgress(done = 0, goal = Settings.DEFAULT_DAILY_GOAL),
) {
    val hasWords: Boolean get() = counts.learned + counts.learning + counts.new > 0
}

@Composable
fun HomeRoute(
    onOpenSettings: () -> Unit,
    onTranslate: () -> Unit,
    onWordReturned: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    HomeScreen(
        state = state,
        onOpenSettings = onOpenSettings,
        onTranslate = onTranslate,
        onStart = { viewModel.start() },
        level = LevelActions(
            onChoose = viewModel::chooseLevel,
            onTakeTest = viewModel::startPlacementTest,
            onAnswer = viewModel::answerPlacement,
            onChooseOther = viewModel::choosePlacementOther,
        ),
        training = TrainingActions(
            onAnswerChange = viewModel::onAnswerChange,
            onCheck = viewModel::check,
            onOverride = viewModel::overrideAsTypo,
            onNext = viewModel::next,
            onFinish = viewModel::finish,
            onWordReturned = onWordReturned,
            onHint = viewModel::showHint,
            onDontKnow = viewModel::dontKnow,
            onPronounce = viewModel::pronounce,
            onRemove = viewModel::removeWord,
            onMarkKnown = viewModel::markKnown,
        ),
    )
}

/** Level choice and placement test callbacks. */
class LevelActions(
    val onChoose: (VocabLevel) -> Unit = {},
    val onTakeTest: () -> Unit = {},
    val onAnswer: (Boolean) -> Unit = {},
    val onChooseOther: () -> Unit = {},
)

/** «Тренировка» tab: how many words are due today, the one action, and a one-line summary. */
@Composable
fun HomeScreen(
    state: HomeUiState,
    onOpenSettings: () -> Unit,
    onTranslate: () -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
    training: TrainingActions = TrainingActions(),
    level: LevelActions = LevelActions(),
) {
    state.session?.let {
        TrainingContent(it, training, modifier)
        return
    }
    state.placement?.let {
        PlacementContent(it, level.onAnswer, level.onChoose, level.onChooseOther, modifier)
        return
    }
    val colors = LexaTheme.colors
    Column(
        modifier
            .fillMaxSize()
            .background(colors.paper)
            .verticalScroll(rememberScrollState()),
    ) {
        // Before a level is chosen there is nothing to repeat yet: the tab just names itself.
        val picking = !state.isLoading && state.level == null
        ScreenHeader(
            title = stringResource(if (picking) R.string.nav_training else R.string.training_due_title),
            trailing = {
                LexaIconButton(R.drawable.ic_settings, stringResource(R.string.action_open_settings), onOpenSettings)
            },
            titleEnd = if (picking) null else {
                { Text(state.dueCount.toString(), style = LexaTheme.type.figure, color = colors.ink) }
            },
        )
        Spacer(Modifier.height(Spacing.xl))
        Column(Modifier.padding(horizontal = Spacing.gutter)) {
            val chosen = state.level
            when {
                state.isLoading -> Unit
                chosen == null -> LevelPicker(level.onChoose, level.onTakeTest)
                else -> {
                    Text(stringResource(R.string.training_level_line, chosen.name), style = LexaTheme.type.body, color = colors.pencil)
                    Spacer(Modifier.height(Spacing.l))
                    DailyGoal(state.daily)
                    Spacer(Modifier.height(Spacing.l))
                    MarkerButton(stringResource(R.string.training_start), onClick = onStart)
                }
            }
            Spacer(Modifier.height(Spacing.xl))
            Text(
                text = stringResource(
                    R.string.training_summary,
                    state.counts.learned.toString(),
                    pluralStringResource(R.plurals.days, state.streakDays, state.streakDays),
                ),
                style = LexaTheme.type.meta,
                color = colors.pencil,
            )
            Spacer(Modifier.height(Spacing.m))
        }
    }
}

/** «Сегодня 12 из 20» and a thin bar: ink on a tone step, full once the goal is reached. */
@Composable
private fun DailyGoal(progress: DailyProgress) {
    val colors = LexaTheme.colors
    val text = if (progress.reached) stringResource(R.string.goal_reached, progress.done, progress.goal)
    else stringResource(R.string.goal_today, progress.done, progress.goal)
    val description = stringResource(R.string.goal_title) + ": " + text
    Column(Modifier.semantics(mergeDescendants = true) { contentDescription = description }) {
        Text(text, style = LexaTheme.type.body, color = colors.ink)
        Spacer(Modifier.height(Spacing.s))
        Box(Modifier.fillMaxWidth().height(4.dp).background(colors.sheet)) {
            Box(Modifier.fillMaxWidth(progress.fraction).height(4.dp).background(colors.ink))
        }
    }
}
