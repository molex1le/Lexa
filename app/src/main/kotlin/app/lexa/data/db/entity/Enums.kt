package app.lexa.data.db.entity

/** Where a word came from. Stored by name. */
enum class WordSource { TRANSLATOR, PROCESS_TEXT, SHARE, MANUAL }

enum class ReviewDirection { EN_RU, RU_EN }

/** OVERRIDDEN is «Я знал, это опечатка». */
enum class ReviewOutcome { CORRECT, CORRECT_TYPO, WRONG, OVERRIDDEN }

enum class CacheStatus { OK, NOT_FOUND }

enum class EntryPoint { APP, PROCESS_TEXT, SHARE }

enum class ExampleSource { TATOEBA, DICTIONARY, MACHINE, LLM }
