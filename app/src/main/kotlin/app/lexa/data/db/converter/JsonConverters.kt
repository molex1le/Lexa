package app.lexa.data.db.converter

import androidx.room.TypeConverter
import app.lexa.data.db.entity.ExampleJson
import app.lexa.data.db.entity.TranslationJson
import kotlinx.serialization.json.Json

/** Lists inside a word are stored as JSON text columns. */
class JsonConverters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun translationsToJson(value: List<TranslationJson>): String = json.encodeToString(value)

    @TypeConverter
    fun translationsFromJson(value: String): List<TranslationJson> = json.decodeFromString(value)

    @TypeConverter
    fun examplesToJson(value: List<ExampleJson>): String = json.encodeToString(value)

    @TypeConverter
    fun examplesFromJson(value: String): List<ExampleJson> = json.decodeFromString(value)

    @TypeConverter
    fun stringsToJson(value: List<String>): String = json.encodeToString(value)

    @TypeConverter
    fun stringsFromJson(value: String): List<String> = json.decodeFromString(value)
}
