package app.lexa.data.dictionary

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HeadwordTest {
    @Test fun `machine output becomes a bare lower-case headword`() {
        assertEquals("run", englishHeadword("To run."))
        assertEquals("reluctant", englishHeadword(" Reluctant "))
        assertEquals("ice cream", englishHeadword("Ice cream!"))
        assertNull(englishHeadword(" . "))
    }
}
