package com.hikari.app.ui.russian

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.os.Handler
import android.os.Looper
import java.util.Locale

/** Dynamic speech uses installed Russian TTS; it is not a native-speaker recording or pronunciation grade. */
class RussianConversationVoice(context: Context) {
    var ready by mutableStateOf(false); private set
    var speaking by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    private val main = Handler(Looper.getMainLooper())
    private var closed = false
    private var initialized = false
    private var tts: TextToSpeech? = null
    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            main.post {
                if (!closed) {
                    initialized = true
                    ready = status == TextToSpeech.SUCCESS && (tts?.setLanguage(Locale("ru", "RU")) ?: -1) >= 0
                }
            }
        }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) { main.post { if (!closed) speaking = true } }
            override fun onDone(id: String?) { main.post { if (!closed) speaking = false } }
            @Deprecated("Android callback") override fun onError(id: String?) { main.post {
                if (!closed) { speaking = false; error = "Vorlesen hat gerade nicht geklappt. Du kannst es erneut versuchen oder mit dem Text weiterüben." }
            } }
        })
    }
    fun speak(text: String, speed: Float) {
        if (closed) return
        if (!ready) {
            error = if (initialized) "Die russische Stimme fehlt. Aktiviere Russisch in den Android-Einstellungen zur Sprachausgabe. Du kannst auch mit dem Text üben."
                else "Die Stimme wird noch vorbereitet. Tippe gleich noch einmal auf Anhören."
            return
        }
        error = null
        tts?.setSpeechRate(speed.coerceIn(0.6f, 1.1f))
        speaking = true
        if (tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ru-conversation") == TextToSpeech.ERROR) {
            speaking = false; error = "Sprachausgabe gerade nicht verfügbar."
        }
    }
    fun stop() { tts?.stop(); speaking = false }
    fun close() { closed = true; stop(); tts?.shutdown(); main.removeCallbacksAndMessages(null) }
}
