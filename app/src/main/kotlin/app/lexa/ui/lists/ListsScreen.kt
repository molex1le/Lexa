package app.lexa.ui.lists

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lexa.R
import app.lexa.domain.model.WordSummary
import app.lexa.ui.components.EmptyState
import app.lexa.ui.components.LexaIcon
import app.lexa.ui.components.ScreenHeader
import app.lexa.ui.components.markerHighlight
import app.lexa.ui.navigation.ListTab
import app.lexa.ui.theme.LexaTheme
import app.lexa.ui.theme.Spacing

data class ListsUiState(
    val tab: ListTab = ListTab.NEW,
    val isLoading: Boolean = true,
    val newWords: List<WordSummary> = emptyList(),
    val favorites: List<WordSummary> = emptyList(),
    val learned: List<WordSummary> = emptyList(),
) {
    val visible: List<WordSummary>
        get() = when (tab) {
            ListTab.NEW -> newWords
            ListTab.FAVORITES -> favorites
            ListTab.LEARNED -> learned
        }
}

@Composable
fun ListsRoute(
    onOpenWord: (Long) -> Unit,
    onTranslate: () -> Unit,
    viewModel: ListsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ListsScreen(state, onSelectTab = viewModel::onSelectTab, onOpenWord = onOpenWord, onTranslate = onTranslate)
}

private val ListTab.label: Int
    @StringRes get() = when (this) {
        ListTab.NEW -> R.string.words_tab_new
        ListTab.FAVORITES -> R.string.words_tab_favorites
        ListTab.LEARNED -> R.string.words_tab_learned
    }

/** «Слова»: Новые / Избранное / Выученные. Search, sort and swipe arrive in phase 4. */
@Composable
fun ListsScreen(
    state: ListsUiState,
    onSelectTab: (ListTab) -> Unit,
    onOpenWord: (Long) -> Unit,
    onTranslate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LexaTheme.colors
    Column(modifier.fillMaxSize().background(colors.paper)) {
        ScreenHeader(title = stringResource(R.string.words_title))
        Spacer(Modifier.height(Spacing.s))
        Tabs(selected = state.tab, onSelect = onSelectTab)
        Spacer(Modifier.height(Spacing.l))
        when {
            state.isLoading -> Unit
            state.visible.isEmpty() -> {
                val (message, action) = when (state.tab) {
                    ListTab.NEW -> R.string.words_empty_new to R.string.action_translate_word
                    ListTab.FAVORITES -> R.string.words_empty_favorites to null
                    ListTab.LEARNED -> R.string.words_empty_learned to null
                }
                EmptyState(
                    message = stringResource(message),
                    actionLabel = action?.let { stringResource(it) },
                    onAction = onTranslate,
                    modifier = Modifier.padding(horizontal = Spacing.gutter),
                )
            }
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(state.visible, key = { it.id }) { word ->
                    WordRow(word, marked = state.tab == ListTab.NEW, onClick = { onOpenWord(word.id) })
                }
            }
        }
    }
}

@Composable
private fun Tabs(selected: ListTab, onSelect: (ListTab) -> Unit) {
    val colors = LexaTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.gutter - Spacing.s)
            .selectableGroup(),
    ) {
        ListTab.entries.forEach { tab ->
            val active = tab == selected
            val underline = colors.ink
            Box(
                Modifier
                    .heightIn(min = Spacing.touch)
                    .selectable(selected = active, role = Role.Tab, onClick = { onSelect(tab) })
                    .padding(horizontal = Spacing.s),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(tab.label),
                    style = LexaTheme.type.body.copy(fontWeight = if (active) FontWeight(600) else FontWeight(400)),
                    color = if (active) colors.ink else colors.pencil,
                    modifier = if (active) {
                        Modifier.drawBehind {
                            val y = size.height + 4.dp.toPx()
                            drawLine(underline, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx())
                        }
                    } else {
                        Modifier
                    },
                )
            }
        }
    }
}

@Composable
private fun WordRow(word: WordSummary, marked: Boolean, onClick: () -> Unit) {
    val colors = LexaTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.gutter, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = word.term,
                style = LexaTheme.type.title,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (marked) Modifier.markerHighlight() else Modifier,
            )
            if (word.translation.isNotEmpty()) {
                Text(
                    text = word.translation,
                    style = LexaTheme.type.meta,
                    color = colors.pencil,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (word.isFavorite) {
            LexaIcon(R.drawable.ic_star, stringResource(R.string.words_favorite), tint = colors.ink)
        }
    }
}
