package app.lexa.screenshots

import androidx.compose.runtime.Composable
import app.lexa.ui.history.HistoryScreen
import app.lexa.ui.history.HistoryUiState
import app.lexa.ui.home.HomeScreen
import app.lexa.ui.home.HomeUiState
import app.lexa.ui.home.TrainingCardUi
import app.lexa.ui.home.TrainingPhase
import app.lexa.ui.home.Verdict
import app.lexa.ui.lists.ListsScreen
import app.lexa.ui.lists.ListsUiState
import app.lexa.ui.navigation.LexaBottomBar
import app.lexa.ui.navigation.ListTab
import app.lexa.ui.navigation.TopLevel
import app.lexa.ui.settings.SettingsScreen
import app.lexa.ui.settings.SettingsUiState
import app.lexa.ui.stats.StatsScreen
import app.lexa.ui.stats.StatsUiState
import app.lexa.ui.theme.LexaTheme
import app.lexa.ui.theme.TypeSpecimen
import app.lexa.ui.translator.TranslatorScreen
import app.lexa.ui.translator.TranslatorPhase
import app.lexa.ui.translator.TranslatorUiState
import app.lexa.domain.model.Example
import app.lexa.domain.model.ExampleOrigin
import app.lexa.domain.model.PartOfSpeech
import app.lexa.domain.model.Sense
import app.lexa.domain.model.Translation
import app.lexa.domain.repository.LookupError
import org.junit.Assume.assumeFalse
import app.lexa.ui.word.WordScreen
import app.lexa.ui.word.WordUiState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private val SampleCommute = Translation(
    term = "commute",
    ipa = "/kəˈmjuːt/",
    audioUrl = "https://api.dictionaryapi.dev/media/pronunciations/en/commute-uk.mp3",
    senses = listOf(
        Sense(PartOfSpeech.VERB, listOf("ездить на работу"), "Регулярно ездить из дома на работу или учёбу и обратно."),
        Sense(PartOfSpeech.NOUN, listOf("поездка на работу"), "Регулярная поездка на работу или учёбу."),
    ),
    definition = "To regularly travel from one's home to one's workplace or school, or vice versa.",
    examples = listOf(
        Example("I commute to work by bicycle.", "Я езжу на работу на велосипеде.", ExampleOrigin.TATOEBA),
        Example("I commute from Brooklyn to Manhattan by bicycle.", "Я езжу из Бруклина на Манхэттен на велосипеде.", ExampleOrigin.MACHINE),
    ),
    synonyms = listOf("travel", "journey", "shuttle"),
)

/**
 * Every phase 1 screen in its empty state, light and dark, on a 360dp phone at font scale 1.0.
 * Screens are stateless, so no Hilt, Room or network is involved.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class ScreenshotTest(private val dark: Boolean) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun themes(): List<Array<Any>> = listOf(arrayOf(false), arrayOf(true))
    }

    private val suffix get() = if (dark) "dark" else "light"

    private fun snap(name: String, content: @Composable () -> Unit) {
        captureRoboImage("src/test/snapshots/${name}_$suffix.png") {
            LexaTheme(darkTheme = dark) { content() }
        }
    }

    /** A top-level screen with the bottom bar under it, as it sits in the app. */
    @Composable
    private fun WithBar(tab: TopLevel, screen: @Composable (Modifier) -> Unit) {
        Column(Modifier.fillMaxSize()) {
            screen(Modifier.weight(1f))
            LexaBottomBar(current = tab, onSelect = {})
        }
    }

    @Test fun training() = snap("training") {
        WithBar(TopLevel.TRAINING) { HomeScreen(HomeUiState(isLoading = false), {}, {}, {}, it) }
    }

    private val commuteCard = TrainingCardUi(prompt = "commute", ipa = "/kəˈmjuːt/", box = 2, expectsEnglish = false)

    private fun feedback(verdict: Verdict, answer: String, boxAfter: Int) = TrainingPhase.Feedback(
        card = commuteCard,
        position = 3,
        total = 12,
        answer = answer,
        verdict = verdict,
        correctAnswer = "ездить на работу, поездка на работу",
        boxAfter = boxAfter,
        learned = false,
        example = SampleCommute.examples.first(),
    )

    private fun trainingSnap(name: String, phase: TrainingPhase) = snap(name) {
        WithBar(TopLevel.TRAINING) { HomeScreen(HomeUiState(isLoading = false, session = phase), {}, {}, {}, it) }
    }

    @Test fun trainingQuestion() =
        trainingSnap("training_question", TrainingPhase.Question(commuteCard, position = 3, total = 12, answer = "ездить на раб"))

    @Test fun trainingFeedbackCorrect() =
        trainingSnap("training_feedback_correct", feedback(Verdict.CORRECT, "ездить на работу", boxAfter = 3))

    @Test fun trainingFeedbackWrong() =
        trainingSnap("training_feedback_wrong", feedback(Verdict.WRONG, "путешествовать", boxAfter = 1))

    @Test fun trainingDone() = trainingSnap("training_done", TrainingPhase.Done(correct = 9, total = 12))

    @Test fun translator() = snap("translator") {
        WithBar(TopLevel.TRANSLATOR) { TranslatorScreen(TranslatorUiState(), {}, {}, {}, it) }
    }

    @Test fun translatorResult() = snap("translator_result") {
        WithBar(TopLevel.TRANSLATOR) {
            TranslatorScreen(
                TranslatorUiState(
                    query = "commute",
                    phase = TranslatorPhase.Result(SampleCommute, "commute", wordId = 1, isFavorite = false, justAdded = false),
                ),
                {}, {}, {}, it,
            )
        }
    }

    /** Light only (phase 2 scope). */
    @Test fun translatorError() {
        assumeFalse(dark)
        snap("translator_error") {
            WithBar(TopLevel.TRANSLATOR) {
                TranslatorScreen(
                    TranslatorUiState(query = "commute", phase = TranslatorPhase.Error(LookupError.NO_NETWORK)),
                    {}, {}, {}, it,
                )
            }
        }
    }

    @Test fun wordsNew() = snap("words_new") {
        WithBar(TopLevel.WORDS) { ListsScreen(ListsUiState(ListTab.NEW, isLoading = false), {}, {}, {}, it) }
    }

    @Test fun wordsFavorites() = snap("words_favorites") {
        WithBar(TopLevel.WORDS) { ListsScreen(ListsUiState(ListTab.FAVORITES, isLoading = false), {}, {}, {}, it) }
    }

    @Test fun wordsLearned() = snap("words_learned") {
        WithBar(TopLevel.WORDS) { ListsScreen(ListsUiState(ListTab.LEARNED, isLoading = false), {}, {}, {}, it) }
    }

    @Test fun stats() = snap("stats") {
        WithBar(TopLevel.STATS) { StatsScreen(StatsUiState(), it) }
    }

    @Test fun settings() = snap("settings") {
        SettingsScreen(SettingsUiState(), onBack = {}, onDirectionChange = {})
    }

    @Test fun history() = snap("history") {
        HistoryScreen(HistoryUiState(isLoading = false), onBack = {}, onTranslate = {})
    }

    @Test fun wordMissing() = snap("word_missing") {
        WordScreen(WordUiState(isLoading = false, word = null), onBack = {})
    }

    @Test fun typeSpecimen() = snap("type_specimen") {
        TypeSpecimen()
    }
}
