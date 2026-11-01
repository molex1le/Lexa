package app.lexa.domain.model

import java.time.DayOfWeek

/** Light, dark, or whatever the phone is set to. */
enum class ThemeMode {
    SYSTEM, LIGHT, DARK;

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }
}

/** The face of the word on a card and of the figures. All three have Cyrillic. */
enum class WordFont { LITERATA, LORA, SOURCE_SERIF }

/** The face of everything else, transcription included, so all three have the full IPA set (SYSTEM: the phone's). */
enum class UiFont { SOURCE_SANS, INTER, SYSTEM }

/** Weekdays as a bit mask: Monday is bit 0, Sunday bit 6. The reminder fires only on the chosen days. */
object ReminderDays {
    const val ALL = 0b1111111

    fun has(mask: Int, day: DayOfWeek): Boolean = mask and (1 shl (day.value - 1)) != 0

    fun toggle(mask: Int, day: DayOfWeek): Int = mask xor (1 shl (day.value - 1))

    fun count(mask: Int): Int = Integer.bitCount(mask and ALL)
}
