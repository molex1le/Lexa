package app.lexa.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.lexa.R
import app.lexa.domain.model.Example
import app.lexa.ui.components.BoxIndicator
import app.lexa.ui.components.MarkerButton
import app.lexa.ui.components.pluralStringResource
import app.lexa.ui.theme.LexaCorner
import app.lexa.ui.theme.LexaTheme
import app.lexa.ui.theme.Spacing

/** What a card shows. [expectsEnglish] is the RU→EN side: the term must be typed. */
data class TrainingCardUi(
    val prompt: String,
    val ipa: String?,
    val box: Int,
    val expectsEnglish: Boolean,
)

enum class Verdict { CORRECT, TYPO, WRONG, OVERRIDDEN }

/** The training state machine shown on the «Тренировка» tab while a session runs. */
sealed interface TrainingPhase {
    data class Question(
        val card: TrainingCardUi,
        val position: Int,
        val total: Int,
        val answer: String = "",
    ) : TrainingPhase {
        val canCheck: Boolean get() = answer.isNotBlank()
    }

    data class Feedback(
        val card: TrainingCardUi,
        val position: Int,
        val total: Int,
        val answer: String,
        val verdict: Verdict,
        /** The right spelling (typo) or the expected answers (wrong). */
        val correctAnswer: String,
        val boxAfter: Int,
        val learned: Boolean,
        val example: Example?,
    ) : TrainingPhase

    data class Done(val correct: Int, val total: Int) : TrainingPhase
}

/** Session callbacks, grouped so the stateless screen stays readable. */
class TrainingActions(
    val onAnswerChange: (String) -> Unit = {},
    val onCheck: () -> Unit = {},
    val onOverride: () -> Unit = {},
    val onNext: () -> Unit = {},
    val onFinish: () -> Unit = {},
)

@Composable
fun TrainingContent(phase: TrainingPhase, actions: TrainingActions, modifier: Modifier = Modifier) {
    val colors = LexaTheme.colors
    BackHandler(onBack = actions.onFinish)
    Column(
        modifier
            .fillMaxSize()
            .background(colors.paper)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.gutter),
    ) {
        when (phase) {
            is TrainingPhase.Question -> QuestionCard(phase, actions)
            is TrainingPhase.Feedback -> FeedbackCard(phase, actions)
            is TrainingPhase.Done -> SessionDone(phase, actions)
        }
        Spacer(Modifier.height(Spacing.l))
    }
}

@Composable
private fun CardHead(card: TrainingCardUi, position: Int, total: Int, box: @Composable () -> Unit) {
    val colors = LexaTheme.colors
    val type = LexaTheme.type
    Box(Modifier.heightIn(min = Spacing.touch), contentAlignment = Alignment.CenterStart) {
        Text(stringResource(R.string.training_progress, position, total), style = type.meta, color = colors.pencil)
    }
    Spacer(Modifier.height(Spacing.l))
    Text(card.prompt, style = type.word, color = colors.ink)
    if (card.ipa != null) {
        Spacer(Modifier.height(Spacing.s))
        Text(card.ipa, style = type.meta, color = colors.pencil)
    }
    Spacer(Modifier.height(Spacing.m))
    box()
    Spacer(Modifier.height(Spacing.xl))
}

@Composable
private fun QuestionCard(phase: TrainingPhase.Question, actions: TrainingActions) {
    val colors = LexaTheme.colors
    val type = LexaTheme.type
    val focus = remember { FocusRequester() }
    val label = stringResource(R.string.training_answer_label)
    CardHead(phase.card, phase.position, phase.total) { BoxIndicator(phase.card.box) }
    BasicTextField(
        value = phase.answer,
        onValueChange = actions.onAnswerChange,
        singleLine = true,
        textStyle = type.title.copy(color = colors.ink),
        cursorBrush = SolidColor(colors.ink),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { if (phase.canCheck) actions.onCheck() }),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focus)
            .semantics { contentDescription = label },
        decorationBox = { field ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .background(colors.sheet, LexaCorner)
                    .padding(horizontal = Spacing.m),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (phase.answer.isEmpty()) {
                    Text(
                        stringResource(if (phase.card.expectsEnglish) R.string.training_hint_english else R.string.training_hint_russian),
                        style = type.title,
                        color = colors.pencil,
                    )
                }
                field()
            }
        },
    )
    Spacer(Modifier.height(Spacing.l))
    MarkerButton(stringResource(R.string.training_check), onClick = actions.onCheck, enabled = phase.canCheck)
    LaunchedEffect(phase.position) { runCatching { focus.requestFocus() } }
}

@Composable
private fun FeedbackCard(phase: TrainingPhase.Feedback, actions: TrainingActions) {
    val colors = LexaTheme.colors
    val type = LexaTheme.type
    val haptics = LocalHapticFeedback.current
    val correct = phase.verdict != Verdict.WRONG
    // The marker settles into the box the word moved to; a wrong answer shows box 1 without motion.
    val landing = remember(phase.position, phase.verdict) { Animatable(if (correct) 0f else 1f) }
    LaunchedEffect(phase.position, phase.verdict) {
        haptics.performHapticFeedback(if (correct) HapticFeedbackType.Confirm else HapticFeedbackType.Reject)
        landing.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
    }
    CardHead(phase.card, phase.position, phase.total) {
        BoxIndicator(phase.boxAfter, markerProgress = landing.value)
    }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        if (phase.verdict == Verdict.WRONG) {
            val wrongDescription = stringResource(R.string.training_your_answer_wrong, phase.answer)
            Text(
                phase.answer,
                style = type.title.copy(textDecoration = TextDecoration.LineThrough),
                color = colors.redpen,
                modifier = Modifier.semantics { contentDescription = wrongDescription },
            )
            Text(phase.correctAnswer, style = type.title, color = colors.ink)
        } else {
            Text(phase.answer, style = type.title, color = colors.ink)
            if (phase.verdict == Verdict.TYPO) {
                Text(
                    "${stringResource(R.string.training_typo)} ${phase.correctAnswer}",
                    style = type.body,
                    color = colors.ink,
                )
            }
            if (phase.verdict == Verdict.OVERRIDDEN) {
                Text(phase.correctAnswer, style = type.body, color = colors.ink)
            }
        }
        Text(statusLine(phase), style = type.meta, color = colors.pencil)
    }

    if (phase.verdict == Verdict.WRONG) {
        phase.example?.let {
            Spacer(Modifier.height(Spacing.l))
            ExampleLines(it)
        }
        Spacer(Modifier.height(Spacing.m))
        Box(
            Modifier
                .heightIn(min = Spacing.touch)
                .clickable(role = Role.Button, onClick = actions.onOverride),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                stringResource(R.string.training_override),
                style = type.body.copy(textDecoration = TextDecoration.Underline),
                color = colors.ink,
            )
        }
    }
    Spacer(Modifier.height(if (phase.verdict == Verdict.WRONG) Spacing.m else Spacing.l))
    MarkerButton(stringResource(R.string.training_next), onClick = actions.onNext)
}

@Composable
private fun statusLine(phase: TrainingPhase.Feedback): String = when (phase.verdict) {
    Verdict.WRONG -> stringResource(R.string.training_back_to_new)
    Verdict.OVERRIDDEN ->
        if (phase.learned) stringResource(R.string.training_overridden_learned)
        else stringResource(R.string.training_overridden_box, phase.boxAfter)
    Verdict.CORRECT, Verdict.TYPO ->
        if (phase.learned) stringResource(R.string.training_correct_learned)
        else stringResource(R.string.training_correct_box, phase.boxAfter)
}

@Composable
private fun ExampleLines(example: Example) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(example.en, style = LexaTheme.type.body, color = LexaTheme.colors.ink)
        example.ru?.let { Text(it, style = LexaTheme.type.body, color = LexaTheme.colors.pencil) }
    }
}

@Composable
private fun SessionDone(phase: TrainingPhase.Done, actions: TrainingActions) {
    Spacer(Modifier.height(Spacing.touch + Spacing.l))
    Text(
        pluralStringResource(R.plurals.training_score, phase.total, phase.correct, phase.total),
        style = LexaTheme.type.heading,
        color = LexaTheme.colors.ink,
    )
    Spacer(Modifier.height(Spacing.s))
    val wrong = phase.total - phase.correct
    Text(
        if (wrong > 0) pluralStringResource(R.plurals.training_returned, wrong, wrong)
        else stringResource(R.string.training_all_moved),
        style = LexaTheme.type.body,
        color = LexaTheme.colors.pencil,
    )
    Spacer(Modifier.height(Spacing.l))
    MarkerButton(stringResource(R.string.training_finish), onClick = actions.onFinish)
}
