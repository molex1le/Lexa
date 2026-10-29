package app.lexa.data.db.entity

/** Where a word came from. Stored by name. */
/** DECK: a built-in word the learner did not know in training. */
enum class WordSource { TRANSLATOR, PROCESS_TEXT, SHARE, MANUAL, DECK }

enum class ReviewDirection { EN_RU, RU_EN }

/** OVERRIDDEN is «Я знал, это опечатка». */
enum class ReviewOutcome { CORRECT, CORRECT_TYPO, WRONG, OVERRIDDEN }

enum class CacheStatus { OK, NOT_FOUND }

enum class EntryPoint { APP, PROCESS_TEXT, SHARE }

enum class ExampleSource { TATOEBA, DICTIONARY, MACHINE, LLM }
