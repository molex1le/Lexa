package app.lexa

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import app.lexa.ui.navigation.LaunchTarget
import app.lexa.ui.navigation.LexaNavHost
import app.lexa.ui.theme.LexaTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    /** Where an outside entry (reminder, «Открыть в Lexa») asked to go; cleared once the nav host has gone there. */
    private val launchTarget = mutableStateOf<LaunchTarget?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Transparent system bars: the paper of the screen shows through edge to edge.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) launchTarget.value = intent.launchTarget()
        setContent {
            LexaTheme {
                LexaNavHost(launchTarget = launchTarget.value, onLaunchHandled = { launchTarget.value = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.launchTarget()?.let { launchTarget.value = it }
    }

    private fun Intent.launchTarget(): LaunchTarget? = when {
        hasExtra(EXTRA_WORD_ID) -> LaunchTarget.Word(getLongExtra(EXTRA_WORD_ID, 0))
        getBooleanExtra(EXTRA_TRAINING, false) -> LaunchTarget.Training
        else -> null
    }

    companion object {
        private const val EXTRA_WORD_ID = "app.lexa.extra.WORD_ID"
        private const val EXTRA_TRAINING = "app.lexa.extra.TRAINING"

        private fun base(context: Context) = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        /** The reminder notification: straight to the «Тренировка» tab. */
        fun openTrainingIntent(context: Context): Intent = base(context).putExtra(EXTRA_TRAINING, true)

        /** «Открыть в Lexa» from the translate sheet: the word card. */
        fun openWordIntent(context: Context, wordId: Long): Intent = base(context).putExtra(EXTRA_WORD_ID, wordId)
    }
}
