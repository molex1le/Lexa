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

/**
 * The face of the word on a card and of the figures: five different characters, all with Cyrillic.
 * A text serif, a high-contrast display serif, a slab, a hand with a marker, a geometric sans.
 */
enum class WordFont { LITERATA, PLAYFAIR, ROBOTO_SLAB, CAVEAT, MONTSERRAT }

/**
 * The face of everything else: a humanist sans, a rounded sans, a geometric sans, a text serif, or the phone's own.
 * Only faces drawn for reading text sizes; the transcription keeps its own face whatever is picked.
 */
enum class UiFont { SOURCE_SANS, NUNITO, MONTSERRAT, LITERATA, SYSTEM }

/** Weekdays as a bit mask: Monday is bit 0, Sunday bit 6. The reminder fires only on the chosen days. */
object ReminderDays {
    const val ALL = 0b1111111

    fun has(mask: Int, day: DayOfWeek): Boolean = mask and (1 shl (day.value - 1)) != 0

    fun toggle(mask: Int, day: DayOfWeek): Int = mask xor (1 shl (day.value - 1))

    fun count(mask: Int): Int = Integer.bitCount(mask and ALL)
}
