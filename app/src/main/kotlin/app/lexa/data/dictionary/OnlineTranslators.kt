package app.lexa.data.dictionary

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import app.lexa.domain.repository.MachineTranslator

/**
 * MyMemory (api.mymemory.translated.net): free EN→RU without a key, ~5000 characters a day per
 * device. Used only while the ML Kit model is not on the phone.
 */
class MyMemoryTranslator @Inject constructor(
    private val client: HttpClient,
    private val json: Json,
) {
    suspend fun translate(text: String): String? = try {
        val response = client.get("https://api.mymemory.translated.net/get") {
            parameter("q", text)
            parameter("langpair", "en|ru")
        }
        if (response.status.value != 200) null else parse(response.bodyAsText())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "MyMemory failed", e)
        null
    }

    internal fun parse(body: String): String? {
        val root = json.parseToJsonElement(body).jsonObject
        if (root["responseStatus"]?.jsonPrimitive?.intOrNull != 200) return null
        val text = root["responseData"]?.jsonObject?.get("translatedText")?.jsonPrimitive?.contentOrNull
        // The service answers quota and query errors with an upper-case English message in the same field.
        return text?.trim()?.takeIf { it.isNotEmpty() && !it.startsWith("MYMEMORY WARNING") && !it.startsWith("QUERY LENGTH") }
    }

    private companion object { const val TAG = "Lexa.MyMemory" }
}

/**
 * ML Kit first; while its Russian model is missing (download failed or not yet tried), translate
 * online through [MyMemoryTranslator] and keep retrying the model on the next lookups.
 * [downloadModel] reports success when either path can translate, so the translator never blocks
 * on a model that will not download.
 */
@Singleton
class ResilientMachineTranslator @Inject constructor(
    private val onDevice: MlKitMachineTranslator,
    private val online: MyMemoryTranslator,
) : MachineTranslator {

    override suspend fun isModelReady(): Boolean = onDevice.isModelReady()

    override suspend fun downloadModel(): Boolean {
        if (onDevice.downloadModel()) return true
        Log.w(TAG, "ML Kit model download failed, translating online until it succeeds")
        return true
    }

    override suspend fun translate(text: String): String? =
        (if (onDevice.isModelReady()) onDevice.translate(text) else null) ?: online.translate(text)

    private companion object { const val TAG = "Lexa.Translator" }
}
