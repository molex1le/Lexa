package app.lexa.domain.training

import app.lexa.domain.model.VocabLevel
import kotlin.test.Test
import kotlin.test.assertEquals

class PlacementTestTest {
    @Test fun `nothing known gives A1`() = assertEquals(VocabLevel.A1, PlacementTest.level(emptyMap()))

    @Test fun `the highest level passed without gaps`() = assertEquals(
        VocabLevel.B1,
        PlacementTest.level(mapOf(VocabLevel.A1 to 5, VocabLevel.A2 to 4, VocabLevel.B1 to 3, VocabLevel.B2 to 2, VocabLevel.C1 to 4)),
    )

    @Test fun `a failed level stops the count even if a harder one passed`() = assertEquals(
        VocabLevel.A1,
        PlacementTest.level(mapOf(VocabLevel.A1 to 5, VocabLevel.A2 to 2, VocabLevel.B1 to 5)),
    )

    @Test fun `everything known gives C1`() =
        assertEquals(VocabLevel.C1, PlacementTest.level(VocabLevel.entries.associateWith { 5 }))

    @Test fun `andAbove lists the level and the harder ones`() =
        assertEquals(listOf(VocabLevel.B2, VocabLevel.C1), VocabLevel.B2.andAbove())
}
