package app.lexa.domain.training

import kotlinx.coroutines.flow.Flow

/** Answers given on one day, as stored: the count belongs to [epochDay] and is void on any other day. */
data class DayCount(val epochDay: Long, val count: Int)

/** Today's progress against the goal: «12 из 20». */
data class DailyProgress(val done: Int, val goal: Int) {
    val reached: Boolean get() = goal > 0 && done >= goal

    /** 0..1 for the bar; full once the goal is reached. */
    val fraction: Float get() = if (goal <= 0) 0f else (done.toFloat() / goal).coerceIn(0f, 1f)
}

/**
 * How many cards the learner answered today (first answers only: retries and «тоже верно» don't count).
 * One counter per day in DataStore: the review log has no row for a built-in word answered right.
 */
interface DailyProgressRepository {
    val count: Flow<DayCount>

    /** Adds one answer to [epochDay]'s count; the count restarts when the day changed. */
    suspend fun add(epochDay: Long)
}

fun DayCount.progressOn(epochDay: Long, goal: Int): DailyProgress =
    DailyProgress(done = if (this.epochDay == epochDay) count else 0, goal = goal)
