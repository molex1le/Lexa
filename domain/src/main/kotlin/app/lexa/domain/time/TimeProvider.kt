package app.lexa.domain.time

import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/** The only source of "now" and "today" (docs/ARCHITECTURE.md §3). */
interface TimeProvider {
    val clock: Clock
    val zone: ZoneId get() = clock.zone
    fun nowMillis(): Long = clock.millis()
    fun today(): LocalDate = LocalDate.now(clock)
}
