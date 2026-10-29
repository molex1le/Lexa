package app.lexa.ui.share

import android.content.Intent

/** What the text selection menu or «Поделиться» handed to Lexa (docs/ARCHITECTURE.md §12). */
sealed interface SharedText {
    data class Query(val text: String) : SharedText
    /** Several sentences, more than [MAX_WORDS] words or [MAX_CHARS] characters, or a link. */
    data object TooLong : SharedText
    data object Empty : SharedText

    companion object {
        const val MAX_WORDS = 5
        const val MAX_CHARS = 60

        /** Quotes, brackets and sentence punctuation that come along with a selected word. */
        private const val EDGE_PUNCTUATION = "\"'«»„“”‘’()[]{}.,;:!?…–—-"
        private val sentenceBreak = Regex("""[.!?…]\s+\S""")
        private val whitespace = Regex("""\s+""")

        /**
         * [processText] is `EXTRA_PROCESS_TEXT` (used for [Intent.ACTION_PROCESS_TEXT]), [sendText] is
         * `EXTRA_TEXT` (used for [Intent.ACTION_SEND]); any other action gives [Empty].
         */
        fun from(action: String?, processText: CharSequence?, sendText: CharSequence?): SharedText {
            val raw = when (action) {
                Intent.ACTION_PROCESS_TEXT -> processText
                Intent.ACTION_SEND -> sendText
                else -> null
            }?.toString()?.trim().orEmpty()
            if (raw.isEmpty()) return Empty
            if ("://" in raw || sentenceBreak.containsMatchIn(raw)) return TooLong
            val text = raw.replace(whitespace, " ").trim { it in EDGE_PUNCTUATION || it.isWhitespace() }
            return when {
                text.isEmpty() -> Empty
                text.length > MAX_CHARS || text.split(' ').size > MAX_WORDS -> TooLong
                else -> Query(text)
            }
        }
    }
}
