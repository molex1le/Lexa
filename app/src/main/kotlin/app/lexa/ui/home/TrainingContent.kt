package app.lexa.ui.home

import app.lexa.domain.training.DailyProgress
import app.lexa.ui.components.LexaIconButton

import androidx.compose.foundation.layout.width

import androidx.compose.foundation.layout.Row

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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import app.lexa.ui.components.FitWordText
import app.lexa.ui.components.markedTextColor
import app.lexa.ui.components.markerHighlight
import app.lexa.ui.components.rememberReducedMotion
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

/** GAVE_UP: «Не знаю». ALSO_RIGHT: another right English word in RU→EN – no credit, no penalty. */
enum class Verdict { CORRECT, TYPO, WRONG, OVERRIDDEN, GAVE_UP, ALSO_RIGHT }

/** The one short celebration a session may have (CLAUDE.md §3 «Движение»). */
sealed interface Celebration {
    data object Learned : Celebration
    data class StreakRecord(val days: Int) : Celebration
}

/** The training state machine shown on the «Тренировка» tab while a session runs. */
sealed interface TrainingPhase {
    data class Question(
        val card: TrainingCardUi,
        val position: Int,
        val total: Int,
        val answer: String = "",
        /** «j _ _ _ _ _ _» once «Подсказка» was tapped. */
        val hint: String? = null,
        /** The word was answered wrong earlier: this card repeats it at the end of the session. */
        val retry: Boolean = false,
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
        /** Set on at most one answer per session. */
        val celebration: Celebration? = null,
        /** A built-in word: wrong adds it to «Новые», right means it is known and never shown again. */
        val fromDeck: Boolean = false,
        val hinted: Boolean = false,
        /** Days until the word comes back; null when it is in no box (learned, known, removed). */
        val nextReviewDays: Int? = null,
        /** ALSO_RIGHT: the English word being learned. */
        val alsoRight: String? = null,
        /** «Убрать из изучения» / «Я это знаю» was used on this card. */
        val removed: Boolean = false,
        /** A retry: nothing was written. */
        val retry: Boolean = false,
        /** A wrong retry that will come round once more. */
        val retryAgain: Boolean = false,
    ) : TrainingPhase

    data class Done(val correct: Int, val total: Int, val daily: DailyProgress? = null) : TrainingPhase
}

/** Session callbacks, grouped so the stateless screen stays readable. */
class TrainingActions(
    val onAnswerChange: (String) -> Unit = {},
    val onCheck: () -> Unit = {},
    val onOverride: () -> Unit = {},
    val onNext: () -> Unit = {},
    val onFinish: () -> Unit = {},
    /** A wrong answer sent the word back to «Новые»: the «Слова» tab pulses once. */
    val onWordReturned: () -> Unit = {},
    val haptics: Boolean = true,
    val onHint: () -> Unit = {},
    val onDontKnow: () -> Unit = {},
    val onPronounce: () -> Unit = {},
    val onRemove: () -> Unit = {},
    val onMarkKnown: () -> Unit = {},
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
private fun Progress(position: Int, total: Int, retry: Boolean = false) {
    Box(Modifier.heightIn(min = Spacing.touch), contentAlignment = Alignment.CenterStart) {
        Text(
            stringResource(if (retry) R.string.training_progress_retry else R.string.training_progress, position, total),
            style = LexaTheme.type.meta,
            color = LexaTheme.colors.pencil,
        )
    }
    Spacer(Modifier.height(Spacing.l))
}

/**
 * The face of the card: the word, its IPA and the box. [rotationY] turns it around the vertical axis
 * (the flip when feedback appears); [term] may decorate the word, e.g. with the flying copy.
 */
@Composable
private fun CardFace(
    card: TrainingCardUi,
    rotationY: Float = 0f,
    term: @Composable (@Composable () -> Unit) -> Unit = { it() },
    onSpeak: (() -> Unit)? = null,
    box: @Composable () -> Unit,
) {
    val colors = LexaTheme.colors
    val type = LexaTheme.type
    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                this.rotationY = rotationY
                cameraDistance = 16f * density
            },
    ) {
        term { FitWordText(card.prompt, style = type.word, color = colors.ink, modifier = Modifier.fillMaxWidth()) }
        if (card.ipa != null || onSpeak != null) {
            Spacer(Modifier.height(Spacing.s))
            Row(verticalAlignment = Alignment.CenterVertically) {
                card.ipa?.let { Text(it, style = type.meta, color = colors.pencil) }
                if (onSpeak != null) {
                    if (card.ipa != null) Spacer(Modifier.width(Spacing.s))
                    LexaIconButton(R.drawable.ic_sound, stringResource(R.string.action_pronounce), onSpeak)
                }
            }
        }
        Spacer(Modifier.height(Spacing.m))
        box()
    }
    Spacer(Modifier.height(Spacing.xl))
}

@Composable
private fun QuestionCard(phase: TrainingPhase.Question, actions: TrainingActions) {
    val colors = LexaTheme.colors
    val type = LexaTheme.type
    val focus = remember { FocusRequester() }
    val label = stringResource(R.string.training_answer_label)
    Progress(phase.position, phase.total, phase.retry)
    // The English word may be heard while answering; in RU→EN the sound would give the answer away.
    CardFace(phase.card, onSpeak = actions.onPronounce.takeUnless { phase.card.expectsEnglish }) { CardBoxes(phase.card.box) }
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
    phase.hint?.let {
        Spacer(Modifier.height(Spacing.s))
        Text(stringResource(R.string.training_hint_shown, it), style = type.body, color = colors.pencil)
    }
    Spacer(Modifier.height(Spacing.l))
    MarkerButton(stringResource(R.string.training_check), onClick = actions.onCheck, enabled = phase.canCheck)
    Spacer(Modifier.height(Spacing.s))
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.l)) {
        if (phase.hint == null) TextAction(stringResource(R.string.training_hint), actions.onHint)
        TextAction(stringResource(R.string.training_dont_know), actions.onDontKnow)
    }
    LaunchedEffect(phase.position) { runCatching { focus.requestFocus() } }
}

@Composable
private fun FeedbackCard(phase: TrainingPhase.Feedback, actions: TrainingActions) {
    val colors = LexaTheme.colors
    val type = LexaTheme.type
    val haptics = LocalHapticFeedback.current
    val reduced = rememberReducedMotion()
    val wrong = phase.verdict == Verdict.WRONG || phase.verdict == Verdict.GAVE_UP
    val correct = !wrong
    // The card turns over (0→180°), then the marker slides from the old box into the new one with a small
    // overshoot; a wrong answer shows box 1 without a slide, and a copy of the word flies off to «Слова».
    // What already played for this card survives rotation, so turning the phone does not replay the
    // flip, the haptic or the flight.
    var played by rememberSaveable(phase.position) { mutableStateOf<Verdict?>(null) }
    var returned by rememberSaveable(phase.position) { mutableStateOf(false) }
    val still = reduced || played != null
    val flip = remember(phase.position) { Animatable(if (still) 180f else 0f) }
    val markerAt = remember(phase.position) {
        Animatable(if (correct && !still) phase.card.box.toFloat() else phase.boxAfter.toFloat())
    }
    val flight = remember(phase.position) { Animatable(if (returned && wrong && !phase.retry) 1f else 0f) }
    val celebrate = remember(phase.position) { Animatable(if (still) 1f else 0f) }
    LaunchedEffect(phase.position, phase.verdict) {
        if (played == phase.verdict) return@LaunchedEffect
        played = phase.verdict
        if (actions.haptics) haptics.performHapticFeedback(if (correct) HapticFeedbackType.Confirm else HapticFeedbackType.Reject)
        flip.animateTo(180f, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMedium))
        val box = phase.boxAfter.toFloat()
        if (reduced) markerAt.snapTo(box)
        else markerAt.animateTo(box, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
        if (wrong && !returned && !phase.retry) {
            returned = true
            if (!reduced) flight.animateTo(1f, spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow))
            actions.onWordReturned()
        }
        // «Я знал, это опечатка»: the word did not go back after all – the copy flies home again.
        if (phase.verdict == Verdict.OVERRIDDEN && flight.value > 0f) {
            if (reduced) flight.snapTo(0f)
            else flight.animateTo(0f, spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow))
        }
        if (phase.celebration != null && !reduced) {
            celebrate.animateTo(1f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow))
        }
    }
    Progress(phase.position, phase.total, phase.retry)
    val angle = flip.value
    if (angle < 90f) {
        // Front: the question as it was, turning away. Text is never shown mirrored.
        CardFace(phase.card, rotationY = angle) { CardBoxes(phase.card.box) }
    } else {
        CardFace(
            phase.card,
            rotationY = angle - 180f,
            term = { word -> FlyingWord(phase.card.prompt, flight.value, word) },
            onSpeak = actions.onPronounce,
        ) {
            if (phase.boxAfter > 0) BoxIndicator(phase.boxAfter, markerAt = markerAt.value) else CardBoxes(0)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        if (wrong) {
            if (phase.answer.isNotBlank()) {
                val wrongDescription = stringResource(R.string.training_your_answer_wrong, phase.answer)
                Text(
                    phase.answer,
                    style = type.title.copy(textDecoration = TextDecoration.LineThrough),
                    color = colors.redpen,
                    modifier = Modifier.semantics { contentDescription = wrongDescription },
                )
            }
            Text(phase.correctAnswer, style = type.title, color = colors.ink)
        } else if (phase.verdict == Verdict.ALSO_RIGHT) {
            Text(phase.answer, style = type.title, color = colors.ink)
            Text(stringResource(R.string.training_also_right, phase.alsoRight.orEmpty()), style = type.body, color = colors.ink)
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
        when (val celebration = phase.celebration) {
            null -> {
                Text(statusLine(phase), style = type.meta, color = colors.pencil)
                nextReviewLine(phase)?.let { Text(it, style = type.meta, color = colors.pencil) }
            }
            Celebration.Learned -> CelebrationLine(statusLine(phase), celebrate.value)
            is Celebration.StreakRecord -> {
                Text(statusLine(phase), style = type.meta, color = colors.pencil)
                CelebrationLine(
                    stringResource(
                        R.string.training_streak_record,
                        pluralStringResource(R.plurals.days, celebration.days, celebration.days),
                    ),
                    celebrate.value,
                )
            }
        }
    }

    if (wrong) {
        phase.example?.let {
            Spacer(Modifier.height(Spacing.l))
            ExampleLines(it)
        }
    }
    Spacer(Modifier.height(Spacing.m))
    if (phase.removed) {
        Text(stringResource(R.string.training_removed), style = type.body, color = colors.pencil)
    } else {
        if (phase.verdict == Verdict.WRONG && !phase.retry) TextAction(stringResource(R.string.training_override), actions.onOverride)
        // A built-in word answered right is already gone from training: nothing to remove.
        if (!(phase.fromDeck && correct)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.l)) {
                TextAction(stringResource(R.string.training_mark_known), actions.onMarkKnown)
                TextAction(stringResource(R.string.training_remove), actions.onRemove)
            }
        }
    }
    Spacer(Modifier.height(Spacing.m))
    MarkerButton(stringResource(R.string.training_next), onClick = actions.onNext)
}

/** A secondary action: underlined ink text with a 48dp target; the marker stays on the main button. */
@Composable
private fun TextAction(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = Spacing.touch)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(label, style = LexaTheme.type.body.copy(textDecoration = TextDecoration.Underline), color = LexaTheme.colors.ink)
    }
}

/** «Повторим через 3 дня» under a word that stays in the boxes. */
@Composable
private fun nextReviewLine(phase: TrainingPhase.Feedback): String? {
    val days = phase.nextReviewDays ?: return null
    if (phase.removed || phase.verdict == Verdict.ALSO_RIGHT) return null
    return if (days == 0) stringResource(R.string.training_next_review_today)
    else stringResource(R.string.training_next_review, pluralStringResource(R.plurals.days, days, days))
}

/**
 * The word, plus a copy of it that shrinks and slides toward the «Слова» tab and fades out, as
 * [progress] goes 0→1. The copy is drawn only while it moves; the word itself never leaves.
 */
@Composable
private fun FlyingWord(term: String, progress: Float, word: @Composable () -> Unit) {
    val view = LocalView.current
    val tabOffset = with(LocalDensity.current) { 32.dp.toPx() }
    var center by remember { mutableStateOf(Offset.Zero) }
    Box(Modifier.onGloballyPositioned { center = it.boundsInRoot().center }) {
        word()
        if (progress > 0f && progress < 1f) {
            // «Слова» is the third of four tabs: its centre is at 5/8 of the width, at the bottom.
            val dx = view.width * 5f / 8f - center.x
            val dy = view.height - tabOffset - center.y
            Text(
                term,
                style = LexaTheme.type.word,
                color = LexaTheme.colors.ink,
                maxLines = 1,
                modifier = Modifier
                    .clearAndSetSemantics {}
                    .graphicsLayer {
                        translationX = dx * progress
                        translationY = dy * progress
                        val scale = 1f - 0.8f * progress
                        scaleX = scale
                        scaleY = scale
                        alpha = 1f - progress
                    },
            )
        }
    }
}

/** The celebration: a marker sweeps across the line left to right while it settles from a slight zoom. */
@Composable
private fun CelebrationLine(text: String, progress: Float) {
    Text(
        text,
        style = LexaTheme.type.title,
        color = markedTextColor(),
        modifier = Modifier
            .graphicsLayer {
                val scale = 1.08f - 0.08f * progress
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0.5f)
            }
            .markerHighlight(progress),
    )
}

/** A built-in word (box 0) is in no box yet: it says so instead of showing five empty slots. */
@Composable
private fun CardBoxes(box: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (box > 0) BoxIndicator(box)
        else Text(stringResource(R.string.training_deck_word), style = LexaTheme.type.meta, color = LexaTheme.colors.pencil)
        Spacer(Modifier.width(Spacing.m))
        BoxesExplainer()
    }
}

/** «Что за коробки?» – the Leitner rules in plain words, in a dialog. */
@Composable
private fun BoxesExplainer() {
    var open by rememberSaveable { mutableStateOf(false) }
    val colors = LexaTheme.colors
    Box(
        Modifier.heightIn(min = Spacing.touch).clickable(role = Role.Button) { open = true },
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            stringResource(R.string.boxes_what),
            style = LexaTheme.type.meta.copy(textDecoration = TextDecoration.Underline),
            color = colors.pencil,
        )
    }
    if (open) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { open = false },
            containerColor = colors.paper,
            title = { Text(stringResource(R.string.boxes_title), style = LexaTheme.type.title, color = colors.ink) },
            text = { Text(stringResource(R.string.boxes_explained), style = LexaTheme.type.body, color = colors.ink) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { open = false }) {
                    Text(stringResource(R.string.boxes_ok), style = LexaTheme.type.body, color = colors.ink)
                }
            },
        )
    }
}

@Composable
private fun statusLine(phase: TrainingPhase.Feedback): String = when {
    phase.removed -> ""
    phase.retry && phase.verdict == Verdict.ALSO_RIGHT -> stringResource(R.string.training_also_right_status)
    phase.retry && (phase.verdict == Verdict.WRONG || phase.verdict == Verdict.GAVE_UP) ->
        stringResource(if (phase.retryAgain) R.string.training_retry_again else R.string.training_retry_left)
    phase.retry -> stringResource(R.string.training_retry_right)
    phase.verdict == Verdict.ALSO_RIGHT -> stringResource(R.string.training_also_right_status)
    phase.fromDeck && phase.hinted && phase.verdict != Verdict.WRONG -> stringResource(R.string.training_deck_added_hint)
    phase.hinted && phase.verdict != Verdict.WRONG -> stringResource(R.string.training_hinted_status)
    phase.fromDeck && (phase.verdict == Verdict.WRONG || phase.verdict == Verdict.GAVE_UP) -> stringResource(R.string.training_deck_added)
    phase.fromDeck -> stringResource(R.string.training_deck_known)
    else -> userWordStatus(phase)
}

@Composable
private fun userWordStatus(phase: TrainingPhase.Feedback): String = when (phase.verdict) {
    Verdict.WRONG, Verdict.GAVE_UP -> stringResource(R.string.training_back_to_new)
    Verdict.ALSO_RIGHT -> stringResource(R.string.training_also_right_status)
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
    phase.daily?.let { daily ->
        Spacer(Modifier.height(Spacing.s))
        Text(
            if (daily.reached) stringResource(R.string.goal_reached, daily.done, daily.goal)
            else stringResource(R.string.goal_today, daily.done, daily.goal),
            style = LexaTheme.type.body,
            color = LexaTheme.colors.pencil,
        )
    }
    Spacer(Modifier.height(Spacing.l))
    MarkerButton(stringResource(R.string.training_finish), onClick = actions.onFinish)
}
