package app.lexa.ui.home

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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
) {
    val hasWords: Boolean get() = counts.learned + counts.learning + counts.new > 0
}

@Composable
fun HomeRoute(
    onOpenSettings: () -> Unit,
    onTranslate: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeScreen(state = state, onOpenSettings = onOpenSettings, onTranslate = onTranslate, onStart = {})
}

/** «Тренировка» tab: how many words are due today, the one action, and a one-line summary. */
@Composable
fun HomeScreen(
    state: HomeUiState,
    onOpenSettings: () -> Unit,
    onTranslate: () -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LexaTheme.colors
    Column(
        modifier
            .fillMaxSize()
            .background(colors.paper)
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(
            title = stringResource(R.string.training_due_title),
            trailing = {
                LexaIconButton(R.drawable.ic_settings, stringResource(R.string.action_open_settings), onOpenSettings)
            },
            titleEnd = {
                Text(state.dueCount.toString(), style = LexaTheme.type.figure, color = colors.ink)
            },
        )
        Spacer(Modifier.height(Spacing.xl))
        Column(Modifier.padding(horizontal = Spacing.gutter)) {
            when {
                state.isLoading -> Unit
                !state.hasWords -> EmptyState(
                    message = stringResource(R.string.training_empty),
                    actionLabel = stringResource(R.string.action_translate_word),
                    onAction = onTranslate,
                )
                state.dueCount == 0 -> EmptyState(message = stringResource(R.string.training_done_today))
                else -> MarkerButton(stringResource(R.string.training_start), onClick = onStart)
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
