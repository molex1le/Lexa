package app.lexa.ui.word

import androidx.compose.ui.text.style.TextDecoration

import androidx.compose.ui.semantics.Role

import androidx.compose.foundation.clickable

import androidx.compose.foundation.layout.heightIn

import androidx.compose.foundation.layout.Box

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lexa.R
import app.lexa.domain.model.WordDetails
import app.lexa.ui.components.BoxIndicator
import app.lexa.ui.components.EmptyState
import app.lexa.ui.components.LexaIconButton
import app.lexa.ui.components.MarkerButton
import app.lexa.ui.components.formatDay
import app.lexa.ui.components.markedTextColor
import app.lexa.ui.components.markerHighlight
import app.lexa.ui.components.pluralStringResource
import app.lexa.ui.theme.LexaTheme
import app.lexa.ui.theme.Spacing
import app.lexa.ui.translator.BlockTitle
import app.lexa.ui.translator.ExampleRow
import app.lexa.ui.translator.Synonyms
import app.lexa.ui.translator.posName

data class WordUiState(
    val isLoading: Boolean = true,
    val details: WordDetails? = null,
)

data class WordActions(
    val onBack: () -> Unit = {},
    val onToggleFavorite: () -> Unit = {},
    val onPronounce: () -> Unit = {},
    val onReturnToLearning: () -> Unit = {},
    val onMarkKnown: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onTranslateQuery: (String) -> Unit = {},
)

@Composable
fun WordRoute(onBack: () -> Unit, onTranslateQuery: (String) -> Unit, viewModel: WordViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    WordScreen(
        state,
        WordActions(
            onBack = onBack,
            onToggleFavorite = viewModel::onToggleFavorite,
            onPronounce = viewModel::onPronounce,
            onReturnToLearning = viewModel::onReturnToLearning,
            onMarkKnown = viewModel::onMarkKnown,
            onDelete = { viewModel.onDelete(onBack) },
            onTranslateQuery = onTranslateQuery,
        ),
    )
}

/** Word card: the stored entry in one scrollable column, then where the word stands in learning. */
@Composable
fun WordScreen(state: WordUiState, actions: WordActions = WordActions(), modifier: Modifier = Modifier) {
    val colors = LexaTheme.colors
    val type = LexaTheme.type
    val details = state.details
    Column(
        modifier
            .fillMaxSize()
            .background(colors.paper)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.xs),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            LexaIconButton(R.drawable.ic_back, stringResource(R.string.action_back), actions.onBack)
            if (details != null) {
                val favorite = details.summary.isFavorite
                LexaIconButton(
                    icon = if (favorite) R.drawable.ic_star_filled else R.drawable.ic_star,
                    contentDescription = stringResource(
                        if (favorite) R.string.action_remove_favorite else R.string.action_add_favorite,
                    ),
                    onClick = actions.onToggleFavorite,
                )
            }
        }
        Column(Modifier.padding(horizontal = Spacing.gutter)) {
            Spacer(Modifier.height(Spacing.m))
            when {
                state.isLoading -> Unit
                details == null -> EmptyState(stringResource(R.string.word_missing))
                else -> {
                    val word = details.summary
                    val entry = details.entry
                    Text(
                        text = word.term,
                        style = type.word,
                        color = if (word.isFresh) markedTextColor() else colors.ink,
                        modifier = Modifier
                            .then(if (word.isFresh) Modifier.markerHighlight() else Modifier)
                            .semantics { heading() },
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        entry.ipa?.let { Text(it, style = type.meta, color = colors.pencil) }
                        LexaIconButton(R.drawable.ic_sound, stringResource(R.string.action_pronounce), actions.onPronounce)
                    }
                    if (entry.senses.isEmpty() && word.translation.isNotEmpty()) {
                        Text(word.translation, style = type.title, color = colors.ink)
                    }
                    entry.senses.forEach { sense ->
                        Spacer(Modifier.height(Spacing.m))
                        sense.pos?.let { Text(posName(it), style = type.meta, color = colors.pencil) }
                        Text(sense.translations.joinToString(", "), style = type.title, color = colors.ink)
                    }
                    entry.definition?.let {
                        BlockTitle(R.string.translator_definition)
                        Text(it, style = type.body, color = colors.ink)
                    }
                    if (entry.examples.isNotEmpty()) {
                        BlockTitle(R.string.translator_examples)
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                            entry.examples.forEach { ExampleRow(it) }
                        }
                    }
                    if (entry.synonyms.isNotEmpty()) {
                        BlockTitle(R.string.translator_synonyms)
                        Synonyms(entry.synonyms, actions.onTranslateQuery)
                    }

                    Spacer(Modifier.height(Spacing.xl))
                    val learnedAt = word.learnedAt
                    if (learnedAt != null) {
                        Text(
                            stringResource(R.string.words_learned_on, formatDay(learnedAt)),
                            style = type.title,
                            color = colors.ink,
                        )
                        Spacer(Modifier.height(Spacing.m))
                        MarkerButton(stringResource(R.string.action_return_to_learning), actions.onReturnToLearning)
                    } else {
                        BoxIndicator(word.box)
                        Spacer(Modifier.height(Spacing.s))
                        Text(
                            stringResource(R.string.word_boxes_description, word.box),
                            style = type.meta,
                            color = colors.ink,
                        )
                    }
                    Spacer(Modifier.height(Spacing.m))
                    Text(
                        pluralStringResource(R.plurals.word_mistakes, word.mistakesCount, word.mistakesCount),
                        style = type.meta,
                        color = colors.pencil,
                    )
                    Text(
                        stringResource(R.string.word_added_on, formatDay(word.addedAt)),
                        style = type.meta,
                        color = colors.pencil,
                    )
                    Spacer(Modifier.height(Spacing.m))
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.l)) {
                        if (learnedAt == null) WordTextAction(stringResource(R.string.training_mark_known), actions.onMarkKnown)
                        WordTextAction(stringResource(R.string.word_delete), actions.onDelete)
                    }
                    Spacer(Modifier.height(Spacing.xl))
                }
            }
        }
    }
}

/** Underlined ink text with a 48dp target: the card's secondary actions. */
@Composable
private fun WordTextAction(label: String, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = Spacing.touch).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(label, style = LexaTheme.type.body.copy(textDecoration = TextDecoration.Underline), color = LexaTheme.colors.ink)
    }
}
