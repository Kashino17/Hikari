package com.hikari.app.ui.russian

import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.hikari.app.player.MusicPlayerController
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Spielt die Offline-Aussprache-Clips aus assets/russian/audio (Opus).
 * Eigener, kleiner ExoPlayer — unabhängig vom Musik-Player; läuft dort
 * gerade Musik, wird sie beim ersten Clip pausiert (Sprache über Musik
 * versteht man nicht).
 */
@Singleton
class RussianAudioPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val music: MusicPlayerController,
) {
    private var player: ExoPlayer? = null
    private var onDone: (() -> Unit)? = null

    private val _playing = MutableStateFlow<String?>(null)
    /** Name des gerade laufenden Clips (für Animationen), sonst null. */
    val playing: StateFlow<String?> = _playing.asStateFlow()

    private val available: Set<String> by lazy {
        context.assets.list("russian/audio")?.toSet().orEmpty()
    }

    /**
     * Beim Öffnen des Bereichs aufrufen: Dateiliste im Hintergrund lesen und
     * den Player bauen, bevor der erste Tipp kommt — sonst hängt der erste
     * Klick spürbar (Player-Aufbau ~0,5 s auf langsamen Geräten).
     */
    suspend fun warmUp() {
        withContext(Dispatchers.IO) { available.size }
        ensure()
    }

    fun has(name: String): Boolean = "$name.ogg" in available

    private fun ensure(): ExoPlayer = player ?: buildPlayer()

    private fun buildPlayer(): ExoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                .build(),
            /* handleAudioFocus = */ false,
        )
        .build().also { p ->
            p.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED) finish()
                }

                override fun onPlayerError(error: PlaybackException) {
                    Log.w(TAG, "Audio-Fehler: ${error.errorCodeName}")
                    finish()
                }
            })
            player = p
        }

    private fun finish() {
        _playing.value = null
        val cb = onDone
        onDone = null
        cb?.invoke()
    }

    /** Clip abspielen; [slow] nimmt die langsam gesprochene Aufnahme. */
    fun play(name: String, slow: Boolean = false, onComplete: (() -> Unit)? = null) {
        val file = if (slow && has("${name}_slow")) "${name}_slow" else name
        if (!has(file)) {
            Log.w(TAG, "Audio fehlt: $file")
            onComplete?.invoke()
            return
        }
        playUri("asset:///russian/audio/$file.ogg", name, onComplete)
    }

    /** Eigene Aufnahme abspielen. */
    fun playFile(file: File, onComplete: (() -> Unit)? = null) {
        if (!file.exists()) return
        playUri(file.toURI().toString(), "recording", onComplete)
    }

    private fun playUri(uri: String, tag: String, onComplete: (() -> Unit)?) {
        if (music.isPlaying.value) music.toggle()
        val p = ensure()
        // Ein laufender Clip wird abgelöst — sein Callback verfällt.
        onDone = null
        p.stop()
        onDone = onComplete
        _playing.value = tag
        p.setMediaItem(MediaItem.fromUri(uri))
        p.prepare()
        p.play()
    }

    fun stop() {
        onDone = null
        player?.stop()
        _playing.value = null
    }

    private companion object {
        const val TAG = "RussianAudio"
    }
}

/** Nimmt die eigene Stimme auf, um sie mit dem Original zu vergleichen. */
class RussianRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    val file: File = File(context.cacheDir, "russian_take.m4a")

    fun start(): Boolean = runCatching {
        stop()
        file.delete()
        @Suppress("DEPRECATION")
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioSamplingRate(44_100)
        r.setAudioEncodingBitRate(96_000)
        r.setOutputFile(file.absolutePath)
        r.prepare()
        r.start()
        recorder = r
        true
    }.getOrElse {
        Log.w("RussianRecorder", "Aufnahme fehlgeschlagen", it)
        recorder = null
        false
    }

    fun stop() {
        recorder?.let { r ->
            runCatching { r.stop() }
            r.release()
        }
        recorder = null
    }
}

/** Zustand der Spracherkennung für eine Sprechübung. */
sealed interface RuListenState {
    data object Idle : RuListenState
    data object Listening : RuListenState
    data class Partial(val text: String) : RuListenState
    data class Done(val hypotheses: List<String>) : RuListenState
    data class Failed(val message: String, val missingLanguage: Boolean = false) : RuListenState
}

/**
 * Android-Spracherkennung auf Russisch (kostenlos, auf den meisten Geräten
 * auch offline mit installiertem Sprachpaket). Muss auf dem Main-Thread
 * erzeugt und benutzt werden.
 */
class RussianSpeechRecognizer(private val context: Context) {
    private val _state = MutableStateFlow<RuListenState>(RuListenState.Idle)
    val state: StateFlow<RuListenState> = _state.asStateFlow()
    private var recognizer: SpeechRecognizer? = null
    private var userStopped: Boolean = false
    private var lastHypotheses: List<String> = emptyList()
    private var lastPartialText: String? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var stopWatchdog: Runnable? = null

    val available: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)

    private fun clearWatchdog() {
        stopWatchdog?.let { mainHandler.removeCallbacks(it) }
        stopWatchdog = null
    }

    fun start() {
        if (!available) {
            _state.value = RuListenState.Failed("Keine Spracherkennung auf diesem Gerät gefunden.")
            return
        }
        destroy()
        userStopped = false
        lastHypotheses = emptyList()
        lastPartialText = null

        val r = runCatching { SpeechRecognizer.createSpeechRecognizer(context) }.getOrNull()
        if (r == null) {
            _state.value = RuListenState.Failed("Spracherkennung konnte nicht initialisiert werden.")
            return
        }

        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _state.value = RuListenState.Listening
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onPartialResults(partialResults: Bundle?) {
                val list = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                val t = list.firstOrNull()
                if (!t.isNullOrBlank()) {
                    lastPartialText = t
                    lastHypotheses = list
                    _state.value = RuListenState.Partial(t)
                }
            }

            override fun onResults(results: Bundle?) {
                clearWatchdog()
                val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                val finalResults = if (list.isNotEmpty()) {
                    list
                } else if (lastHypotheses.isNotEmpty()) {
                    lastHypotheses
                } else if (!lastPartialText.isNullOrBlank()) {
                    listOf(lastPartialText!!)
                } else {
                    emptyList()
                }
                _state.value = RuListenState.Done(finalResults)
            }

            override fun onError(error: Int) {
                clearWatchdog()
                // Wenn der User auf Stopp/Absenden getippt hat oder bereits Text gehört wurde:
                if (userStopped || !lastPartialText.isNullOrBlank() || lastHypotheses.isNotEmpty()) {
                    val fallback = if (lastHypotheses.isNotEmpty()) {
                        lastHypotheses
                    } else if (!lastPartialText.isNullOrBlank()) {
                        listOf(lastPartialText!!)
                    } else {
                        emptyList()
                    }
                    _state.value = RuListenState.Done(fallback)
                    return
                }

                // Bei manuellem Client-Stopp oder No-Match ohne Sprache: als Done(leer) werten, nie abbrechen!
                _state.value = when (error) {
                    SpeechRecognizer.ERROR_CLIENT,
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                        RuListenState.Done(emptyList())
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                        RuListenState.Failed("Mikrofon-Berechtigung fehlt.")
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER ->
                        RuListenState.Failed("Die Spracherkennung braucht Internet oder das russische Offline-Sprachpaket.", missingLanguage = true)
                    12, 13 -> // ERROR_LANGUAGE_NOT_SUPPORTED / ERROR_LANGUAGE_UNAVAILABLE (API 31)
                        RuListenState.Failed("Russisch ist in der Spracherkennung nicht installiert.", missingLanguage = true)
                    else -> RuListenState.Failed("Spracherkennung fehlgeschlagen (Code $error).")
                }
            }
        })
        recognizer = r
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // Stille-Erkennung für automatisches Fertigstellen
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
        }
        _state.value = RuListenState.Listening
        runCatching { r.startListening(intent) }.onFailure { e ->
            Log.w("RussianSpeechRecognizer", "startListening failed", e)
            _state.value = RuListenState.Failed("Spracherkennung konnte nicht gestartet werden.")
        }
    }

    fun stopListening() {
        userStopped = true
        val partial = lastPartialText
        val hypotheses = lastHypotheses

        // Watchdog: Falls das OS nach stopListening() weder onResults noch onError aufruft,
        // nach 2.5s garantiert mit den bisherigen Erkenntnissen abschließen.
        clearWatchdog()
        val watchdog = Runnable {
            if (_state.value is RuListenState.Listening || _state.value is RuListenState.Partial) {
                val fallback = if (lastHypotheses.isNotEmpty()) {
                    lastHypotheses
                } else if (!lastPartialText.isNullOrBlank()) {
                    listOf(lastPartialText!!)
                } else {
                    emptyList()
                }
                _state.value = RuListenState.Done(fallback)
            }
        }
        stopWatchdog = watchdog
        mainHandler.postDelayed(watchdog, 2500L)

        runCatching { recognizer?.stopListening() }.onFailure {
            clearWatchdog()
            if (hypotheses.isNotEmpty()) {
                _state.value = RuListenState.Done(hypotheses)
            } else if (!partial.isNullOrBlank()) {
                _state.value = RuListenState.Done(listOf(partial))
            } else {
                _state.value = RuListenState.Done(emptyList())
            }
        }
    }

    fun reset() {
        clearWatchdog()
        userStopped = false
        lastHypotheses = emptyList()
        lastPartialText = null
        _state.value = RuListenState.Idle
    }

    fun destroy() {
        clearWatchdog()
        runCatching { recognizer?.destroy() }
        recognizer = null
        userStopped = false
        lastHypotheses = emptyList()
        lastPartialText = null
    }
}
