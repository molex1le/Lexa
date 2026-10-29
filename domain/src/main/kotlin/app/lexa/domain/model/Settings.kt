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
) {
    companion object {
        const val DEFAULT_REMINDER_MINUTES = 19 * 60
        const val DEFAULT_SESSION_SIZE = 20
        val SESSION_SIZES = 5..100
    }
}
