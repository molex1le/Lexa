package app.lexa.domain.leitner

import app.lexa.domain.time.TimeProvider
import javax.inject.Inject

/** The scheduling facts of one word. [learnedAt] non-null means the word left box 5. */
data class ReviewState(
    val box: Int,
    val nextReviewAt: Long,
    val mistakesCount: Int,
    val learnedAt: Long? = null,
)

/** A word's state after an answer. [toBox] is the box written to the review log: 0 = learned. */
data class Transition(val state: ReviewState, val toBox: Int) {
    val learned: Boolean get() = toBox == LEARNED

    companion object {
        const val LEARNED = 0
    }
}

/**
 * Leitner moves (docs/ARCHITECTURE.md §9). Every next review is the start of a local day, so a word
 * is ready in the morning, not at the hour it was answered.
 */
class LeitnerScheduler @Inject constructor(private val time: TimeProvider) {

    /**
     * Correct answer. In the schedule ([wasDue]) the word moves up a box, or out of box 5 into learned.
     * Out-of-schedule practice of hard words does not promote (owner decision, §17): state is unchanged.
     */
    fun onCorrect(before: ReviewState, wasDue: Boolean = true): Transition {
        if (!wasDue) return Transition(before, before.box)
        if (before.box >= LeitnerRules.LAST_BOX) {
            return Transition(before.copy(box = LeitnerRules.LAST_BOX, learnedAt = time.nowMillis()), Transition.LEARNED)
        }
        val box = before.box + 1
        return Transition(before.copy(box = box, nextReviewAt = startOfDayIn(LeitnerRules.intervalDays(box))), box)
    }

    /** Wrong answer from any box: back to box 1 («Новые»), due now, one more mistake. */
    fun onWrong(before: ReviewState): Transition =
        Transition(
            before.copy(box = LeitnerRules.FIRST_BOX, nextReviewAt = time.nowMillis(), mistakesCount = before.mistakesCount + 1),
            LeitnerRules.FIRST_BOX,
        )

    /**
     * «Я знал, это опечатка» right after a wrong answer: the correct move applied to the state *before*
     * that answer, so the box change and the extra mistake are both undone.
     */
    fun onOverride(before: ReviewState, wasDue: Boolean = true): Transition = onCorrect(before, wasDue)

    private fun startOfDayIn(days: Int): Long =
        time.today().plusDays(days.toLong()).atStartOfDay(time.zone).toInstant().toEpochMilli()
}
