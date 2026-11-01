package app.lexa.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import app.lexa.ui.components.LocalReducedMotion
import app.lexa.ui.home.Celebration
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
import app.lexa.domain.model.Settings
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
import app.lexa.domain.model.AnswerTotals
import app.lexa.domain.model.WordCounts
import app.lexa.domain.model.WordDetails
import app.lexa.domain.model.WordSummary
import java.time.LocalDate
import java.time.ZoneOffset
import app.lexa.domain.repository.LookupError
import org.junit.Assume.assumeFalse
import app.lexa.ui.word.WordScreen
import app.lexa.ui.word.WordUiState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import app.lexa.ui.share.SheetActions
import app.lexa.ui.share.SheetContent
import app.lexa.ui.share.TranslateSheetContent
import app.lexa.ui.theme.LexaCornerRadius
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

private fun day(date: String): Long = LocalDate.parse(date).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

private fun sampleWord(
    id: Long,
    term: String,
    translation: String,
    box: Int = 1,
    mistakes: Int = 0,
    added: String,
    favorite: Boolean = false,
    learned: String? = null,
    reviewed: Boolean = true,
) = WordSummary(
    id = id, term = term, translation = translation, isFavorite = favorite, box = box, mistakesCount = mistakes,
    addedAt = day(added), learnedAt = learned?.let(::day), lastReviewedAt = if (reviewed) day(added) else null,
)

/** Nine words: 3 new (box 1), 3 in learning (boxes 2–4), 3 learned. Boxes sum to 6, learned list has 3. */
private val SampleNew = listOf(
    sampleWord(1, "squander", "растрачивать, транжирить", added = "2026-10-29", reviewed = false),
    sampleWord(2, "commute", "ездить на работу, поездка на работу", added = "2026-10-28", reviewed = false),
    sampleWord(4, "reluctant", "неохотный, вынужденный", mistakes = 4, added = "2026-10-20"),
    sampleWord(5, "thorough", "тщательный, основательный", box = 2, mistakes = 2, added = "2026-10-18", favorite = true),
    sampleWord(6, "wander", "бродить, странствовать", box = 3, mistakes = 1, added = "2026-10-12"),
    sampleWord(7, "eager", "стремящийся, жаждущий", box = 4, added = "2026-10-05", favorite = true),
)
private val SampleLearned = listOf(
    sampleWord(8, "abundant", "обильный, изобилующий", added = "2026-09-20", learned = "2026-10-29"),
    sampleWord(9, "sincere", "искренний", added = "2026-09-08", learned = "2026-10-14", favorite = true),
    sampleWord(10, "borrow", "одалживать, занимать", added = "2026-08-30", learned = "2026-10-02"),
)
private val SampleLists = ListsUiState(
    tab = ListTab.NEW,
    isLoading = false,
    newWords = SampleNew,
    favorites = (SampleNew + SampleLearned).filter { it.isFavorite },
    learned = SampleLearned,
)

/** 30 days ending 29.10: sincere on 14.10 (index 14), borrow on 2.10 (index 2), abundant today. */
private val SampleStats = StatsUiState(
    counts = WordCounts(learned = 3, learning = 3, new = 3),
    learnedToday = 1,
    learnedWeek = 1,
    learnedMonth = 3,
    learnedByDay = List(30) { i -> if (i == 2 || i == 14 || i == 29) 1 else 0 },
    streak = 4,
    bestStreak = 9,
    weekAnswers = AnswerTotals(answers = 18, correct = 14),
    allAnswers = AnswerTotals(answers = 64, correct = 49),
    hardWords = SampleNew.filter { it.mistakesCount > 0 }.sortedByDescending { it.mistakesCount },
    boxes = listOf(3, 1, 1, 1, 0),
    learnedWords = SampleLearned,
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

    /** [fontScale] 1.3 is the large-font pass (phase 6): light theme only, files named *_font130_light.png. */
    private fun snap(name: String, fontScale: Float = 1f, content: @Composable () -> Unit) {
        captureRoboImage("src/test/snapshots/${name}_$suffix.png") {
            val density = LocalDensity.current
            CompositionLocalProvider(
                // Every animated value is captured in its final state.
                LocalReducedMotion provides true,
                LocalDensity provides Density(density.density, fontScale),
            ) {
                LexaTheme(darkTheme = dark) { content() }
            }
        }
    }

    private fun large(name: String, content: @Composable () -> Unit) {
        assumeFalse(dark)
        snap("${name}_font130", fontScale = 1.3f, content)
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

    @Composable
    private fun Training(phase: TrainingPhase) {
        WithBar(TopLevel.TRAINING) { HomeScreen(HomeUiState(isLoading = false, session = phase), {}, {}, {}, it) }
    }

    private fun trainingSnap(name: String, phase: TrainingPhase) = snap(name) { Training(phase) }

    private val questionPhase = TrainingPhase.Question(commuteCard, position = 3, total = 12, answer = "ездить на раб")

    @Test fun trainingQuestionLarge() = large("training_question") { Training(questionPhase) }

    @Test fun trainingFeedbackWrongLarge() =
        large("training_feedback_wrong") { Training(feedback(Verdict.WRONG, "путешествовать", boxAfter = 1)) }

    /** A long term shrinks to fit one line instead of breaking mid-word. Light only. */
    @Test fun trainingQuestionLong() {
        assumeFalse(dark)
        trainingSnap(
            "training_question_long",
            TrainingPhase.Question(
                TrainingCardUi(prompt = "incomprehensibilities", ipa = "/ɪnˌkɒmprɪˌhɛnsəˈbɪlɪtiz/", box = 1, expectsEnglish = false),
                position = 1,
                total = 12,
            ),
        )
    }

    /** A word learned in this session: the one celebration line. */
    @Test fun trainingFeedbackLearned() = trainingSnap(
        "training_feedback_learned",
        feedback(Verdict.CORRECT, "ездить на работу", boxAfter = 5).copy(learned = true, celebration = Celebration.Learned),
    )

    @Test fun trainingQuestion() = trainingSnap("training_question", questionPhase)

    @Test fun trainingFeedbackCorrect() =
        trainingSnap("training_feedback_correct", feedback(Verdict.CORRECT, "ездить на работу", boxAfter = 3))

    @Test fun trainingFeedbackWrong() =
        trainingSnap("training_feedback_wrong", feedback(Verdict.WRONG, "путешествовать", boxAfter = 1))

    @Test fun trainingDone() = trainingSnap("training_done", TrainingPhase.Done(correct = 9, total = 12))

    private val deckCard = TrainingCardUi(prompt = "reluctant", ipa = null, box = 0, expectsEnglish = false)

    @Test fun trainingDeckQuestion() =
        trainingSnap("training_deck_question", TrainingPhase.Question(deckCard, position = 1, total = 20, answer = "неохот"))

    @Test fun trainingDeckWrong() = trainingSnap(
        "training_deck_wrong",
        TrainingPhase.Feedback(
            card = deckCard, position = 1, total = 20, answer = "медленный", verdict = Verdict.WRONG,
            correctAnswer = "неохотный, вынужденный", boxAfter = 1, learned = false, example = null, fromDeck = true,
        ),
    )

    private val ruCard = TrainingCardUi(prompt = "поездка, путешествие", ipa = null, box = 2, expectsEnglish = true)

    @Test fun trainingHint() = trainingSnap(
        "training_hint",
        TrainingPhase.Question(ruCard, position = 4, total = 20, answer = "jo", hint = "j _ _ _ _ _ _"),
    )

    @Test fun trainingGaveUp() = trainingSnap(
        "training_gave_up",
        TrainingPhase.Feedback(
            card = commuteCard, position = 5, total = 20, answer = "", verdict = Verdict.GAVE_UP,
            correctAnswer = "ездить на работу, поездка на работу", boxAfter = 1, learned = false,
            example = null, nextReviewDays = 0,
        ),
    )

    @Test fun trainingAlsoRight() = trainingSnap(
        "training_also_right",
        TrainingPhase.Feedback(
            card = ruCard, position = 4, total = 20, answer = "trip", verdict = Verdict.ALSO_RIGHT,
            correctAnswer = "journey", boxAfter = 2, learned = false, example = null, alsoRight = "journey",
        ),
    )

    private fun homeSnap(name: String, state: HomeUiState) = snap(name) {
        WithBar(TopLevel.TRAINING) { HomeScreen(state, {}, {}, {}, it) }
    }

    @Test fun levelPicker() = homeSnap("level_picker", HomeUiState(isLoading = false))

    @Test fun placementAsking() =
        homeSnap("placement_asking", HomeUiState(isLoading = false, placement = app.lexa.ui.home.Placement.Asking("thorough", 14, 25)))

    @Test fun placementResult() = homeSnap(
        "placement_result",
        HomeUiState(isLoading = false, placement = app.lexa.ui.home.Placement.Result(app.lexa.domain.model.VocabLevel.B1, known = 15, total = 25)),
    )

    @Test fun trainingWithLevel() = homeSnap(
        "training_level",
        HomeUiState(
            isLoading = false, dueCount = 4, level = app.lexa.domain.model.VocabLevel.B1,
            daily = app.lexa.domain.training.DailyProgress(done = 12, goal = 20),
        ),
    )

    @Test fun trainingGoalReached() = homeSnap(
        "training_goal_reached",
        HomeUiState(
            isLoading = false, dueCount = 0, level = app.lexa.domain.model.VocabLevel.B1,
            daily = app.lexa.domain.training.DailyProgress(done = 20, goal = 20),
        ),
    )

    @Test fun trainingRetry() = trainingSnap(
        "training_retry",
        TrainingPhase.Question(commuteCard.copy(box = 1), position = 13, total = 14, retry = true),
    )

    @Test fun trainingRetryWrong() = trainingSnap(
        "training_retry_wrong",
        TrainingPhase.Feedback(
            card = commuteCard.copy(box = 1), position = 13, total = 14, answer = "ехать", verdict = Verdict.WRONG,
            correctAnswer = "ездить на работу, поездка на работу", boxAfter = 1, learned = false,
            example = null, retry = true, retryAgain = true,
        ),
    )

    @Test fun trainingDoneWithGoal() = trainingSnap(
        "training_done_goal",
        TrainingPhase.Done(correct = 9, total = 12, daily = app.lexa.domain.training.DailyProgress(done = 20, goal = 20)),
    )

    @Test fun translator() = snap("translator") {
        WithBar(TopLevel.TRANSLATOR) { TranslatorScreen(TranslatorUiState(), {}, {}, {}, it) }
    }

    @Test fun translatorResult() = snap("translator_result") { TranslatorResultScreen() }

    @Test fun translatorResultLarge() = large("translator_result") { TranslatorResultScreen() }

    @Composable
    private fun TranslatorResultScreen() {
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
        WithBar(TopLevel.WORDS) { ListsScreen(ListsUiState(ListTab.NEW, isLoading = false), modifier = it) }
    }

    @Test fun wordsFavorites() = snap("words_favorites") {
        WithBar(TopLevel.WORDS) { ListsScreen(ListsUiState(ListTab.FAVORITES, isLoading = false), modifier = it) }
    }

    @Test fun wordsLearned() = snap("words_learned") {
        WithBar(TopLevel.WORDS) { ListsScreen(ListsUiState(ListTab.LEARNED, isLoading = false), modifier = it) }
    }

    @Test fun stats() = snap("stats") {
        WithBar(TopLevel.STATS) { StatsScreen(StatsUiState(), modifier = it) }
    }

    @Test fun settings() = snap("settings") {
        SettingsScreen(SettingsUiState(appVersion = "0.6.0"), onBack = {})
    }

    @Test fun settingsLarge() = large("settings") {
        SettingsScreen(SettingsUiState(Settings(reminderEnabled = true), appVersion = "0.6.0"), onBack = {})
    }

    @Test fun settingsReminderOn() = snap("settings_reminder") {
        SettingsScreen(SettingsUiState(Settings(reminderEnabled = true), appVersion = "0.6.0"), onBack = {})
    }

    /** The sheet as it floats over the calling app: scrim above, the sheet body at the bottom. */
    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    @Test fun translateSheet() = snap("translate_sheet") {
        val colors = LexaTheme.colors
        Column(Modifier.fillMaxSize().background(androidx.compose.material3.MaterialTheme.colorScheme.scrim)) {
            Spacer(Modifier.weight(1f))
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 640.dp)
                    .shadow(8.dp, RoundedCornerShape(topStart = LexaCornerRadius, topEnd = LexaCornerRadius))
                    .background(colors.paper, RoundedCornerShape(topStart = LexaCornerRadius, topEnd = LexaCornerRadius)),
            ) {
                // Stands in for ModalBottomSheet's drag handle, which needs a real window to draw.
                androidx.compose.material3.BottomSheetDefaults.DragHandle(Modifier.align(Alignment.CenterHorizontally))
                TranslateSheetContent(
                    SheetContent.Lookup(
                        TranslatorPhase.Result(SampleCommute, "commute", wordId = 1, isFavorite = false, justAdded = true),
                    ),
                    SheetActions(),
                )
            }
        }
    }

    @Test fun history() = snap("history") {
        HistoryScreen(HistoryUiState(isLoading = false), onBack = {}, onTranslate = {})
    }

    @Test fun wordMissing() = snap("word_missing") {
        WordScreen(WordUiState(isLoading = false))
    }

    @Test fun wordsNewData() = snap("words_new_data") {
        WithBar(TopLevel.WORDS) { ListsScreen(SampleLists, modifier = it) }
    }

    @Test fun wordsNewDataLarge() = large("words_new_data") {
        WithBar(TopLevel.WORDS) { ListsScreen(SampleLists, modifier = it) }
    }

    @Test fun wordsLearnedData() = snap("words_learned_data") {
        WithBar(TopLevel.WORDS) { ListsScreen(SampleLists.copy(tab = ListTab.LEARNED), modifier = it) }
    }

    /** Light only (phase 4 scope). */
    @Test fun wordsSearchEmpty() {
        assumeFalse(dark)
        snap("words_search_empty") {
            WithBar(TopLevel.WORDS) { ListsScreen(SampleLists.copy(query = "serendipity"), modifier = it) }
        }
    }

    @Test fun wordCard() = wordCard("word_card")

    @Test fun wordCardLarge() = large("word_card") { WordCardScreen() }

    private fun wordCard(name: String) = snap(name) { WordCardScreen() }

    @Composable
    private fun WordCardScreen() {
        WordScreen(
            WordUiState(
                isLoading = false,
                details = WordDetails(
                    summary = sampleWord(3, "commute", "ездить на работу, поездка на работу", box = 2, mistakes = 1,
                        added = "2026-10-21", favorite = true, reviewed = true),
                    entry = SampleCommute.copy(senses = SampleCommute.senses.map { it.copy(gloss = null) }),
                ),
            ),
        )
    }

    @Test fun statsData() = snap("stats_data") {
        WithBar(TopLevel.STATS) { StatsScreen(SampleStats, modifier = it) }
    }

    @Test fun statsDataLarge() = large("stats_data") {
        WithBar(TopLevel.STATS) { StatsScreen(SampleStats, modifier = it) }
    }

    /** The whole statistics column on one tall image, so blocks below the fold get reviewed too. */
    @Test @Config(qualifiers = "w360dp-h2400dp-xxhdpi")
    fun statsDataFull() = snap("stats_data_full") { StatsScreen(SampleStats) }

    @Test @Config(qualifiers = "w360dp-h1600dp-xxhdpi")
    fun wordCardFull() = wordCard("word_card_full")

    @Test fun typeSpecimen() = snap("type_specimen") {
        TypeSpecimen()
    }

    /** The whole settings column on one tall image: the blocks below the first screen are reviewed too. */
    @Test @Config(qualifiers = "w360dp-h3400dp-xxhdpi")
    fun settingsFull() = snap("settings_full") {
        SettingsScreen(
            SettingsUiState(
                Settings(
                    reminderEnabled = true, vocabLevel = app.lexa.domain.model.VocabLevel.B1, dailyGoal = 35,
                    reminderDays = 0b0011111, newWordsPerDay = 0,
                ),
                appVersion = "0.6.0",
            ),
            onBack = {},
            updateStatus = app.lexa.ui.settings.UpdateStatus.UP_TO_DATE,
        )
    }

    private fun fonts(name: String, word: app.lexa.domain.model.WordFont, ui: app.lexa.domain.model.UiFont) =
        captureRoboImage("src/test/snapshots/${name}_$suffix.png") {
            LexaTheme(darkTheme = dark, wordFont = word, uiFont = ui) { TypeSpecimen() }
        }

    @Test fun fontsLoraInter() = fonts("fonts_lora_inter", app.lexa.domain.model.WordFont.LORA, app.lexa.domain.model.UiFont.INTER)

    @Test fun fontsSourceSerifSystem() =
        fonts("fonts_sourceserif_system", app.lexa.domain.model.WordFont.SOURCE_SERIF, app.lexa.domain.model.UiFont.SYSTEM)

    /** The same training card at 90 % and 130 % of the text size, to see the scale hold. */
    @Test fun textScale130() = captureRoboImage("src/test/snapshots/text_scale130_$suffix.png") {
        LexaTheme(darkTheme = dark, textScale = 1.3f) { TypeSpecimen() }
    }
}
