package app.lexa.ui.share

import androidx.compose.ui.text.font.FontWeight

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.lexa.R
import app.lexa.ui.components.MarkerButton
import app.lexa.ui.theme.LexaTheme
import app.lexa.ui.theme.Spacing
import app.lexa.ui.translator.ErrorBlock
import app.lexa.ui.translator.TranslationResult
import app.lexa.ui.translator.TranslatorPhase

/** One screen state of the translate sheet: the shared text was not a word, or the translator's phase. */
sealed interface SheetContent {
    data class NotAWord(val reason: SharedText) : SheetContent
    data class Lookup(val phase: TranslatorPhase) : SheetContent
}

data class SheetActions(
    val onOpenInApp: (Long) -> Unit = {},
    val onClose: () -> Unit = {},
    val onToggleFavorite: () -> Unit = {},
    val onPronounce: () -> Unit = {},
    val onUndoAdd: (Long) -> Unit = {},
    val onTranslateQuery: (String) -> Unit = {},
)

/**
 * The body of the sheet TranslateActivity shows over the calling app: the same result block as the
 * «Перевод» tab, a line saying the word is now in «Новые» with «Отменить», and «Открыть в Lexa».
 */
@Composable
fun TranslateSheetContent(content: SheetContent, actions: SheetActions, modifier: Modifier = Modifier) {
    val colors = LexaTheme.colors
    val type = LexaTheme.type
    // The result scrolls; «Добавлено…» and «Открыть в Lexa» stay pinned under it, always in reach.
    Column(modifier.fillMaxWidth().navigationBarsPadding()) {
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter),
        ) {
            SheetBody(content, actions)
            Spacer(Modifier.height(Spacing.l))
        }
        val result = (content as? SheetContent.Lookup)?.phase as? TranslatorPhase.Result
        if (result?.wordId != null) {
            Column(Modifier.background(colors.sheet).padding(horizontal = Spacing.gutter, vertical = Spacing.m)) {
                if (result.justAdded) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.translator_added),
                            style = type.meta,
                            color = colors.pencil,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { actions.onUndoAdd(result.wordId) }) {
                            Text(stringResource(R.string.action_undo), style = type.body, color = colors.ink)
                        }
                    }
                    Spacer(Modifier.height(Spacing.s))
                }
                // A text action, not a MarkerButton: the word above already carries the one marker.
                TextButton(
                    onClick = { actions.onOpenInApp(result.wordId) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.touch),
                ) {
                    Text(
                        stringResource(R.string.share_open_in_app),
                        style = type.body.copy(fontWeight = FontWeight(600)),
                        color = colors.ink,
                    )
                }
            }
        }
    }
}

@Composable
private fun SheetBody(content: SheetContent, actions: SheetActions) {
    val colors = LexaTheme.colors
    val type = LexaTheme.type
    when (content) {
        is SheetContent.NotAWord -> {
            Text(
                stringResource(if (content.reason is SharedText.Empty) R.string.share_empty else R.string.share_too_long),
                style = type.body,
                color = colors.ink,
            )
            Spacer(Modifier.height(Spacing.l))
            MarkerButton(stringResource(R.string.share_close), actions.onClose, Modifier.fillMaxWidth())
        }
        is SheetContent.Lookup -> when (val phase = content.phase) {
            TranslatorPhase.Idle, is TranslatorPhase.Loading -> Text(
                stringResource(
                    if ((phase as? TranslatorPhase.Loading)?.downloadingModel == true) {
                        R.string.translator_downloading_model
                    } else {
                        R.string.translator_loading
                    },
                ),
                style = type.body,
                color = colors.pencil,
                modifier = Modifier.heightIn(min = Spacing.touch),
            )
            is TranslatorPhase.Error -> {
                ErrorBlock(phase.error)
                Spacer(Modifier.height(Spacing.l))
                MarkerButton(stringResource(R.string.share_close), actions.onClose, Modifier.fillMaxWidth())
            }
            is TranslatorPhase.Result -> key(phase.translation.term, phase.wordId != null) {
                TranslationResult(phase, actions.onToggleFavorite, actions.onPronounce, actions.onTranslateQuery)
            }
        }
    }
}
