package app.lexa.ui.home

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.lexa.R
import app.lexa.domain.model.VocabLevel
import app.lexa.ui.components.MarkerButton
import app.lexa.ui.theme.LexaTheme
import app.lexa.ui.theme.Spacing

/** Placement test state on the «Тренировка» tab. */
sealed interface Placement {
    data class Asking(val term: String, val position: Int, val total: Int) : Placement
    data class Result(val level: VocabLevel, val known: Int, val total: Int) : Placement
}

@Composable
fun levelLabel(level: VocabLevel): String = stringResource(
    when (level) {
        VocabLevel.A1 -> R.string.level_a1
        VocabLevel.A2 -> R.string.level_a2
        VocabLevel.B1 -> R.string.level_b1
        VocabLevel.B2 -> R.string.level_b2
        VocabLevel.C1 -> R.string.level_c1
    },
)

/** First start: pick a level, or take the test (the one marker on the screen). */
@Composable
fun LevelPicker(onChoose: (VocabLevel) -> Unit, onTakeTest: () -> Unit) {
    val colors = LexaTheme.colors
    val type = LexaTheme.type
    Text(stringResource(R.string.level_question), style = type.title, color = colors.ink, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(Spacing.s))
    Text(stringResource(R.string.level_question_hint), style = type.body, color = colors.pencil)
    Spacer(Modifier.height(Spacing.l))
    MarkerButton(stringResource(R.string.level_take_test), onClick = onTakeTest, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(Spacing.l))
    Text(stringResource(R.string.level_or_choose), style = type.meta, color = colors.pencil)
    Spacer(Modifier.height(Spacing.s))
    VocabLevel.entries.forEach { level ->
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(role = Role.Button) { onChoose(level) }
                .padding(vertical = Spacing.s),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(levelLabel(level), style = type.body, color = colors.ink)
        }
    }
}

/** «Знаю / Не знаю» for one word, or the result with the level it gives. */
@Composable
fun PlacementContent(
    placement: Placement,
    onAnswer: (Boolean) -> Unit,
    onAccept: (VocabLevel) -> Unit,
    onChooseOther: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LexaTheme.colors
    val type = LexaTheme.type
    Column(
        modifier
            .fillMaxSize()
            .background(colors.paper)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.gutter),
    ) {
        Spacer(Modifier.height(Spacing.touch + Spacing.l))
        when (placement) {
            is Placement.Asking -> {
                Text(
                    stringResource(R.string.training_progress, placement.position, placement.total),
                    style = type.meta,
                    color = colors.pencil,
                )
                Spacer(Modifier.height(Spacing.l))
                Text(placement.term, style = type.word, color = colors.ink)
                Spacer(Modifier.height(Spacing.s))
                Text(stringResource(R.string.level_test_hint), style = type.body, color = colors.pencil)
                Spacer(Modifier.height(Spacing.xl))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    AnswerChoice(stringResource(R.string.level_know), Modifier.weight(1f)) { onAnswer(true) }
                    AnswerChoice(stringResource(R.string.level_dont_know), Modifier.weight(1f)) { onAnswer(false) }
                }
            }
            is Placement.Result -> {
                Text(
                    stringResource(R.string.level_result, placement.level.name),
                    style = type.heading,
                    color = colors.ink,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(Spacing.s))
                Text(
                    stringResource(R.string.level_result_detail, placement.known, placement.total),
                    style = type.body,
                    color = colors.pencil,
                )
                Spacer(Modifier.height(Spacing.s))
                Text(levelLabel(placement.level), style = type.body, color = colors.ink)
                Spacer(Modifier.height(Spacing.l))
                MarkerButton(
                    stringResource(R.string.level_start_with, placement.level.name),
                    onClick = { onAccept(placement.level) },
                )
                Spacer(Modifier.height(Spacing.s))
                Box(
                    Modifier.heightIn(min = Spacing.touch).clickable(role = Role.Button, onClick = onChooseOther),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(stringResource(R.string.level_choose_other), style = type.body, color = colors.ink)
                }
            }
        }
    }
}

/** Two equal answers: a tone step on the sheet, no marker – neither is "the" action. */
@Composable
private fun AnswerChoice(label: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(LexaTheme.colors.sheet)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = LexaTheme.type.body.copy(fontWeight = FontWeight(600)), color = LexaTheme.colors.ink)
    }
}
