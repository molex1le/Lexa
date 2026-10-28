package app.lexa.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lexa.R
import app.lexa.domain.model.HistoryItem
import app.lexa.ui.components.EmptyState
import app.lexa.ui.components.ScreenHeader
import app.lexa.ui.theme.LexaTheme
import app.lexa.ui.theme.Spacing

data class HistoryUiState(
    val isLoading: Boolean = true,
    val items: List<HistoryItem> = emptyList(),
)

@Composable
fun HistoryRoute(
    onBack: () -> Unit,
    onTranslate: (String?) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HistoryScreen(state, onBack = onBack, onTranslate = onTranslate)
}

/** Translation history. Tapping a query opens the translator with it. */
@Composable
fun HistoryScreen(
    state: HistoryUiState,
    onBack: () -> Unit,
    onTranslate: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LexaTheme.colors
    Column(modifier.fillMaxSize().background(colors.paper)) {
        ScreenHeader(title = stringResource(R.string.history_title), onBack = onBack)
        Spacer(Modifier.height(Spacing.l))
        when {
            state.isLoading -> Unit
            state.items.isEmpty() -> EmptyState(
                message = stringResource(R.string.history_empty),
                actionLabel = stringResource(R.string.action_translate_word),
                onAction = { onTranslate(null) },
                modifier = Modifier.padding(horizontal = Spacing.gutter),
            )
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(state.items, key = { it.id }) { item ->
                    Text(
                        text = item.query,
                        style = LexaTheme.type.body,
                        color = colors.ink,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = Spacing.touch)
                            .clickable { onTranslate(item.query) }
                            .padding(horizontal = Spacing.gutter, vertical = Spacing.s + Spacing.xs),
                    )
                }
            }
        }
    }
}
