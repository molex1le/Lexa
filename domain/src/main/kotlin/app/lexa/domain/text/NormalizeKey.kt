package app.lexa.domain.text

import java.text.Normalizer
import java.util.Locale

private val whitespace = Regex("\\s+")

/**
 * Key for de-duplication and search (docs/ARCHITECTURE.md §6): NFC, lower case, ё → е,
 * collapsed whitespace, trimmed.
 */
fun normalizeKey(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFC)
        .lowercase(Locale.ROOT)
        .replace('ё', 'е')
        .replace(whitespace, " ")
        .trim()
