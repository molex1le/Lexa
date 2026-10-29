package app.lexa.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.contentLength
import io.ktor.utils.io.readAvailable
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** One GitHub release with its APK. [notes] is the tag message («что нового»). */
data class Release(val version: String, val notes: String, val apkUrl: String, val size: Long)

/** «0.10» is newer than «0.9»: numbers, not strings. */
fun isNewer(candidate: String, installed: String): Boolean {
    val a = candidate.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
    val b = installed.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}

/** `releases/latest` JSON → [Release]; null when the release has no APK attached. */
fun parseRelease(json: Json, body: String): Release? {
    val root = json.parseToJsonElement(body).jsonObject
    val apk = root["assets"]?.jsonArray?.map { it.jsonObject }
        ?.firstOrNull { it["name"]?.jsonPrimitive?.contentOrNull?.endsWith(".apk") == true } ?: return null
    return Release(
        version = root["tag_name"]?.jsonPrimitive?.contentOrNull?.removePrefix("v") ?: return null,
        notes = root["body"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty(),
        apkUrl = apk["browser_download_url"]?.jsonPrimitive?.contentOrNull ?: return null,
        size = apk["size"]?.jsonPrimitive?.longOrNull ?: 0,
    )
}

/**
 * Updates from the public releases of github.com/molex1le/Lexa, the same way UniTime does it:
 * a tag builds a signed APK on GitHub, the app sees the newer release, downloads it and hands it to
 * the system installer (one tap to confirm).
 */
@Singleton
class AppUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: HttpClient,
    private val json: Json,
) {
    val installedVersion: String
        get() = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull()?.substringBefore("-") ?: "0"

    /** The latest release if it is newer than this build; null when it isn't or GitHub can't be reached. */
    suspend fun newerRelease(): Release? = try {
        val response = client.get(LATEST) {
            header("Accept", "application/vnd.github+json")
            timeout { requestTimeoutMillis = 15_000 }
        }
        if (response.status.value != 200) null
        else parseRelease(json, response.bodyAsText())?.takeIf { isNewer(it.version, installedVersion) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Update check failed", e)
        null
    }

    /** Downloads the APK into the app cache; [progress] runs 0..1. Throws if the file comes incomplete. */
    suspend fun download(release: Release, progress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, "Lexa-${release.version}.apk")
        client.prepareGet(release.apkUrl) { timeout { requestTimeoutMillis = 10 * 60_000 } }.execute { response ->
            check(response.status.value == 200) { "HTTP ${response.status.value}" }
            val total = response.contentLength()?.takeIf { it > 0 } ?: release.size
            val channel = response.bodyAsChannel()
            out.outputStream().use { file ->
                val buffer = ByteArray(64 * 1024)
                var done = 0L
                while (true) {
                    val read = channel.readAvailable(buffer)
                    if (read < 0) break
                    if (read == 0) { if (channel.isClosedForRead) break else continue }
                    file.write(buffer, 0, read)
                    done += read
                    if (total > 0) progress((done.toFloat() / total).coerceAtMost(1f))
                }
            }
        }
        check(release.size <= 0 || out.length() == release.size) { "file incomplete" }
        out
    }

    /**
     * Opens the system installer. If Lexa may not install apps yet, opens that switch in the system
     * settings instead and returns false – the learner comes back and taps «Обновить» again.
     */
    fun install(apk: File): Boolean {
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        return true
    }

    private companion object {
        const val LATEST = "https://api.github.com/repos/molex1le/Lexa/releases/latest"
        const val TAG = "Lexa.Update"
    }
}
