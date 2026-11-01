package app.lexa.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import app.lexa.R
import app.lexa.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import app.lexa.ui.theme.LexaTheme
import app.lexa.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UpdateUiState(
    val release: Release? = null,
    /** 0..1 while the APK downloads; null otherwise. */
    val progress: Float? = null,
    val failed: Boolean = false,
    /** A check asked for in the settings is running. */
    val checking: Boolean = false,
    /** The last check found nothing newer than the installed version. */
    val upToDate: Boolean = false,
)

/**
 * Checks GitHub once per launch (unless the learner turned that off); the dialog shows only when a newer
 * version exists. The settings screen shares this view model through the activity and can ask for a check.
 */
@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val updater: AppUpdater,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(UpdateUiState())
    val state: StateFlow<UpdateUiState> = _state

    val installedVersion: String get() = updater.installedVersion

    init {
        viewModelScope.launch {
            if (settings.settings.first().updateCheck) updater.newerRelease()?.let { r -> _state.update { it.copy(release = r) } }
        }
    }

    /** «Проверить обновления» in the settings: always asks GitHub, whatever the automatic check is set to. */
    fun checkNow() {
        if (_state.value.checking) return
        _state.update { it.copy(checking = true, upToDate = false) }
        viewModelScope.launch {
            val release = updater.newerRelease()
            _state.update { it.copy(checking = false, release = release ?: it.release, upToDate = release == null) }
        }
    }

    fun update() {
        val release = _state.value.release ?: return
        if (_state.value.progress != null) return
        _state.update { it.copy(progress = 0f, failed = false) }
        viewModelScope.launch {
            val apk = runCatching { updater.download(release) { p -> _state.update { it.copy(progress = p) } } }.getOrNull()
            _state.update { it.copy(progress = null, failed = apk == null) }
            if (apk != null) updater.install(apk)
        }
    }

    fun later() = _state.update { UpdateUiState() }
}

@Composable
fun UpdatePrompt(viewModel: UpdateViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val release = state.release ?: return
    val colors = LexaTheme.colors
    val type = LexaTheme.type
    val downloading = state.progress != null
    AlertDialog(
        onDismissRequest = { if (!downloading) viewModel.later() },
        containerColor = colors.paper,
        title = { Text(stringResource(R.string.update_title, release.version), style = type.title, color = colors.ink) },
        text = {
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                Text(release.notes.ifBlank { stringResource(R.string.update_notes_default) }, style = type.body, color = colors.ink)
                if (downloading) {
                    Spacer(Modifier.height(Spacing.m))
                    LinearProgressIndicator(
                        progress = { state.progress ?: 0f },
                        color = colors.ink,
                        trackColor = colors.sheet,
                    )
                }
                if (state.failed) {
                    Spacer(Modifier.height(Spacing.m))
                    Text(stringResource(R.string.update_failed), style = type.body, color = colors.redpen)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = viewModel::update, enabled = !downloading) {
                Text(
                    stringResource(if (downloading) R.string.update_downloading else R.string.update_install),
                    style = type.body.copy(fontWeight = FontWeight(600)),
                    color = colors.ink,
                )
            }
        },
        dismissButton = {
            if (!downloading) {
                TextButton(onClick = viewModel::later) {
                    Text(stringResource(R.string.update_later), style = type.body, color = colors.pencil)
                }
            }
        },
    )
}
