package app.lexa.data.repository

import android.content.Context
import androidx.room.withTransaction
import app.lexa.data.db.LexaDatabase
import app.lexa.data.db.entity.DeckSeenEntity
import app.lexa.data.db.entity.ReviewDirection
import app.lexa.data.db.entity.ReviewLogEntity
import app.lexa.data.db.entity.ReviewOutcome
import app.lexa.data.db.entity.TranslationJson
import app.lexa.data.db.entity.WordEntity
import app.lexa.data.db.entity.WordSource
import app.lexa.domain.model.VocabLevel
import app.lexa.domain.text.normalizeKey
import app.lexa.domain.time.TimeProvider
import app.lexa.domain.training.AnswerChecker
import app.lexa.domain.training.DeckRepository
import app.lexa.domain.training.DeckWord
import app.lexa.domain.training.ReviewRecord
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Parses `level<TAB>term<TAB>translations` lines; blank and malformed lines are skipped. */
internal fun parseDeck(text: String): List<DeckWord> = text.lineSequence().mapNotNull { line ->
    val parts = line.split('\t')
    if (parts.size != 3) return@mapNotNull null
    val level = runCatching { VocabLevel.valueOf(parts[0].trim()) }.getOrNull() ?: return@mapNotNull null
    DeckWord(level, parts[1].trim(), parts[2].trim()).takeIf { it.term.isNotEmpty() && it.translations.isNotEmpty() }
}.toList()

/** The built-in deck from assets/deck_words.tsv, with progress in the deck_seen table. */
@Singleton
class AssetDeckRepository internal constructor(
    private val load: suspend () -> String,
    private val db: LexaDatabase,
    private val time: TimeProvider,
    private val random: Random,
) : DeckRepository {

    @Inject constructor(@ApplicationContext context: Context, db: LexaDatabase, time: TimeProvider) : this(
        load = { withContext(Dispatchers.IO) { context.assets.open(ASSET).bufferedReader().use { it.readText() } } },
        db = db,
        time = time,
        random = Random.Default,
    )

    private val mutex = Mutex()
    private var cache: List<DeckWord>? = null

    private suspend fun words(): List<DeckWord> = mutex.withLock { cache ?: parseDeck(load()).also { cache = it } }

    override suspend fun randomUnseen(level: VocabLevel, count: Int): List<DeckWord> {
        if (count <= 0) return emptyList()
        val seen = db.deckDao().seenTerms().toSet()
        val inList = db.wordDao().allTermKeys().toSet()
        val fresh = words().filter { it.term !in seen && normalizeKey(it.term) !in inList }
        // This level first; harder ones only once it is used up.
        return level.andAbove().asSequence()
            .flatMap { l -> fresh.filter { it.level == l }.shuffled(random) }
            .take(count)
            .toList()
    }

    override suspend fun sample(perLevel: Int): Map<VocabLevel, List<DeckWord>> =
        words().groupBy { it.level }.mapValues { (_, list) -> list.shuffled(random).take(perLevel) }

    override suspend fun markKnown(term: String) {
        db.deckDao().upsert(DeckSeenEntity(term, known = true, seenAt = time.nowMillis()))
    }

    override suspend fun addAsNew(word: DeckWord, record: ReviewRecord): Pair<Long, Long> = db.withTransaction {
        val now = record.answeredAt
        val translations = AnswerChecker.candidates(listOf(word.translations)).map { TranslationJson(text = it) }
        val wordId = db.wordDao().insert(
            WordEntity(
                term = word.term,
                termKey = normalizeKey(word.term),
                translations = translations,
                box = 1,
                nextReviewAt = now,
                mistakesCount = 1,
                addedAt = now,
                lastReviewedAt = now,
                source = WordSource.DECK,
                searchText = normalizeKey(word.term + " " + translations.joinToString(" ") { it.text }),
            ),
        )
        val logId = db.reviewLogDao().insert(
            ReviewLogEntity(
                wordId = wordId,
                answeredAt = now,
                localDay = record.localDay,
                direction = ReviewDirection.valueOf(record.direction.name),
                userAnswer = record.userAnswer,
                outcome = ReviewOutcome.WRONG,
                isCorrect = false,
                fromBox = record.fromBox,
                toBox = record.toBox,
                wasDue = false,
            ),
        )
        db.deckDao().upsert(DeckSeenEntity(word.term, known = false, seenAt = now))
        wordId to logId
    }

    override suspend fun undoAddAsNew(wordId: Long, term: String) = db.withTransaction {
        // The review_log row goes with the word (ON DELETE CASCADE).
        db.wordDao().deleteById(wordId)
        db.deckDao().upsert(DeckSeenEntity(term, known = true, seenAt = time.nowMillis()))
    }

    companion object {
        const val ASSET = "deck_words.tsv"
    }
}
