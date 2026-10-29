package app.lexa.ui.translator

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject

/**
 * Plays the dictionary recording if there is one, otherwise speaks the word with TextToSpeech en-US
 * (docs/ARCHITECTURE.md §10). Owned by one ViewModel; [release] in onCleared.
 */
class Pronouncer @Inject constructor(@ApplicationContext private val context: Context) {
    private var player: MediaPlayer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pending: String? = null

    fun play(term: String, audioUrl: String?) {
        if (audioUrl.isNullOrBlank()) speak(term) else playUrl(audioUrl, term)
    }

    private fun playUrl(url: String, term: String) {
        player?.release()
        player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            setOnPreparedListener { it.start() }
            setOnErrorListener { _, _, _ -> speak(term); true }
            runCatching {
                setDataSource(url)
                prepareAsync()
            }.onFailure { speak(term) }
        }
    }

    private fun speak(term: String) {
        val engine = tts
        if (engine != null && ttsReady) {
            engine.speak(term, TextToSpeech.QUEUE_FLUSH, null, term)
            return
        }
        pending = term
        if (engine == null) {
            tts = TextToSpeech(context) { status ->
                ttsReady = status == TextToSpeech.SUCCESS
                if (ttsReady) {
                    tts?.language = Locale.US
                    pending?.let { tts?.speak(it, TextToSpeech.QUEUE_FLUSH, null, it) }
                }
                pending = null
            }
        }
    }

    fun release() {
        player?.release()
        player = null
        tts?.shutdown()
        tts = null
        ttsReady = false
    }
}
