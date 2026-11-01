package app.lexa.domain.training

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DailyGoalTest {
    @Test fun `the count of another day does not count today`() {
        assertEquals(DailyProgress(done = 0, goal = 20), DayCount(epochDay = 100, count = 14).progressOn(101, 20))
        assertEquals(DailyProgress(done = 14, goal = 20), DayCount(epochDay = 101, count = 14).progressOn(101, 20))
    }

    @Test fun `the goal is reached at exactly the goal and the bar caps at full`() {
        assertFalse(DailyProgress(19, 20).reached)
        assertTrue(DailyProgress(20, 20).reached)
        assertEquals(1f, DailyProgress(35, 20).fraction)
        assertEquals(0.5f, DailyProgress(10, 20).fraction)
    }

    @Test fun `a zero goal never counts as reached and never divides by zero`() {
        assertFalse(DailyProgress(0, 0).reached)
        assertEquals(0f, DailyProgress(5, 0).fraction)
    }
}
