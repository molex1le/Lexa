package app.lexa.domain.reminder

import app.lexa.domain.model.ReminderDays
import app.lexa.domain.model.ThemeMode
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReminderDaysTest {
    private val zone = ZoneId.of("Europe/Minsk")
    private fun clockAt(dateTime: String): Clock =
        Clock.fixed(LocalDateTime.parse(dateTime).atZone(zone).toInstant(), zone)

    private val seven = 19 * 60
    private val weekdays = DayOfWeek.entries.take(5).fold(0) { mask, day -> mask or (1 shl (day.value - 1)) }

    // 2026-10-29 is a Thursday.
    @Test fun `a chosen day later today still fires today`() {
        val next = ReminderTime.nextRun(clockAt("2026-10-29T10:00:00"), seven, weekdays)
        assertEquals(LocalDateTime.parse("2026-10-29T19:00:00"), next.toLocalDateTime())
    }

    @Test fun `after the time on Friday the weekend is skipped to Monday`() {
        val next = ReminderTime.nextRun(clockAt("2026-10-30T20:00:00"), seven, weekdays)
        assertEquals(DayOfWeek.MONDAY, next.dayOfWeek)
        assertEquals(LocalDateTime.parse("2026-11-02T19:00:00"), next.toLocalDateTime())
    }

    @Test fun `a single day a week fires a week later when today's time has passed`() {
        val thursday = 1 shl (DayOfWeek.THURSDAY.value - 1)
        val next = ReminderTime.nextRun(clockAt("2026-10-29T20:00:00"), seven, thursday)
        assertEquals(LocalDateTime.parse("2026-11-05T19:00:00"), next.toLocalDateTime())
    }

    @Test fun `an empty selection is treated as every day`() {
        val next = ReminderTime.nextRun(clockAt("2026-10-29T20:00:00"), seven, 0)
        assertEquals(LocalDateTime.parse("2026-10-30T19:00:00"), next.toLocalDateTime())
    }

    @Test fun `weekday masks toggle and count`() {
        assertEquals(7, ReminderDays.count(ReminderDays.ALL))
        val withoutSunday = ReminderDays.toggle(ReminderDays.ALL, DayOfWeek.SUNDAY)
        assertFalse(ReminderDays.has(withoutSunday, DayOfWeek.SUNDAY))
        assertTrue(ReminderDays.has(withoutSunday, DayOfWeek.MONDAY))
        assertEquals(6, ReminderDays.count(withoutSunday))
    }

    @Test fun `the theme mode decides dark, the phone only when asked`() {
        assertTrue(ThemeMode.SYSTEM.isDark(systemDark = true))
        assertFalse(ThemeMode.SYSTEM.isDark(systemDark = false))
        assertTrue(ThemeMode.DARK.isDark(systemDark = false))
        assertFalse(ThemeMode.LIGHT.isDark(systemDark = true))
    }
}
