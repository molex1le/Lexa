package app.lexa.domain.translate

import app.lexa.domain.model.DictionaryData
import app.lexa.domain.model.Example
import app.lexa.domain.model.ExampleOrigin
import app.lexa.domain.model.PartOfSpeech
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import org.junit.Test

class TranslationAssemblerTest {

    private val calls = mutableListOf<String>()
    private val dictionaryMt = mapOf(
        "to run" to "Чтобы бежать.",
        "a run" to "Бег",
        "run" to "бежать",
        "To move swiftly." to "Быстро двигаться.",
        "A jog." to "Пробежка.",
        "I run every day." to "Я бегаю каждый день.",
    )
    private val assembler = TranslationAssembler { text -> calls += text; dictionaryMt[text] }

    private val run = DictionaryData(
        term = "run",
        ipa = "/ɹʌn/",
        audioUrl = "https://example.org/run-us.mp3",
        meanings = listOf(
            DictionaryData.Meaning(PartOfSpeech.VERB, listOf("To move swiftly."), listOf("I run every day."), listOf("sprint", "Run")),
            DictionaryData.Meaning(PartOfSpeech.NOUN, listOf("A jog."), synonyms = listOf("sprint", "jog")),
            DictionaryData.Meaning(PartOfSpeech.ADJECTIVE, listOf("Melted.")),
        ),
    )

    @Test fun `translates each part of speech through its frame and cleans the result`() = runTest {
        val t = assembler.assemble("Run", run, emptyList())!!
        assertEquals("run", t.term)
        // «run» as adjective gives «бежать» again: the duplicate is dropped, and with no gloss the sense goes too
        assertEquals(listOf(PartOfSpeech.VERB, PartOfSpeech.NOUN), t.senses.map { it.pos })
        assertEquals(listOf("бежать"), t.senses[0].translations) // «Чтобы бежать.» → «бежать»
        assertEquals(listOf("бег"), t.senses[1].translations)
        assertEquals("Быстро двигаться.", t.senses[0].gloss)
        assertEquals("To move swiftly.", t.definition)
        assertEquals("/ɹʌn/", t.ipa)
        assertEquals(listOf("sprint", "jog"), t.synonyms) // the term itself is not its own synonym
    }

    @Test fun `human examples come first, machine ones fill up to two and are marked`() = runTest {
        val human = listOf(Example("I run to work.", "Я бегу на работу.", ExampleOrigin.TATOEBA))
        val t = assembler.assemble("run", run, human)!!
        assertEquals(
            listOf(human[0], Example("I run every day.", "Я бегаю каждый день.", ExampleOrigin.MACHINE)),
            t.examples,
        )
    }

    @Test fun `two human examples are enough, no machine translation of examples`() = runTest {
        val human = List(2) { Example("Run $it", "Беги $it", ExampleOrigin.TATOEBA) }
        val t = assembler.assemble("run", run, human)!!
        assertEquals(human, t.examples)
        assertEquals(false, "I run every day." in calls)
    }

    @Test fun `unknown word is translated whole by the machine`() = runTest {
        val t = TranslationAssembler { if (it == "run amok") "Взбеситься." else null }
            .assemble(" run amok ", dictionary = null, sentences = emptyList())!!
        assertEquals("run amok", t.term)
        assertEquals(1, t.senses.size)
        assertNull(t.senses[0].pos)
        assertEquals(listOf("взбеситься"), t.senses[0].translations)
        assertNull(t.ipa)
    }

    @Test fun `nothing from any source gives null`() = runTest {
        assertNull(TranslationAssembler { null }.assemble("qwzx", null, emptyList()))
    }
}
