package app.lexa.data.repository

import androidx.room.withTransaction
import app.lexa.data.db.LexaDatabase
import app.lexa.data.db.entity.ExampleJson
import app.lexa.data.db.entity.ExampleSource
import app.lexa.data.db.entity.TranslationJson
import app.lexa.data.db.entity.WordEntity
import app.lexa.data.db.entity.WordSource
import app.lexa.domain.leitner.LeitnerRules
import app.lexa.domain.model.ExampleOrigin
import app.lexa.domain.model.Translation
import app.lexa.domain.repository.AddedWord
import app.lexa.domain.repository.TranslatedWordRepository
import app.lexa.domain.text.normalizeKey
import app.lexa.domain.time.TimeProvider
import javax.inject.Inject

/** Auto-add from the translator (docs/ARCHITECTURE.md §10 `LookupAndAddWord`): box 1, due today, no duplicates. */
class RoomTranslatedWordRepository @Inject constructor(
    private val db: LexaDatabase,
    private val time: TimeProvider,
) : TranslatedWordRepository {
    private val dao get() = db.wordDao()

    override suspend fun addFromTranslation(translation: Translation): AddedWord = db.withTransaction {
        val key = normalizeKey(translation.term)
        val existing = dao.findByKey(key)
        if (existing != null) {
            if (existing.deletedAt != null) dao.restore(existing.id)
            return@withTransaction AddedWord(existing.id, isNew = false, isFavorite = existing.isFavorite)
        }
        val now = time.nowMillis()
        val translations = translation.senses.flatMap { sense ->
            sense.translations.map { TranslationJson(text = it, pos = sense.pos?.name) }
        }.distinctBy { it.text }
        val id = dao.insert(
            WordEntity(
                term = translation.term,
                termKey = key,
                translations = translations,
                definition = translation.definition,
                ipa = translation.ipa,
                audioUrl = translation.audioUrl,
                examples = translation.examples.map {
                    ExampleJson(en = it.en, ru = it.ru, source = it.origin.toEntity())
                },
                synonyms = translation.synonyms,
                box = LeitnerRules.FIRST_BOX,
                nextReviewAt = time.today().atStartOfDay(time.zone).toInstant().toEpochMilli(),
                addedAt = now,
                source = WordSource.TRANSLATOR,
                searchText = normalizeKey(translation.term + " " + translations.joinToString(" ") { it.text }),
            ),
        )
        AddedWord(id, isNew = true, isFavorite = false)
    }

    override suspend fun undoAdd(wordId: Long) = dao.deleteById(wordId)

    override suspend fun setFavorite(wordId: Long, favorite: Boolean) = dao.setFavorite(wordId, favorite)

    private fun ExampleOrigin.toEntity() = when (this) {
        ExampleOrigin.TATOEBA -> ExampleSource.TATOEBA
        ExampleOrigin.DICTIONARY -> ExampleSource.DICTIONARY
        ExampleOrigin.MACHINE -> ExampleSource.MACHINE
    }
}
