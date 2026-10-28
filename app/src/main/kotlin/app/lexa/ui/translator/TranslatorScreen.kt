package app.lexa.ui.translator

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lexa.R
import app.lexa.domain.model.HistoryItem
import app.lexa.ui.components.LexaIconButton
import app.lexa.ui.components.MarkerButton
import app.lexa.ui.components.ScreenHeader
import app.lexa.ui.theme.LexaCorner
import app.lexa.ui.theme.LexaTheme
import app.lexa.ui.theme.Spacing

data class TranslatorUiState(
    val query: String = "",
    val recent: List<HistoryItem> = emptyList(),
) {
    val canTranslate: Boolean get() = query.isNotBlank()
}

@Composable
fun TranslatorRoute(
    onOpenHistory: () -> Unit,
    viewModel: TranslatorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    TranslatorScreen(
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onTranslate = viewModel::onTranslate,
        onOpenHistory = onOpenHistory,
    )
}

/** «Перевод» tab. Phase 1: the input and the empty hint; lookup arrives in phase 2. */
@Composable
fun TranslatorScreen(
    state: TranslatorUiState,
    onQueryChange: (String) -> Unit,
    onTranslate: () -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LexaTheme.colors
    val type = LexaTheme.type
    Column(
        modifier
            .fillMaxSize()
            .background(colors.paper)
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(
            title = stringResource(R.string.translator_title),
            trailing = {
                LexaIconButton(R.drawable.ic_history, stringResource(R.string.action_open_history), onOpenHistory)
            },
        )
        Column(
            Modifier
                .padding(horizontal = Spacing.gutter)
                .widthIn(max = Spacing.maxColumn),
        ) {
            Spacer(Modifier.height(Spacing.l))
            val inputLabel = stringResource(R.string.translator_input_label)
            BasicTextField(
                value = state.query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = type.title.copy(color = colors.ink),
                cursorBrush = SolidColor(colors.ink),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Search,
                ),
                keyboardActions = KeyboardActions(onSearch = { if (state.canTranslate) onTranslate() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = inputLabel },
                decorationBox = { field ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .background(colors.sheet, LexaCorner)
                            .padding(horizontal = Spacing.m),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (state.query.isEmpty()) {
                            Text(stringResource(R.string.translator_hint), style = type.title, color = colors.pencil)
                        }
                        field()
                    }
                },
            )
            Spacer(Modifier.height(Spacing.m))
            MarkerButton(
                text = stringResource(R.string.translator_action),
                onClick = onTranslate,
                enabled = state.canTranslate,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Spacing.xl))
            if (state.recent.isEmpty()) {
                Text(stringResource(R.string.translator_empty), style = type.body, color = colors.pencil)
            } else {
                Text(stringResource(R.string.translator_recent), style = type.title, color = colors.ink)
                Spacer(Modifier.height(Spacing.s))
                state.recent.forEach { item ->
                    Text(
                        text = item.query,
                        style = type.body,
                        color = colors.ink,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = Spacing.touch)
                            .clickable { onQueryChange(item.query) }
                            .padding(vertical = Spacing.s + Spacing.xs),
                    )
                }
            }
            Spacer(Modifier.height(Spacing.m))
        }
    }
}


