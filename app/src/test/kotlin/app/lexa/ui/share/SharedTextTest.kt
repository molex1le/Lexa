package app.lexa.ui.share

import android.content.Intent
import org.junit.Test
import kotlin.test.assertEquals

class SharedTextTest {
    private fun process(text: CharSequence?) = SharedText.from(Intent.ACTION_PROCESS_TEXT, text, null)
    private fun send(text: CharSequence?) = SharedText.from(Intent.ACTION_SEND, null, text)

    @Test fun processTextReadsProcessExtra() {
        assertEquals(SharedText.Query("commute"), SharedText.from(Intent.ACTION_PROCESS_TEXT, "commute", "other"))
    }

    @Test fun sendReadsTextExtra() {
        assertEquals(SharedText.Query("commute"), SharedText.from(Intent.ACTION_SEND, "other", "commute"))
    }

    @Test fun otherActionIsEmpty() {
        assertEquals(SharedText.Empty, SharedText.from(Intent.ACTION_VIEW, "commute", "commute"))
        assertEquals(SharedText.Empty, SharedText.from(null, "commute", null))
    }

    @Test fun trimsWhitespaceAndNewlines() {
        assertEquals(SharedText.Query("commute"), process("  \n commute\t "))
        assertEquals(SharedText.Query("give up"), process("give \n  up"))
    }

    @Test fun stripsPunctuationAroundSelection() {
        assertEquals(SharedText.Query("commute"), process("commute,"))
        assertEquals(SharedText.Query("commute"), send("«commute»"))
        assertEquals(SharedText.Query("look forward to"), process("(look forward to)."))
        assertEquals(SharedText.Query("don't"), process("\"don't\""))
    }

    @Test fun shortPhraseIsAQuery() {
        assertEquals(SharedText.Query("by and large"), send("by and large"))
        assertEquals(SharedText.Query("one two three four five"), process("one two three four five"))
    }

    @Test fun tooManyWordsIsTooLong() {
        assertEquals(SharedText.TooLong, process("one two three four five six"))
    }

    @Test fun tooManyCharsIsTooLong() {
        val long = "a".repeat(SharedText.MAX_CHARS + 1)
        assertEquals(SharedText.TooLong, process(long))
        assertEquals(SharedText.Query("a".repeat(SharedText.MAX_CHARS)), process("a".repeat(SharedText.MAX_CHARS)))
    }

    @Test fun severalSentencesAreTooLong() {
        assertEquals(SharedText.TooLong, send("It rains. Take it"))
        assertEquals(SharedText.TooLong, process("Really? Yes"))
    }

    @Test fun linkIsTooLong() {
        assertEquals(SharedText.TooLong, send("https://example.com/word"))
    }

    @Test fun blankOrPunctuationOnlyIsEmpty() {
        assertEquals(SharedText.Empty, process(null))
        assertEquals(SharedText.Empty, send("   "))
        assertEquals(SharedText.Empty, process("…!"))
    }
}
