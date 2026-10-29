package app.lexa

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lexa.ui.share.SharedText
import app.lexa.ui.share.SheetActions
import app.lexa.ui.share.SheetContent
import app.lexa.ui.share.TranslateSheetContent
import app.lexa.ui.theme.LexaTheme
import app.lexa.ui.translator.TranslatorViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * «Перевести в Lexa» in the text selection menu and «Поделиться»: a bottom sheet over the calling app
 * (docs/ARCHITECTURE.md §12). Read-only for PROCESS_TEXT: no result is set, the selected text stays as is.
 * The word is added to «Новые» by the same TranslatorViewModel as the «Перевод» tab.
 */
@AndroidEntryPoint
class TranslateActivity : ComponentActivity() {
    private val viewModel: TranslatorViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val shared = SharedText.from(
            intent.action,
            intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT),
            intent.getCharSequenceExtra(Intent.EXTRA_TEXT),
        )
        // On rotation the view model keeps the lookup: translate (and add) only once.
        if (savedInstanceState == null && shared is SharedText.Query) viewModel.translate(shared.text)

        setContent {
            LexaTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                val scope = rememberCoroutineScope()
                val close: () -> Unit = { scope.launch { sheetState.hide() }.invokeOnCompletion { finish() } }
                val content = if (shared is SharedText.Query) SheetContent.Lookup(state.phase) else SheetContent.NotAWord(shared)
                Box(Modifier.fillMaxSize()) {
                    ModalBottomSheet(
                        onDismissRequest = ::finish,
                        sheetState = sheetState,
                        shape = RoundedCornerShape(topStart = SheetCorner, topEnd = SheetCorner),
                        containerColor = LexaTheme.colors.paper,
                        contentColor = LexaTheme.colors.ink,
                    ) {
                        TranslateSheetContent(
                            content = content,
                            actions = SheetActions(
                                onOpenInApp = { id ->
                                    startActivity(MainActivity.openWordIntent(this@TranslateActivity, id))
                                    finish()
                                },
                                onClose = close,
                                onToggleFavorite = viewModel::onToggleFavorite,
                                onPronounce = viewModel::onPronounce,
                                onUndoAdd = viewModel::onUndoAdd,
                                onTranslateQuery = viewModel::translate,
                            ),
                        )
                    }
                }
            }
        }
    }

    private companion object {
        val SheetCorner = app.lexa.ui.theme.LexaCornerRadius
    }
}
