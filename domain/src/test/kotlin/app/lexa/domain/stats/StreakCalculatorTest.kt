package app.lexa.domain.stats

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class StreakCalculatorTest {
    private val today = LocalDate.of(2026, 10, 29).toEpochDay()
    private fun days(vararg back: Int) = back.map { today - it }

    @Test fun `no answers is zero and zero`() =
        assertEquals(Streak(0, 0), StreakCalculator.compute(emptyList(), today))

    @Test fun `consecutive days ending today`() =
        assertEquals(Streak(3, 3), StreakCalculator.compute(days(0, 1, 2), today))

    @Test fun `today not yet practised keeps yesterday's streak`() =
        assertEquals(Streak(2, 2), StreakCalculator.compute(days(1, 2), today))

    @Test fun `a missed day before yesterday breaks the streak but not the best`() =
        assertEquals(Streak(0, 3), StreakCalculator.compute(days(2, 3, 4), today))

    @Test fun `gap splits runs, best is the longest`() =
        assertEquals(Streak(2, 4), StreakCalculator.compute(days(0, 1, 3, 4, 5, 6, 9), today))

    @Test fun `duplicates and unsorted input are fine`() =
        assertEquals(Streak(2, 2), StreakCalculator.compute(listOf(today, today - 1, today, today - 1), today))

    @Test fun `days after today are ignored`() =
        assertEquals(Streak(1, 1), StreakCalculator.compute(listOf(today, today + 1), today))

    @Test fun `single practice day long ago`() =
        assertEquals(Streak(0, 1), StreakCalculator.compute(days(30), today))

    @Test fun `local day follows the user's zone`() {
        // 22:30 UTC on 28.10 is already 29.10 in Minsk (UTC+3), still 28.10 in New York.
        val instant = Instant.parse("2026-10-28T22:30:00Z")
        assertEquals(today, StreakCalculator.localDay(instant, ZoneId.of("Europe/Minsk")))
        assertEquals(today - 1, StreakCalculator.localDay(instant, ZoneId.of("America/New_York")))
        val minsk = listOf(today - 1, StreakCalculator.localDay(instant, ZoneId.of("Europe/Minsk")))
        assertEquals(Streak(2, 2), StreakCalculator.compute(minsk, today))
    }
}
