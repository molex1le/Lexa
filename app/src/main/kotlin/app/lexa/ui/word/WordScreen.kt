package app.lexa.ui.word

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lexa.R
import app.lexa.domain.model.WordSummary
import app.lexa.ui.components.BoxIndicator
import app.lexa.ui.components.EmptyState
import app.lexa.ui.components.LexaIconButton
import app.lexa.ui.components.markerHighlight
import app.lexa.ui.theme.LexaTheme
import app.lexa.ui.theme.Spacing

data class WordUiState(
    val isLoading: Boolean = true,
    val word: WordSummary? = null,
)

@Composable
fun WordRoute(onBack: () -> Unit, viewModel: WordViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    WordScreen(state, onBack)
}

/** Word card. Phase 1 shows the word, its translation and box; the full entry comes with phase 2/4. */
@Composable
fun WordScreen(state: WordUiState, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LexaTheme.colors
    Column(
        modifier
            .fillMaxSize()
            .background(colors.paper)
            .verticalScroll(rememberScrollState()),
    ) {
        LexaIconButton(R.drawable.ic_back, stringResource(R.string.action_back), onBack,
            Modifier.padding(start = Spacing.xs))
        Column(Modifier.padding(horizontal = Spacing.gutter)) {
            Spacer(Modifier.height(Spacing.l))
            val word = state.word
            when {
                state.isLoading -> Unit
                word == null -> EmptyState(stringResource(R.string.word_missing))
                else -> {
                    val isNew = word.learnedAt == null && word.box == 1
                    Text(
                        text = word.term,
                        style = LexaTheme.type.word,
                        color = colors.ink,
                        modifier = Modifier
                            .then(if (isNew) Modifier.markerHighlight() else Modifier)
                            .semantics { heading() },
                    )
                    Spacer(Modifier.height(Spacing.s))
                    Text(word.translation, style = LexaTheme.type.title, color = colors.ink)
                    Spacer(Modifier.height(Spacing.l))
                    if (word.learnedAt != null) {
                        Text(stringResource(R.string.word_learned), style = LexaTheme.type.meta, color = colors.pencil)
                    } else {
                        BoxIndicator(word.box)
                    }
                }
            }
        }
    }
}
