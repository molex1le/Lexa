package app.lexa.domain.model

data class Settings(
    val direction: Direction = Direction.EN_RU,
    /** Minutes from midnight; 19:00 by default (docs/ARCHITECTURE.md §11). */
    val reminderMinutes: Int = DEFAULT_REMINDER_MINUTES,
    val reminderEnabled: Boolean = false,
    val wordSort: WordSort = WordSort.ADDED,
    /** Null until the learner picks a level or takes the placement test. */
    val vocabLevel: VocabLevel? = null,
    /** Cards per training session (5–100). */
    val sessionSize: Int = DEFAULT_SESSION_SIZE,
    /** Cards to answer a day (5–200); the overview shows «12 из 20». */
    val dailyGoal: Int = DEFAULT_DAILY_GOAL,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val wordFont: WordFont = WordFont.LITERATA,
    val uiFont: UiFont = UiFont.SOURCE_SANS,
    /** Text size on top of the phone's own, in percent: 90, 100, 115 or 130. */
    val textScalePercent: Int = DEFAULT_TEXT_SCALE,
    /** Weekdays the reminder fires on ([ReminderDays]). */
    val reminderDays: Int = ReminderDays.ALL,
    /** Unknown built-in words that may join «Новые» a day; 0 means review only. */
    val newWordsPerDay: Int = DEFAULT_NEW_WORDS_PER_DAY,
    /** Say the word aloud after every answer. */
    val autoPronounce: Boolean = false,
    val haptics: Boolean = true,
    /** Look for a newer version on GitHub at launch and once a day. */
    val updateCheck: Boolean = true,
) {
    companion object {
        const val DEFAULT_REMINDER_MINUTES = 19 * 60
        const val DEFAULT_SESSION_SIZE = 20
        const val DEFAULT_DAILY_GOAL = 20
        const val DEFAULT_TEXT_SCALE = 100
        val TEXT_SCALES = listOf(90, 100, 115, 130)
        const val DEFAULT_NEW_WORDS_PER_DAY = 10
        val NEW_WORDS_PER_DAY = 0..100
        val DAILY_GOALS = 5..200
        val SESSION_SIZES = 5..100
    }
}
