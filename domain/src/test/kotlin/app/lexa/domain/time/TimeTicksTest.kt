package app.lexa.domain.time

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** A clock that tests move by hand. */
private class MovableTime(start: String, private val zoneId: ZoneId = ZoneId.of("Europe/Minsk")) : TimeProvider {
    var instant: Instant = Instant.parse(start)
    override val clock: Clock get() = Clock.fixed(instant, zoneId)
}

class TimeTicksTest {
    @Test fun `next midnight is counted in the local zone`() {
        // 22:30 in Minsk (UTC+3) – 1.5 hours to midnight.
        val time = MovableTime("2026-10-29T19:30:00Z")
        assertEquals(90 * 60_000L, time.millisUntilNextMidnight())
    }

    @Test fun `ticks at start, at midnight and on refresh`() = runTest {
        val time = MovableTime("2026-10-29T20:59:00Z") // 23:59 local
        val refresh = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val ticks = mutableListOf<Long>()
        val job = launch { time.nowTicks(refresh).take(3).toList(ticks) }
        runCurrent()
        assertEquals(1, ticks.size)

        time.instant = Instant.parse("2026-10-29T21:00:00Z") // midnight passes
        advanceTimeBy(60_001)
        assertEquals(2, ticks.size)

        refresh.tryEmit(Unit)
        runCurrent()
        assertEquals(3, ticks.size)
        job.join()
    }
}
