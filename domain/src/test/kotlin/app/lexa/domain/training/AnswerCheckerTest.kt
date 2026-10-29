package app.lexa.domain.training

import app.lexa.domain.training.AnswerLanguage.ENGLISH
import app.lexa.domain.training.AnswerLanguage.RUSSIAN
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

enum class ExpectedVerdict { EXACT, TYPO, WRONG }

/** ARCHITECTURE §8.4: every rule, one row each. Candidates are split from stored strings as in the app. */
@RunWith(Parameterized::class)
class AnswerCheckerTest(
    private val name: String,
    private val answer: String,
    private val stored: List<String>,
    private val language: AnswerLanguage,
    private val verdict: ExpectedVerdict,
) {
    companion object {
        private fun row(name: String, answer: String, stored: List<String>, lang: AnswerLanguage, v: ExpectedVerdict) =
            arrayOf<Any>(name, answer, stored, lang, v)

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> = listOf(
            // normalization, English side (RU→EN cards expect the term)
            row("exact", "run", listOf("run"), ENGLISH, ExpectedVerdict.EXACT),
            row("case", "Run", listOf("run"), ENGLISH, ExpectedVerdict.EXACT),
            row("case in candidate", "give up", listOf("Give Up"), ENGLISH, ExpectedVerdict.EXACT),
            row("outer spaces", "  run  ", listOf("run"), ENGLISH, ExpectedVerdict.EXACT),
            row("inner spaces", "give    up", listOf("give up"), ENGLISH, ExpectedVerdict.EXACT),
            row("tabs and newline", "give\t up\n", listOf("give up"), ENGLISH, ExpectedVerdict.EXACT),
            row("to in answer", "to run", listOf("run"), ENGLISH, ExpectedVerdict.EXACT),
            row("to in candidate", "run", listOf("to run"), ENGLISH, ExpectedVerdict.EXACT),
            row("to both sides", "To give up", listOf("to give up"), ENGLISH, ExpectedVerdict.EXACT),
            row("to inside is kept", "go to bed", listOf("go bed"), ENGLISH, ExpectedVerdict.WRONG),
            row("toast is not to", "toast", listOf("ast"), ENGLISH, ExpectedVerdict.WRONG),
            row("articles are kept", "the house", listOf("house"), ENGLISH, ExpectedVerdict.WRONG),
            row("curly apostrophe", "don’t", listOf("don't"), ENGLISH, ExpectedVerdict.EXACT),
            row("backtick apostrophe", "don`t", listOf("don’t"), ENGLISH, ExpectedVerdict.EXACT),
            row("en dash", "well–known", listOf("well-known"), ENGLISH, ExpectedVerdict.EXACT),
            row("em dash", "well—known", listOf("well-known"), ENGLISH, ExpectedVerdict.EXACT),
            row("non-breaking hyphen", "well‑known", listOf("well-known"), ENGLISH, ExpectedVerdict.EXACT),
            row("edge punctuation", "run!", listOf("run"), ENGLISH, ExpectedVerdict.EXACT),
            row("quotes around", "«run».", listOf("run"), ENGLISH, ExpectedVerdict.EXACT),
            row("question mark", "  run? ", listOf("run"), ENGLISH, ExpectedVerdict.EXACT),
            // Russian side (EN→RU cards)
            row("yo in answer", "ёлка", listOf("елка"), RUSSIAN, ExpectedVerdict.EXACT),
            row("yo in candidate", "елка", listOf("ёлка"), RUSSIAN, ExpectedVerdict.EXACT),
            row("russian case", "Бежать", listOf("бежать"), RUSSIAN, ExpectedVerdict.EXACT),
            row("to not cut on russian side", "to бежать", listOf("бежать"), RUSSIAN, ExpectedVerdict.WRONG),
            row("any of several", "мчаться", listOf("бежать", "мчаться"), RUSSIAN, ExpectedVerdict.EXACT),
            row("comma list", "бегать", listOf("бежать, бегать"), RUSSIAN, ExpectedVerdict.EXACT),
            row("semicolon list", "мчаться", listOf("бежать; мчаться"), RUSSIAN, ExpectedVerdict.EXACT),
            row("slash list", "бегать", listOf("бежать/бегать"), RUSSIAN, ExpectedVerdict.EXACT),
            row("parentheses dropped", "ключ", listOf("ключ (от замка)"), RUSSIAN, ExpectedVerdict.EXACT),
            row("parenthesised text alone is wrong", "от замка", listOf("ключ (от замка)"), RUSSIAN, ExpectedVerdict.WRONG),
            row("not in list", "идти", listOf("бежать, бегать"), RUSSIAN, ExpectedVerdict.WRONG),
            // typos
            row("typo in 9 letters", "beautifl", listOf("beautiful"), ENGLISH, ExpectedVerdict.TYPO),
            row("typo in exactly 6 letters", "hpuses", listOf("houses"), ENGLISH, ExpectedVerdict.TYPO),
            row("missing letter, 6-letter word", "house", listOf("houses"), ENGLISH, ExpectedVerdict.TYPO),
            row("typo in 5 letters", "hous", listOf("house"), ENGLISH, ExpectedVerdict.WRONG),
            row("substitution in 5 letters", "hoose", listOf("house"), ENGLISH, ExpectedVerdict.WRONG),
            row("two typos", "beautfl", listOf("beautiful"), ENGLISH, ExpectedVerdict.WRONG),
            row("transposition costs 2", "recieve", listOf("receive"), ENGLISH, ExpectedVerdict.WRONG),
            row("typo in long word of phrase", "give upp", listOf("give up"), ENGLISH, ExpectedVerdict.WRONG),
            row("typo in phrase long word", "commutte home", listOf("commute home"), ENGLISH, ExpectedVerdict.TYPO),
            row("typos in two words", "commutte homme", listOf("commute home"), ENGLISH, ExpectedVerdict.WRONG),
            row("different word count", "give", listOf("give up"), ENGLISH, ExpectedVerdict.WRONG),
            row("russian typo, long word", "ездитъ", listOf("ездить"), RUSSIAN, ExpectedVerdict.TYPO),
            row("russian typo, 6 letters", "бижать", listOf("бежать"), RUSSIAN, ExpectedVerdict.TYPO),
            row("russian typo, 5 letters", "кошкя", listOf("кошка"), RUSSIAN, ExpectedVerdict.WRONG),
            row("russian 4 letters", "кот", listOf("кит"), RUSSIAN, ExpectedVerdict.WRONG),
            row("typo after to", "to beautifl", listOf("beautiful"), ENGLISH, ExpectedVerdict.TYPO),
            // empty
            row("empty", "", listOf("run"), ENGLISH, ExpectedVerdict.WRONG),
            row("blank", "   ", listOf("run"), ENGLISH, ExpectedVerdict.WRONG),
            row("only punctuation", "?!", listOf("run"), ENGLISH, ExpectedVerdict.WRONG),
        )
    }

    @Test fun check() {
        val result = AnswerChecker.check(answer, AnswerChecker.candidates(stored), language)
        when (verdict) {
            ExpectedVerdict.EXACT -> assertIs<CheckResult.Exact>(result, name)
            ExpectedVerdict.TYPO -> assertIs<CheckResult.AcceptedWithTypo>(result, name)
            ExpectedVerdict.WRONG -> assertIs<CheckResult.Wrong>(result, name)
        }
    }
}

class AnswerCheckerDetailsTest {
    @Test fun `exact match beats typo match in another candidate`() {
        val result = AnswerChecker.check("houses", listOf("housed", "houses"), ENGLISH)
        assertEquals(CheckResult.Exact("houses"), result)
    }

    @Test fun `typo result carries the correct spelling`() {
        assertEquals(CheckResult.AcceptedWithTypo("beautiful"), AnswerChecker.check("beautifl", listOf("beautiful"), ENGLISH))
    }

    @Test fun `wrong result lists all candidates`() {
        val expected = AnswerChecker.candidates(listOf("бежать, бегать", "мчаться"))
        assertEquals(listOf("бежать", "бегать", "мчаться"), expected)
        assertEquals(CheckResult.Wrong(expected), AnswerChecker.check("идти", expected, RUSSIAN))
    }

    @Test fun `candidates drop blanks and duplicates`() {
        assertEquals(listOf("ключ", "замок"), AnswerChecker.candidates(listOf("ключ (от замка); ; замок", "ключ")))
    }

    @Test fun `levenshtein distances`() {
        assertEquals(0, AnswerChecker.levenshtein("run", "run"))
        assertEquals(1, AnswerChecker.levenshtein("run", "ran"))
        assertEquals(2, AnswerChecker.levenshtein("recieve", "receive"))
        assertEquals(3, AnswerChecker.levenshtein("", "abc"))
        assertEquals(3, AnswerChecker.levenshtein("kitten", "sitting"))
    }
}
