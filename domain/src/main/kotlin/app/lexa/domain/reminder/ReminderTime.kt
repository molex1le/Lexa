package app.lexa.domain.reminder

import app.lexa.domain.model.ReminderDays
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/** When the daily review reminder fires next (docs/ARCHITECTURE.md §13). */
object ReminderTime {
    /**
     * The next moment at [minutesOfDay] in the clock's zone, strictly after now: if that time has
     * passed today (or is right now), it is tomorrow. DST gaps resolve the way [ZonedDateTime.of] does.
     */
    fun nextRun(clock: Clock, minutesOfDay: Int, days: Int = ReminderDays.ALL): ZonedDateTime {
        val now = ZonedDateTime.now(clock)
        val time = LocalTime.of(minutesOfDay / 60 % 24, minutesOfDay % 60)
        // An empty selection would never fire: treat it as every day.
        val mask = if (days and ReminderDays.ALL == 0) ReminderDays.ALL else days
        for (offset in 0L..7L) {
            val date = LocalDate.now(clock).plusDays(offset)
            if (!ReminderDays.has(mask, date.dayOfWeek)) continue
            val candidate = ZonedDateTime.of(date, time, clock.zone)
            if (candidate.isAfter(now)) return candidate
        }
        error("unreachable: some weekday is always selected")
    }

    fun delayUntilNext(clock: Clock, minutesOfDay: Int, days: Int = ReminderDays.ALL): Duration =
        Duration.between(ZonedDateTime.now(clock), nextRun(clock, minutesOfDay, days))
}
