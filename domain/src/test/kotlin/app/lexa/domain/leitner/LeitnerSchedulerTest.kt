package app.lexa.domain.leitner

import app.lexa.domain.time.TimeProvider
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

internal class FixedTime(instant: String, zone: String = "Europe/Minsk") : TimeProvider {
    override val clock: Clock = Clock.fixed(Instant.parse(instant), ZoneId.of(zone))
}

/** 29.10.2026 21:30 in Minsk (UTC+3): late evening, so "start of local day" is visible in the numbers. */
private val time = FixedTime("2026-10-29T18:30:00Z")
private val scheduler = LeitnerScheduler(time)
private fun startOf(date: String, zone: String = "Europe/Minsk") =
    LocalDate.parse(date).atStartOfDay(ZoneId.of(zone)).toInstant().toEpochMilli()

/** ARCHITECTURE §9: each correct move and its interval. */
@RunWith(Parameterized::class)
class LeitnerCorrectTest(private val from: Int, private val to: Int, private val nextDay: String) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "box {0} -> {1}")
        fun cases(): List<Array<Any>> = listOf(
            arrayOf(1, 2, "2026-10-30"), // +1 day
            arrayOf(2, 3, "2026-11-01"), // +3 days
            arrayOf(3, 4, "2026-11-05"), // +7 days
            arrayOf(4, 5, "2026-11-19"), // +21 days
        )
    }

    @Test fun promotes() {
        val t = scheduler.onCorrect(ReviewState(box = from, nextReviewAt = 0, mistakesCount = 2))
        assertEquals(to, t.state.box)
        assertEquals(to, t.toBox)
        assertEquals(startOf(nextDay), t.state.nextReviewAt)
        assertEquals(2, t.state.mistakesCount)
        assertNull(t.state.learnedAt)
        assertFalse(t.learned)
    }
}

/** A wrong answer from every box lands in box 1, due now, one more mistake. */
@RunWith(Parameterized::class)
class LeitnerWrongTest(private val from: Int) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "box {0}")
        fun boxes(): List<Array<Any>> = LeitnerRules.boxes.map { arrayOf<Any>(it) }
    }

    private val before = ReviewState(box = from, nextReviewAt = 123L, mistakesCount = 4)

    @Test fun demotesToBoxOne() {
        val t = scheduler.onWrong(before)
        assertEquals(ReviewState(box = 1, nextReviewAt = time.nowMillis(), mistakesCount = 5), t.state)
        assertEquals(1, t.toBox)
    }

    @Test fun overrideAfterWrongIsCorrectFromSnapshot() {
        scheduler.onWrong(before)
        val t = scheduler.onOverride(before)
        assertEquals(scheduler.onCorrect(before), t)
        assertEquals(4, t.state.mistakesCount, "the extra mistake is undone")
        assertEquals(if (from == 5) 0 else from + 1, t.toBox)
    }

    @Test fun practiceWrongStillDemotes() {
        assertEquals(scheduler.onWrong(before), scheduler.onWrong(before))
        assertEquals(1, scheduler.onWrong(before).state.box)
    }

    @Test fun practiceCorrectDoesNotPromote() {
        val t = scheduler.onCorrect(before, wasDue = false)
        assertEquals(before, t.state)
        assertEquals(from, t.toBox)
    }

    @Test fun practiceOverrideRestoresSnapshot() {
        assertEquals(before, scheduler.onOverride(before, wasDue = false).state)
    }
}

class LeitnerSchedulerTest {
    @Test fun `correct in box 5 is learned, box stays 5, log says 0`() {
        val t = scheduler.onCorrect(ReviewState(box = 5, nextReviewAt = 7L, mistakesCount = 1))
        assertTrue(t.learned)
        assertEquals(Transition.LEARNED, t.toBox)
        assertEquals(5, t.state.box)
        assertEquals(time.nowMillis(), t.state.learnedAt)
    }

    @Test fun `next review is start of the local day in the user's zone`() {
        // Same instant: in Minsk it is already 30.10 at 01:30, in New York still 29.10.
        val minsk = LeitnerScheduler(FixedTime("2026-10-29T22:30:00Z"))
        val newYork = LeitnerScheduler(FixedTime("2026-10-29T22:30:00Z", "America/New_York"))
        val box1 = ReviewState(box = 1, nextReviewAt = 0, mistakesCount = 0)
        assertEquals(startOf("2026-10-31"), minsk.onCorrect(box1).state.nextReviewAt)
        assertEquals(startOf("2026-10-30", "America/New_York"), newYork.onCorrect(box1).state.nextReviewAt)
    }

    @Test fun `interval across a DST change still lands on midnight`() {
        // Berlin leaves summer time on 25.10.2026; 21 days from 20.10 is 10.11.
        val berlin = LeitnerScheduler(FixedTime("2026-10-20T10:00:00Z", "Europe/Berlin"))
        val t = berlin.onCorrect(ReviewState(box = 4, nextReviewAt = 0, mistakesCount = 0))
        assertEquals(startOf("2026-11-10", "Europe/Berlin"), t.state.nextReviewAt)
    }
}
