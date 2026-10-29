package app.lexa.domain.reminder

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
    fun nextRun(clock: Clock, minutesOfDay: Int): ZonedDateTime {
        val now = ZonedDateTime.now(clock)
        val time = LocalTime.of(minutesOfDay / 60 % 24, minutesOfDay % 60)
        val today = ZonedDateTime.of(LocalDate.now(clock), time, clock.zone)
        return if (today.isAfter(now)) today else ZonedDateTime.of(LocalDate.now(clock).plusDays(1), time, clock.zone)
    }

    fun delayUntilNext(clock: Clock, minutesOfDay: Int): Duration =
        Duration.between(ZonedDateTime.now(clock), nextRun(clock, minutesOfDay))
}
