package app.lexa.ui.translator

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.lexa.domain.repository.DictionaryRepository
import app.lexa.domain.repository.HistoryRepository
import app.lexa.domain.repository.LookupProgress
import app.lexa.domain.repository.LookupResult
import app.lexa.domain.repository.TranslatedWordRepository
import app.lexa.ui.navigation.TranslatorRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class TranslatorViewModel @Inject constructor(
    private val dictionary: DictionaryRepository,
    private val words: TranslatedWordRepository,
    private val history: HistoryRepository,
    private val pronouncer: Pronouncer,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val local = MutableStateFlow(TranslatorUiState())
    private var lookupJob: Job? = null

    /** The wait after the last keystroke; a paste or a long jump in the text translates at once. */
    private var autoJob: Job? = null

    /** The word an automatic lookup added to «Новые», until the learner types on past it. */
    private var autoAdded: AutoAdded? = null

    private data class AutoAdded(val wordId: Long, val query: String)

    val state: StateFlow<TranslatorUiState> = combine(local, history.observeHistory()) { s, items ->
        s.copy(recent = items.distinctBy { it.query.lowercase() }.take(RECENT_LIMIT))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TranslatorUiState())

    init {
        // A query from the route (history, later «Поделиться») translates at once.
        runCatching { savedStateHandle.toRoute<TranslatorRoute>().query }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let(::translate)
        // History screen hands a picked query back through this handle.
        viewModelScope.launch {
            savedStateHandle.getStateFlow<String?>(PICKED_QUERY, null).collect { picked ->
                if (!picked.isNullOrBlank()) {
                    savedStateHandle[PICKED_QUERY] = null
                    translate(picked)
                }
            }
        }
    }

    /**
     * Typing translates by itself: [AUTO_DELAY_MS] after the last keystroke, at once after a paste
     * (more than one character changed). A word added on the way to a longer one («car» on the way to
     * «carpet») is taken out of «Новые» again.
     */
    fun onQueryChange(value: String) {
        val previous = local.value.query
        local.update { it.copy(query = value) }
        autoJob?.cancel()
        val text = value.trim()
        if (text.isEmpty()) {
            lookupJob?.cancel()
            local.update { it.copy(phase = TranslatorPhase.Idle) }
            return
        }
        dropWordOnTheWay(text)
        val current = local.value.phase
        if (current is TranslatorPhase.Result && current.query == text) return
        val pasted = abs(value.length - previous.length) > 1
        autoJob = viewModelScope.launch {
            if (!pasted) delay(AUTO_DELAY_MS)
            lookup(value, auto = true)
        }
    }

    /** The keyboard's «search» key: no waiting. */
    fun onTranslate() = translate(local.value.query)

    fun translate(query: String) {
        autoJob?.cancel()
        lookup(query, auto = false)
    }

    private fun dropWordOnTheWay(text: String) {
        val added = autoAdded ?: return
        if (text.length > added.query.length && text.lowercase().startsWith(added.query)) {
            autoAdded = null
            viewModelScope.launch { words.undoAdd(added.wordId) }
            val phase = local.value.phase
            if (phase is TranslatorPhase.Result && phase.wordId == added.wordId) {
                local.update { it.copy(phase = phase.copy(wordId = null, justAdded = false, isFavorite = false), snackbarWordId = null) }
            }
        }
    }

    private fun lookup(query: String, auto: Boolean) {
        if (query.isBlank()) return
        lookupJob?.cancel()
        local.update { it.copy(query = query, phase = TranslatorPhase.Loading(downloadingModel = false)) }
        lookupJob = viewModelScope.launch {
            val result = dictionary.lookup(query) { progress ->
                if (progress == LookupProgress.DOWNLOADING_MODEL) {
                    local.update { it.copy(phase = TranslatorPhase.Loading(downloadingModel = true)) }
                }
            }
            when (result) {
                is LookupResult.Failed -> local.update { it.copy(phase = TranslatorPhase.Error(result.error)) }
                is LookupResult.Found -> {
                    val added = words.addFromTranslation(result.translation)
                    history.record(query, added.wordId)
                    autoAdded = if (auto && added.isNew) AutoAdded(added.wordId, query.trim().lowercase()) else null
                    local.update {
                        it.copy(
                            phase = TranslatorPhase.Result(
                                translation = result.translation,
                                query = query.trim(),
                                wordId = added.wordId,
                                isFavorite = added.isFavorite,
                                justAdded = added.isNew,
                            ),
                            snackbarWordId = if (added.isNew) added.wordId else null,
                        )
                    }
                }
            }
        }
    }

    fun onSnackbarShown() = local.update { it.copy(snackbarWordId = null) }

    /** «Отменить»: deletes the word only if this lookup created it. */
    fun onUndoAdd(wordId: Long) {
        val result = local.value.phase as? TranslatorPhase.Result ?: return
        if (!result.justAdded || result.wordId != wordId) return
        if (autoAdded?.wordId == wordId) autoAdded = null
        viewModelScope.launch {
            words.undoAdd(wordId)
            local.update { it.copy(phase = result.copy(wordId = null, justAdded = false, isFavorite = false)) }
        }
    }

    fun onToggleFavorite() {
        val result = local.value.phase as? TranslatorPhase.Result ?: return
        val id = result.wordId ?: return
        val favorite = !result.isFavorite
        local.update { it.copy(phase = result.copy(isFavorite = favorite)) }
        viewModelScope.launch { words.setFavorite(id, favorite) }
    }

    fun onPronounce() {
        val result = local.value.phase as? TranslatorPhase.Result ?: return
        pronouncer.play(result.translation.term, result.translation.audioUrl)
    }

    override fun onCleared() {
        pronouncer.release()
    }

    companion object {
        const val PICKED_QUERY = "picked_query"
        private const val RECENT_LIMIT = 5

        /** How long typing must pause before the word is translated (and added to «Новые»). */
        const val AUTO_DELAY_MS = 1_200L
    }
}
