package app.lexa.domain.model

data class Settings(
    val direction: Direction = Direction.EN_RU,
    /** Minutes from midnight; 19:00 by default (docs/ARCHITECTURE.md §11). */
    val reminderMinutes: Int = DEFAULT_REMINDER_MINUTES,
    val reminderEnabled: Boolean = false,
) {
    companion object {
        const val DEFAULT_REMINDER_MINUTES = 19 * 60
    }
}
