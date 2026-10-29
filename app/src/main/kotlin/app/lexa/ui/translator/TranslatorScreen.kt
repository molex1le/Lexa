package app.lexa.ui.translator

import android.provider.Settings
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.graphics.lerp
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
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lexa.R
import app.lexa.domain.model.Example
import app.lexa.domain.model.ExampleOrigin
import app.lexa.domain.model.HistoryItem
import app.lexa.domain.model.PartOfSpeech
import app.lexa.domain.model.Translation
import app.lexa.domain.repository.LookupError
import app.lexa.ui.components.LexaIconButton
import app.lexa.ui.components.MarkerButton
import app.lexa.ui.components.ScreenHeader
import app.lexa.ui.components.markedTextColor
import app.lexa.ui.components.markerHighlight
import app.lexa.ui.theme.LexaCorner
import app.lexa.ui.theme.LexaTheme
import app.lexa.ui.theme.Spacing

sealed interface TranslatorPhase {
    data object Idle : TranslatorPhase
    data class Loading(val downloadingModel: Boolean) : TranslatorPhase
    data class Error(val error: LookupError) : TranslatorPhase
    /** [wordId] is null after «Отменить»; [justAdded] – this lookup created the word (marker draws in). */
    data class Result(
        val translation: Translation,
        val query: String,
        val wordId: Long?,
        val isFavorite: Boolean,
        val justAdded: Boolean,
    ) : TranslatorPhase
}

data class TranslatorUiState(
    val query: String = "",
    val recent: List<HistoryItem> = emptyList(),
    val phase: TranslatorPhase = TranslatorPhase.Idle,
    /** Set once per fresh add: the screen shows «Добавлено в новые слова» with «Отменить». */
    val snackbarWordId: Long? = null,
) {
    val canTranslate: Boolean
        get() = query.isNotBlank() &&
            phase !is TranslatorPhase.Loading &&
            !(phase is TranslatorPhase.Result && phase.query == query.trim())
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
        onTranslateQuery = viewModel::translate,
        onToggleFavorite = viewModel::onToggleFavorite,
        onPronounce = viewModel::onPronounce,
        onUndoAdd = viewModel::onUndoAdd,
        onSnackbarShown = viewModel::onSnackbarShown,
    )
}

/** «Перевод» tab: input on a sheet, one marker action, then the result, an error or recent queries. */
@Composable
fun TranslatorScreen(
    state: TranslatorUiState,
    onQueryChange: (String) -> Unit,
    onTranslate: () -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier,
    onTranslateQuery: (String) -> Unit = {},
    onToggleFavorite: () -> Unit = {},
    onPronounce: () -> Unit = {},
    onUndoAdd: (Long) -> Unit = {},
    onSnackbarShown: () -> Unit = {},
) {
    val colors = LexaTheme.colors
    val type = LexaTheme.type
    val snackbar = remember { SnackbarHostState() }
    val addedMessage = stringResource(R.string.translator_added)
    val undoLabel = stringResource(R.string.action_undo)
    LaunchedEffect(state.snackbarWordId) {
        val id = state.snackbarWordId ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(addedMessage, actionLabel = undoLabel, duration = SnackbarDuration.Long)
        onSnackbarShown()
        if (result == SnackbarResult.ActionPerformed) onUndoAdd(id)
    }
    Box(modifier.fillMaxSize().background(colors.paper)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
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
                QueryInput(state, onQueryChange, onTranslate)
                Spacer(Modifier.height(Spacing.m))
                MarkerButton(
                    text = stringResource(R.string.translator_action),
                    onClick = onTranslate,
                    enabled = state.canTranslate,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Spacing.l))
                when (val phase = state.phase) {
                    TranslatorPhase.Idle -> Recent(state.recent, onTranslateQuery)
                    is TranslatorPhase.Loading -> Text(
                        text = stringResource(
                            if (phase.downloadingModel) R.string.translator_downloading_model else R.string.translator_loading,
                        ),
                        style = type.body,
                        color = colors.pencil,
                    )
                    is TranslatorPhase.Error -> ErrorBlock(phase.error)
                    is TranslatorPhase.Result -> key(phase.translation.term, phase.wordId != null) {
                        ResultBlock(phase, onToggleFavorite, onPronounce, onTranslateQuery)
                    }
                }
                Spacer(Modifier.height(Spacing.xl))
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
private fun QueryInput(state: TranslatorUiState, onQueryChange: (String) -> Unit, onTranslate: () -> Unit) {
    val colors = LexaTheme.colors
    val type = LexaTheme.type
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
}

@Composable
private fun Recent(recent: List<HistoryItem>, onTranslateQuery: (String) -> Unit) {
    val colors = LexaTheme.colors
    val type = LexaTheme.type
    if (recent.isEmpty()) {
        Text(stringResource(R.string.translator_empty), style = type.body, color = colors.pencil)
        return
    }
    Text(stringResource(R.string.translator_recent), style = type.title, color = colors.ink)
    Spacer(Modifier.height(Spacing.s))
    recent.forEach { item ->
        Text(
            text = item.query,
            style = type.body,
            color = colors.ink,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Spacing.touch)
                .clickable { onTranslateQuery(item.query) }
                .padding(vertical = Spacing.s + Spacing.xs),
        )
    }
}

@Composable
private fun ErrorBlock(error: LookupError) {
    val (title, hint) = when (error) {
        LookupError.NO_NETWORK -> R.string.error_no_network to R.string.error_no_network_hint
        LookupError.MODEL_MISSING_OFFLINE -> R.string.error_model_offline to R.string.error_model_offline_hint
        LookupError.MODEL_DOWNLOAD_FAILED -> R.string.error_model_failed to R.string.error_model_failed_hint
        LookupError.NOTHING_FOUND -> R.string.error_nothing_found to R.string.error_nothing_found_hint
    }
    Text(stringResource(title), style = LexaTheme.type.title, color = LexaTheme.colors.ink)
    Spacer(Modifier.height(Spacing.s))
    Text(stringResource(hint), style = LexaTheme.type.body, color = LexaTheme.colors.pencil)
}

/** True when the system «Убрать анимацию» (animator duration scale 0) is on. */
@Composable
private fun rememberReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

@Composable
private fun ResultBlock(
    result: TranslatorPhase.Result,
    onToggleFavorite: () -> Unit,
    onPronounce: () -> Unit,
    onTranslateQuery: (String) -> Unit,
) {
    val colors = LexaTheme.colors
    val type = LexaTheme.type
    val t = result.translation
    val inList = result.wordId != null

    // The marker draws in under a freshly added word, left to right; an existing word is already marked.
    val reducedMotion = rememberReducedMotion()
    var target by remember { mutableFloatStateOf(if (result.justAdded && !reducedMotion) 0f else 1f) }
    LaunchedEffect(Unit) { target = 1f }
    val progress by animateFloatAsState(
        targetValue = if (inList) target else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 400f),
        label = "marker",
    )

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = t.term,
            style = type.word,
            // On the dark theme the ink turns dark only as the marker arrives, so no letter is ever dark on dark.
            color = if (inList) lerp(colors.ink, markedTextColor(), progress) else colors.ink,
            modifier = Modifier.weight(1f, fill = false).markerHighlight(progress),
        )
        if (inList) {
            LexaIconButton(
                icon = if (result.isFavorite) R.drawable.ic_star_filled else R.drawable.ic_star,
                contentDescription = stringResource(
                    if (result.isFavorite) R.string.action_remove_favorite else R.string.action_add_favorite,
                ),
                onClick = onToggleFavorite,
            )
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        t.ipa?.let { ipa ->
            Text(ipa, style = type.meta, color = colors.pencil)
        }
        LexaIconButton(R.drawable.ic_sound, stringResource(R.string.action_pronounce), onPronounce)
    }

    t.senses.forEach { sense ->
        Spacer(Modifier.height(Spacing.m))
        sense.pos?.let { pos ->
            Text(posName(pos), style = type.meta, color = colors.pencil)
        }
        if (sense.translations.isNotEmpty()) {
            Text(sense.translations.joinToString(", "), style = type.title, color = colors.ink)
        }
        sense.gloss?.let { gloss ->
            Text(gloss, style = type.body, color = colors.pencil)
        }
    }

    t.definition?.let { definition ->
        BlockTitle(R.string.translator_definition)
        Text(definition, style = type.body, color = colors.ink)
    }

    if (t.examples.isNotEmpty()) {
        BlockTitle(R.string.translator_examples)
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            t.examples.forEach { ExampleRow(it) }
        }
    }

    if (t.synonyms.isNotEmpty()) {
        BlockTitle(R.string.translator_synonyms)
        Synonyms(t.synonyms, onTranslateQuery)
    }
}

@Composable
internal fun BlockTitle(text: Int) {
    Spacer(Modifier.height(Spacing.l))
    Text(stringResource(text), style = LexaTheme.type.title, color = LexaTheme.colors.ink)
    Spacer(Modifier.height(Spacing.s))
}

@Composable
internal fun ExampleRow(example: Example) {
    val colors = LexaTheme.colors
    val type = LexaTheme.type
    Column {
        Text(example.en, style = type.body, color = colors.ink)
        example.ru?.let { Text(it, style = type.body, color = colors.pencil) }
        val note = when (example.origin) {
            ExampleOrigin.MACHINE -> R.string.translator_machine
            ExampleOrigin.TATOEBA -> R.string.translator_tatoeba
            ExampleOrigin.DICTIONARY -> null
        }
        if (note != null) Text(stringResource(note), style = type.meta, color = colors.pencil)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun Synonyms(synonyms: List<String>, onTranslateQuery: (String) -> Unit) {
    val colors = LexaTheme.colors
    val label = stringResource(R.string.action_translate_synonym)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        synonyms.forEach { synonym ->
            Box(
                Modifier
                    .heightIn(min = Spacing.touch)
                    .clip(LexaCorner)
                    .background(colors.sheet)
                    .clickable(onClickLabel = label) { onTranslateQuery(synonym) }
                    .padding(horizontal = Spacing.m),
                contentAlignment = Alignment.Center,
            ) {
                Text(synonym, style = LexaTheme.type.body.copy(fontWeight = FontWeight(500)), color = colors.ink)
            }
        }
    }
}

@Composable
internal fun posName(pos: PartOfSpeech): String = stringResource(
    when (pos) {
        PartOfSpeech.NOUN -> R.string.pos_noun
        PartOfSpeech.VERB -> R.string.pos_verb
        PartOfSpeech.ADJECTIVE -> R.string.pos_adjective
        PartOfSpeech.ADVERB -> R.string.pos_adverb
        PartOfSpeech.PRONOUN -> R.string.pos_pronoun
        PartOfSpeech.PREPOSITION -> R.string.pos_preposition
        PartOfSpeech.CONJUNCTION -> R.string.pos_conjunction
        PartOfSpeech.INTERJECTION -> R.string.pos_interjection
        PartOfSpeech.OTHER -> R.string.pos_other
    },
)
