package app.lexa.domain

import app.lexa.domain.leitner.LeitnerRules
import app.lexa.domain.model.AnswerTotals
import app.lexa.domain.text.normalizeKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** Proves :domain builds and runs on the plain JVM. Full checker and Leitner tests come in phase 3. */
class DomainSmokeTest {

    @Test
    fun intervalsMatchSpec() {
        assertEquals(listOf(0, 1, 3, 7, 21), LeitnerRules.boxes.map(LeitnerRules::intervalDays))
    }

    @Test
    fun boxOutsideRangeIsRejected() {
        assertFailsWith<IllegalArgumentException> { LeitnerRules.intervalDays(0) }
        assertFailsWith<IllegalArgumentException> { LeitnerRules.intervalDays(6) }
    }

    @Test
    fun normalizeKeyFoldsCaseSpacesAndYo() {
        assertEquals("give up", normalizeKey("  Give   UP "))
        assertEquals("елка", normalizeKey("Ёлка"))
    }

    @Test
    fun accuracyIsUndefinedWithoutAnswers() {
        assertNull(AnswerTotals().accuracyPercent)
        assertEquals(67, AnswerTotals(answers = 3, correct = 2).accuracyPercent)
    }
}
