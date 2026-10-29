package app.lexa.domain.stats

import java.time.Instant
import java.time.ZoneId

/** Current and best run of consecutive practice days. */
data class Streak(val current: Int = 0, val best: Int = 0)

/**
 * Streaks from the days with at least one answer (docs/ARCHITECTURE.md §7), in Kotlin because
 * SQLite on API 26 has no window functions. Days are `LocalDate.toEpochDay()` in the user's zone.
 */
object StreakCalculator {

    /**
     * The current streak ends today or yesterday: a day not yet practised does not break it.
     * The best streak is the longest run anywhere, never shorter than the current one.
     */
    fun compute(practiceDays: Collection<Long>, today: Long): Streak {
        val days = practiceDays.filter { it <= today }.toSortedSet()
        if (days.isEmpty()) return Streak()
        var best = 0
        var run = 0
        var previous: Long? = null
        for (day in days) {
            run = if (previous != null && day == previous + 1) run + 1 else 1
            best = maxOf(best, run)
            previous = day
        }
        val end = when {
            today in days -> today
            today - 1 in days -> today - 1
            else -> return Streak(current = 0, best = best)
        }
        var current = 0
        var day = end
        while (day in days) {
            current++
            day--
        }
        return Streak(current = current, best = best)
    }

    /** The epoch day of [instant] in [zone]: how review_log.localDay is written. */
    fun localDay(instant: Instant, zone: ZoneId): Long = instant.atZone(zone).toLocalDate().toEpochDay()
}
