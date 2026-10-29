package app.lexa.data.dictionary

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import app.lexa.domain.repository.MachineTranslator
import app.lexa.domain.repository.NetworkStatus
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}

/**
 * ML Kit on-device EN→RU (ADR-0001). One client for the app; calls are serialized.
 * The ~30 MB model downloads on any network: the translator is useless without it.
 */
@Singleton
class MlKitMachineTranslator @Inject constructor() : MachineTranslator {
    private val client by lazy {
        Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(TranslateLanguage.RUSSIAN)
                .build(),
        )
    }
    private val model = TranslateRemoteModel.Builder(TranslateLanguage.RUSSIAN).build()
    private val mutex = Mutex()

    override suspend fun isModelReady(): Boolean = guarded(false) {
        RemoteModelManager.getInstance().isModelDownloaded(model).await()
    }

    override suspend fun downloadModel(): Boolean = guarded(false) {
        client.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
        true
    }

    override suspend fun translate(text: String): String? = guarded(null) {
        mutex.withLock { client.translate(text).await() }
    }

    private inline fun <T> guarded(fallback: T, block: () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        fallback
    }
}

class AndroidNetworkStatus @Inject constructor(@ApplicationContext private val context: Context) : NetworkStatus {
    override fun isOnline(): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
