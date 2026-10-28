package app.lexa.domain.leitner

/**
 * Leitner boxes 1..5 and the wait before a word in each box is shown again.
 * The scheduler that moves words between boxes arrives in phase 3.
 */
object LeitnerRules {
    const val FIRST_BOX = 1
    const val LAST_BOX = 5
    val boxes: IntRange = FIRST_BOX..LAST_BOX

    private val intervals = intArrayOf(0, 1, 3, 7, 21)

    fun intervalDays(box: Int): Int {
        require(box in boxes) { "Box must be in $boxes, was $box" }
        return intervals[box - 1]
    }
}
