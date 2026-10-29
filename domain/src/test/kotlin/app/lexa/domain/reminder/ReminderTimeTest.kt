package app.lexa.domain.reminder

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class ReminderTimeTest {
    private val zone = ZoneId.of("Europe/Minsk")
    private fun clockAt(dateTime: String): Clock =
        Clock.fixed(LocalDateTime.parse(dateTime).atZone(zone).toInstant(), zone)

    private val seven = 19 * 60

    @Test fun beforeTimeTodayFiresToday() {
        val next = ReminderTime.nextRun(clockAt("2026-10-29T10:15:00"), seven)
        assertEquals(LocalDateTime.parse("2026-10-29T19:00:00"), next.toLocalDateTime())
        assertEquals(Duration.ofHours(8).plusMinutes(45), ReminderTime.delayUntilNext(clockAt("2026-10-29T10:15:00"), seven))
    }

    @Test fun afterTimeTodayFiresTomorrow() {
        val next = ReminderTime.nextRun(clockAt("2026-10-29T19:30:00"), seven)
        assertEquals(LocalDateTime.parse("2026-10-30T19:00:00"), next.toLocalDateTime())
    }

    @Test fun exactlyAtTimeFiresTomorrow() {
        val next = ReminderTime.nextRun(clockAt("2026-10-29T19:00:00"), seven)
        assertEquals(LocalDateTime.parse("2026-10-30T19:00:00"), next.toLocalDateTime())
    }

    @Test fun oneMinuteBeforeIsOneMinute() {
        assertEquals(Duration.ofMinutes(1), ReminderTime.delayUntilNext(clockAt("2026-10-29T18:59:00"), seven))
    }

    @Test fun midnightAndMinutes() {
        val next = ReminderTime.nextRun(clockAt("2026-12-31T23:59:30"), 0)
        assertEquals(LocalDateTime.parse("2027-01-01T00:00:00"), next.toLocalDateTime())
        val half = ReminderTime.nextRun(clockAt("2026-10-29T07:00:00"), 7 * 60 + 30)
        assertEquals(LocalDateTime.parse("2026-10-29T07:30:00"), half.toLocalDateTime())
    }

    @Test fun dstDayKeepsWallClockTime() {
        val berlin = ZoneId.of("Europe/Berlin")
        // 25.10.2026 is a 25-hour day in Berlin: the reminder stays at 19:00 local, so the delay is 25 h.
        val clock = Clock.fixed(LocalDateTime.parse("2026-10-24T19:00:00").atZone(berlin).toInstant(), berlin)
        assertEquals(Duration.ofHours(25), ReminderTime.delayUntilNext(clock, seven))
        assertEquals(Instant.parse("2026-10-25T18:00:00Z"), ReminderTime.nextRun(clock, seven).toInstant())
    }
}
