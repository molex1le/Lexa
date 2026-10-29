package app.lexa.ui.lists

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lexa.R
import app.lexa.domain.model.WordSort
import app.lexa.domain.model.WordSummary
import app.lexa.domain.model.searchAndSort
import app.lexa.ui.components.EmptyState
import app.lexa.ui.components.LexaIcon
import app.lexa.ui.components.LexaIconButton
import app.lexa.ui.components.ScreenHeader
import app.lexa.ui.components.formatDay
import app.lexa.ui.components.markedTextColor
import app.lexa.ui.components.markerHighlight
import app.lexa.ui.navigation.ListTab
import app.lexa.ui.theme.LexaCorner
import app.lexa.ui.theme.LexaTheme
import app.lexa.ui.theme.Spacing

data class ListsUiState(
    val tab: ListTab = ListTab.NEW,
    val isLoading: Boolean = true,
    val newWords: List<WordSummary> = emptyList(),
    val favorites: List<WordSummary> = emptyList(),
    val learned: List<WordSummary> = emptyList(),
    val query: String = "",
    val sort: WordSort = WordSort.ADDED,
    /** A word just swiped away: the snackbar offers to bring it back. */
    val deletedWordId: Long? = null,
) {
    val tabWords: List<WordSummary>
        get() = when (tab) {
            ListTab.NEW -> newWords
            ListTab.FAVORITES -> favorites
            ListTab.LEARNED -> learned
        }

    val visible: List<WordSummary> get() = tabWords.searchAndSort(query, sort)
}

data class ListsActions(
    val onSelectTab: (ListTab) -> Unit = {},
    val onOpenWord: (Long) -> Unit = {},
    val onTranslate: () -> Unit = {},
    val onQueryChange: (String) -> Unit = {},
    val onSortChange: (WordSort) -> Unit = {},
    val onToggleFavorite: (WordSummary) -> Unit = {},
    val onDelete: (Long) -> Unit = {},
    val onUndoDelete: (Long) -> Unit = {},
    val onDeleteConfirmed: (Long) -> Unit = {},
    val onReturnToLearning: (Long) -> Unit = {},
)

@Composable
fun ListsRoute(
    onOpenWord: (Long) -> Unit,
    onTranslate: () -> Unit,
    viewModel: ListsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ListsScreen(
        state,
        ListsActions(
            onSelectTab = viewModel::onSelectTab,
            onOpenWord = onOpenWord,
            onTranslate = onTranslate,
            onQueryChange = viewModel::onQueryChange,
            onSortChange = viewModel::onSortChange,
            onToggleFavorite = viewModel::onToggleFavorite,
            onDelete = viewModel::onDelete,
            onUndoDelete = viewModel::onUndoDelete,
            onDeleteConfirmed = viewModel::onDeleteConfirmed,
            onReturnToLearning = viewModel::onReturnToLearning,
        ),
    )
}

private val ListTab.label: Int
    @StringRes get() = when (this) {
        ListTab.NEW -> R.string.words_tab_new
        ListTab.FAVORITES -> R.string.words_tab_favorites
        ListTab.LEARNED -> R.string.words_tab_learned
    }

private val WordSort.label: Int
    @StringRes get() = when (this) {
        WordSort.ADDED -> R.string.words_sort_added
        WordSort.ALPHABET -> R.string.words_sort_alphabet
        WordSort.MISTAKES -> R.string.words_sort_mistakes
    }

/** «Слова»: Новые / Избранное / Выученные with search, sort, swipe to delete with undo. */
@Composable
fun ListsScreen(state: ListsUiState, actions: ListsActions = ListsActions(), modifier: Modifier = Modifier) {
    val colors = LexaTheme.colors
    val snackbar = remember { SnackbarHostState() }
    val deletedMessage = stringResource(R.string.words_deleted)
    val restoreLabel = stringResource(R.string.action_restore)
    LaunchedEffect(state.deletedWordId) {
        val id = state.deletedWordId ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(deletedMessage, actionLabel = restoreLabel, duration = SnackbarDuration.Long)
        if (result == SnackbarResult.ActionPerformed) actions.onUndoDelete(id) else actions.onDeleteConfirmed(id)
    }
    Box(modifier.fillMaxSize().background(colors.paper)) {
        Column(Modifier.fillMaxSize()) {
            // Search and sort appear once the tab has words: an empty list has nothing to look through.
            val searchable = !state.isLoading && (state.tabWords.isNotEmpty() || state.query.isNotEmpty())
            ScreenHeader(
                title = stringResource(R.string.words_title),
                trailing = if (searchable) ({ SortMenu(state.sort, actions.onSortChange) }) else null,
            )
            Spacer(Modifier.height(Spacing.s))
            Tabs(selected = state.tab, onSelect = actions.onSelectTab)
            Spacer(Modifier.height(Spacing.l))
            if (searchable) {
                SearchField(state.query, actions.onQueryChange)
                Spacer(Modifier.height(Spacing.s))
            }
            val visible = state.visible
            when {
                state.isLoading -> Unit
                state.tabWords.isEmpty() -> {
                    val (message, action) = when (state.tab) {
                        ListTab.NEW -> R.string.words_empty_new to R.string.action_translate_word
                        ListTab.FAVORITES -> R.string.words_empty_favorites to null
                        ListTab.LEARNED -> R.string.words_empty_learned to null
                    }
                    EmptyState(
                        message = stringResource(message),
                        actionLabel = action?.let { stringResource(it) },
                        onAction = actions.onTranslate,
                        modifier = Modifier.padding(horizontal = Spacing.gutter),
                    )
                }
                visible.isEmpty() -> EmptyState(
                    message = stringResource(R.string.words_search_empty, state.query.trim()),
                    modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.m),
                )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(visible, key = { it.id }) { word ->
                        SwipeToDelete(onDelete = { actions.onDelete(word.id) }) {
                            WordRow(word, learnedTab = state.tab == ListTab.LEARNED, actions = actions)
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter)) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = colors.ink,
                contentColor = colors.paper,
                actionColor = colors.paper,
                shape = LexaCorner,
            )
        }
    }
}

@Composable
private fun SortMenu(sort: WordSort, onSortChange: (WordSort) -> Unit) {
    val colors = LexaTheme.colors
    var open by remember { mutableStateOf(false) }
    Box {
        LexaIconButton(R.drawable.ic_sort, stringResource(R.string.words_sort), onClick = { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = colors.sheet) {
            WordSort.entries.forEach { option ->
                val selected = option == sort
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(option.label),
                            style = LexaTheme.type.body.copy(fontWeight = if (selected) FontWeight(600) else FontWeight(400)),
                            color = if (selected) colors.ink else colors.pencil,
                        )
                    },
                    onClick = {
                        open = false
                        onSortChange(option)
                    },
                    modifier = Modifier.semantics { this.selected = selected },
                )
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    val colors = LexaTheme.colors
    val type = LexaTheme.type
    val label = stringResource(R.string.words_search_label)
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = type.body.copy(color = colors.ink),
        cursorBrush = SolidColor(colors.ink),
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.gutter)
            .semantics { contentDescription = label },
        decorationBox = { field ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Spacing.touch)
                    .background(colors.sheet, LexaCorner)
                    .padding(start = Spacing.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LexaIcon(R.drawable.ic_search, null, tint = colors.pencil)
                Spacer(Modifier.width(Spacing.s))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text(stringResource(R.string.words_search_hint), style = type.body, color = colors.pencil)
                    field()
                }
                if (query.isNotEmpty()) {
                    LexaIconButton(R.drawable.ic_close, stringResource(R.string.words_search_clear), { onQueryChange("") })
                } else {
                    Spacer(Modifier.width(Spacing.m))
                }
            }
        },
    )
}

@Composable
private fun SwipeToDelete(onDelete: () -> Unit, content: @Composable () -> Unit) {
    val colors = LexaTheme.colors
    val state = rememberSwipeToDismissBoxState()
    val deleteLabel = stringResource(R.string.words_delete)
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        onDismiss = { if (it == SwipeToDismissBoxValue.EndToStart) onDelete() },
        modifier = Modifier.semantics {
            customActions = listOf(CustomAccessibilityAction(deleteLabel) { onDelete(); true })
        },
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().background(colors.sheet).padding(horizontal = Spacing.gutter),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(deleteLabel, style = LexaTheme.type.body, color = colors.redpen)
            }
        },
    ) {
        Box(Modifier.background(colors.paper)) { content() }
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
private fun WordRow(word: WordSummary, learnedTab: Boolean, actions: ListsActions) {
    val colors = LexaTheme.colors
    val type = LexaTheme.type
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable { actions.onOpenWord(word.id) }
            .padding(start = Spacing.gutter, end = Spacing.xs, top = Spacing.s, bottom = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val fresh = word.isFresh
            Text(
                text = word.term,
                style = type.title,
                color = if (fresh) markedTextColor() else colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (fresh) Modifier.markerHighlight() else Modifier,
            )
            if (word.translation.isNotEmpty()) {
                Text(
                    text = word.translation,
                    style = type.meta,
                    color = colors.pencil,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (learnedTab && word.learnedAt != null) {
                Text(stringResource(R.string.words_learned_on, formatDay(word.learnedAt!!)), style = type.meta, color = colors.pencil)
            }
        }
        if (learnedTab) {
            Box(
                Modifier
                    .heightIn(min = Spacing.touch)
                    .clickable(role = Role.Button) { actions.onReturnToLearning(word.id) }
                    .padding(horizontal = Spacing.s),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.action_return_to_learning),
                    style = type.meta.copy(fontWeight = FontWeight(600)),
                    color = colors.ink,
                )
            }
        }
        LexaIconButton(
            icon = if (word.isFavorite) R.drawable.ic_star_filled else R.drawable.ic_star,
            contentDescription = stringResource(
                if (word.isFavorite) R.string.action_remove_favorite else R.string.action_add_favorite,
            ),
            onClick = { actions.onToggleFavorite(word) },
        )
    }
}
