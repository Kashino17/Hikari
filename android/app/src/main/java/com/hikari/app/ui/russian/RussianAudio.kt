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
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.hikari.app.domain.russian.RuDeepdiveLogic
import com.hikari.app.domain.russian.RuGender
import com.hikari.app.domain.russian.RuPatternLogic
import com.hikari.app.domain.russian.RuSettings
import com.hikari.app.domain.russian.RussianCourseRepository
import com.hikari.app.domain.russian.RussianPhonetics
import com.hikari.app.player.MusicPlayerController
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.Locale
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
    private val repo: RussianCourseRepository,
) {
    private var player: ExoPlayer? = null
    private var onDone: (() -> Unit)? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private val _playing = MutableStateFlow<String?>(null)
    /** Name des gerade laufenden Clips (für Animationen), sonst null. */
    val playing: StateFlow<String?> = _playing.asStateFlow()

    private val available: Set<String> by lazy {
        context.assets.list("russian/audio")?.toSet().orEmpty()
    }

    /**
     * Zuordnung aller Clip-Namen zum russischen Klartext, damit neue Lektionen (Tage 15–20),
     * neue Satzbau-Muster und selbst gebaute Sätze im Satzbuch nahtlos per russischer
     * Sprachausgabe (TTS) gesprochen werden, falls keine .ogg-Datei im Asset-Ordner liegt.
     */
    private val clipTextIndex: Map<String, String> by lazy {
        buildMap {
            val combos = listOf(
                RuSettings(RuGender.MALE, RuGender.MALE),
                RuSettings(RuGender.FEMALE, RuGender.FEMALE),
                RuSettings(RuGender.MALE, RuGender.FEMALE),
                RuSettings(RuGender.FEMALE, RuGender.MALE),
            )
            for (idx in listOf(repo.index, repo.podcastIndex)) {
                for (s in combos) {
                    for (d in idx.days) {
                        for (p in idx.dayPhrases(d, s)) {
                            put(p.audio, p.plain)
                        }
                        for ((_, p) in idx.dialog(d, s)) {
                            put(p.audio, p.plain)
                        }
                    }
                }
            }
            for (p in repo.patterns) {
                for (l in p.scene.lines) {
                    put(RuPatternLogic.lineClip(l, RuGender.MALE), RussianPhonetics.plain(l.ru))
                    if (l.ruF != null) {
                        put(RuPatternLogic.lineClip(l, RuGender.FEMALE), RussianPhonetics.plain(l.ruF))
                    }
                }
                for (c in p.core + p.free.model) {
                    put(RuDeepdiveLogic.clipName(c.ru), RussianPhonetics.plain(c.ru))
                }
            }
            for (e in repo.deepdive.values) {
                val strings = e.variants.map { it.ru } + e.examples.map { it.ru } + e.gender?.forms.orEmpty().map { it.ru }
                for (ru in strings) {
                    put(RuDeepdiveLogic.clipName(ru), RussianPhonetics.plain(ru))
                }
            }
        }
    }

    private fun resolveTtsText(name: String): String? {
        val base = name.removeSuffix("_slow")
        if (base.startsWith("tts|")) return RussianPhonetics.plain(base.removePrefix("tts|")).takeIf { it.isNotBlank() }
        return clipTextIndex[base] ?: RuDeepdiveLogic.textForClip(base)
    }

    private fun ensureTts() {
        if (tts != null) return
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("ru", "RU")
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        mainHandler.post { finish() }
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        mainHandler.post { finish() }
                    }
                    override fun onError(utteranceId: String?, errorCode: Int) {
                        mainHandler.post { finish() }
                    }
                })
                ttsReady = true
            }
        }
    }

    /**
     * Beim Öffnen des Bereichs aufrufen: Dateiliste im Hintergrund lesen und
     * den Player bauen, bevor der erste Tipp kommt — sonst hängt der erste
     * Klick spürbar (Player-Aufbau ~0,5 s auf langsamen Geräten).
     */
    suspend fun warmUp() {
        withContext(Dispatchers.IO) {
            available.size
            clipTextIndex.size
        }
        ensure()
        ensureTts()
    }

    fun has(name: String): Boolean = "$name.ogg" in available || resolveTtsText(name) != null

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

    /** Clip abspielen; [slow] nimmt die langsam gesprochene Aufnahme (oder langsames russisches TTS). */
    fun play(name: String, slow: Boolean = false, onComplete: (() -> Unit)? = null) {
        val file = if (slow && "$name.ogg" in available && "${name}_slow.ogg" in available) "${name}_slow" else name
        if ("$file.ogg" in available) {
            tts?.stop()
            playUri("asset:///russian/audio/$file.ogg", name, onComplete)
            return
        }
        val ruText = resolveTtsText(name)
        if (ruText != null) {
            playTts(ruText, name, slow, onComplete)
            return
        }
        Log.w(TAG, "Audio fehlt: $file")
        onComplete?.invoke()
    }

    private fun playTts(text: String, tag: String, slow: Boolean, onComplete: (() -> Unit)?) {
        if (music.isPlaying.value) music.toggle()
        player?.stop()
        ensureTts()
        onDone = onComplete
        _playing.value = tag
        val engine = tts
        if (engine != null && ttsReady) {
            engine.setSpeechRate(if (slow) 0.72f else 0.96f)
            val res = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ru_tts_${System.nanoTime()}")
            if (res == TextToSpeech.ERROR) {
                finish()
            }
        } else {
            // Falls die Engine gerade erst initialisiert wird, kurz verzögert sprechen oder sauber beenden
            mainHandler.postDelayed({
                val readyEngine = tts
                if (readyEngine != null && ttsReady) {
                    readyEngine.setSpeechRate(if (slow) 0.72f else 0.96f)
                    val res = readyEngine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ru_tts_${System.nanoTime()}")
                    if (res == TextToSpeech.ERROR) finish()
                } else {
                    finish()
                }
            }, 350L)
        }
    }

    /** Eigene Aufnahme abspielen. */
    fun playFile(file: File, onComplete: (() -> Unit)? = null) {
        if (!file.exists()) return
        tts?.stop()
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
        tts?.stop()
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
    /**
     * [heard] ist nur bei leerem Ergebnis von Belang: true = es wurde gesprochen, aber nichts
     * verstanden; false = es war gar nichts zu hören.
     */
    data class Done(val hypotheses: List<String>, val heard: Boolean = false) : RuListenState
    data class Failed(val message: String, val missingLanguage: Boolean = false) : RuListenState
}

/**
 * Das russische Sprachpaket des Erkennungsdienstes: Mit ihm läuft die Erkennung auf dem Gerät,
 * ohne Internet. Ohne es geht jede Aufnahme zu Google — das hakt bei wackeliger Verbindung oder
 * VPN. Ob es da ist, zeigt sich am ersten Versuch (kein Paket → der Dienst lehnt sofort ab).
 */
object RussianSpeechPack {
    /** null = noch nicht ausprobiert. */
    @Volatile var installed: Boolean? = null

    /** Googles eigener Dialog zum Laden des Pakets (»Sprachdienste von Google«). */
    private fun downloadIntent() = Intent("com.google.recognition.action.DOWNLOAD_LANGUAGE")
        .setPackage("com.google.android.tts")
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun canDownload(context: Context): Boolean =
        runCatching { downloadIntent().resolveActivity(context.packageManager) != null }.getOrDefault(false)

    /** Öffnet den Dialog. Danach wird beim nächsten Sprechen neu geprüft, ob das Paket da ist. */
    fun download(context: Context): Boolean = runCatching {
        context.startActivity(downloadIntent())
        installed = null
        true
    }.getOrElse {
        Log.w("RussianSpeechPack", "Dialog zum Laden des Sprachpakets ließ sich nicht öffnen", it)
        false
    }
}

/**
 * Android-Spracherkennung auf Russisch. Mit installiertem Sprachpaket auf dem Gerät, sonst
 * übers Internet (siehe [RussianSpeechPack]). Muss auf dem Main-Thread erzeugt und benutzt werden.
 */
class RussianSpeechRecognizer(private val context: Context) {
    init {
        // Betonungs-Wörterbuch für die Auswertung (RuAnswerCheck) — klein, einmal pro Prozess.
        runCatching { com.hikari.app.domain.russian.RussianGlossary.load(context.applicationContext.assets) }
    }

    private val _state = MutableStateFlow<RuListenState>(RuListenState.Idle)
    val state: StateFlow<RuListenState> = _state.asStateFlow()

    /**
     * true, sobald das Mikrofon wirklich offen ist. Zwischen dem Start und diesem Moment vergeht
     * spürbar Zeit — wer da schon spricht, verliert das erste Wort. Die Oberfläche zeigt deshalb
     * erst »Ich höre zu«, wenn das hier true ist.
     */
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()
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

    private var retries = 0
    private var retryRun: Runnable? = null
    private var patienceRun: Runnable? = null

    /** Start des laufenden Anlaufs — ein Fehler gleich danach ist ein Stolpern des Dienstes. */
    private var beganAt = 0L

    /** Moment des Tippens — ab hier läuft die Wartezeit, in der man anfangen darf zu sprechen. */
    private var tappedAt = 0L

    /** Wie oft das Mikrofon in dieser Wartezeit still wieder geöffnet wurde. */
    private var reopened = 0

    /** Lautester Pegel der laufenden Sitzung — steht im Logcat. */
    private var peakRms = Float.NEGATIVE_INFINITY

    /** Lautester Pegel seit dem Tippen: unterscheidet am Ende »nichts gehört« von »nicht verstanden«. */
    private var peakSinceTap = Float.NEGATIVE_INFINITY

    /** Die laufende Sitzung erkennt auf dem Gerät (true) oder übers Internet (false). */
    private var onDevice = false

    /** In dieser Wartezeit hing das Netz — bestimmt die Meldung, falls am Ende nichts verstanden ist. */
    private var sawNetworkError = false

    /**
     * Gibt die Instanz frei, sobald ein Versuch zu Ende ist. So liegt zwischen zwei Versuchen
     * Zeit, in der der Erkennungsdienst seine Sitzung abräumt — statt dass der nächste Versuch
     * die alte Instanz erst beim Start zerstört und sofort eine neue anlegt.
     */
    private fun releaseWhenDone(r: SpeechRecognizer) {
        _ready.value = false
        mainHandler.post {
            if (recognizer === r) {
                runCatching { r.destroy() }
                recognizer = null
            }
        }
    }

    fun start() {
        retries = 0
        reopened = 0
        peakSinceTap = Float.NEGATIVE_INFINITY
        sawNetworkError = false
        // Erst auf dem Gerät versuchen; fehlt das Sprachpaket, lehnt der Dienst sofort ab (siehe onError).
        onDevice = RussianSpeechPack.installed != false
        tappedAt = android.os.SystemClock.elapsedRealtime()
        patienceRun?.let { mainHandler.removeCallbacks(it) }
        patienceRun = Runnable { patienceOver() }
            .also { mainHandler.postDelayed(it, com.hikari.app.domain.russian.RuSpeechRetry.PATIENCE_MS) }
        begin()
    }

    /** Ende der Wartezeit: Ist bis jetzt kein Wort erkannt, schließt das Mikrofon und meldet es. */
    private fun patienceOver() {
        patienceRun = null
        val s = _state.value
        if (s !is RuListenState.Listening && s !is RuListenState.Partial) return
        // Wer gerade spricht oder selbst gestoppt hat, wird nicht unterbrochen.
        if (userStopped || !lastPartialText.isNullOrBlank() || lastHypotheses.isNotEmpty()) return
        logEnd("Wartezeit um, kein Wort erkannt")
        val heard = com.hikari.app.domain.russian.RuSpeechRetry.heard(peakSinceTap)
        teardown()
        // Der Netz-Hinweis nur, wenn es keinen Erkenner auf dem Gerät gibt — sonst hat schlicht niemand etwas gesagt.
        val netOnly = sawNetworkError && RussianSpeechPack.installed != true
        _state.value = if (netOnly) NETWORK_FAILED else RuListenState.Done(emptyList(), heard = heard)
    }

    private fun hasNetwork(): Boolean = runCatching {
        context.getSystemService(android.net.ConnectivityManager::class.java)?.activeNetwork != null
    }.getOrDefault(true)

    /** Eine Zeile je Sitzungsende — damit sich am Gerät nachlesen lässt, was die Erkennung getan hat. */
    private fun logEnd(what: String) {
        val now = android.os.SystemClock.elapsedRealtime()
        Log.i(
            TAG,
            "Ende: $what nach ${now - beganAt}ms (seit Tippen ${now - tappedAt}ms), ${if (onDevice) "auf dem Gerät" else "übers Netz"}, Pegel max $peakRms, " +
                "neu geöffnet $reopened, Anläufe $retries, selbst gestoppt $userStopped, Teiltext ${!lastPartialText.isNullOrBlank()}",
        )
    }

    /**
     * Die Sitzung endete ohne Text. Läuft die Wartezeit noch, geht das Mikrofon still wieder auf
     * (true) — sonst entscheidet der Aufrufer, was gemeldet wird.
     */
    private fun keepWaiting(code: Int): Boolean {
        val sinceTap = android.os.SystemClock.elapsedRealtime() - tappedAt
        if (!com.hikari.app.domain.russian.RuSpeechRetry.shouldKeepWaiting(code, sinceTap, reopened)) return false
        reopened++
        onDevice = com.hikari.app.domain.russian.RuSpeechRetry.nextOnDevice(onDevice, RussianSpeechPack.installed == true, com.hikari.app.domain.russian.RuSpeechRetry.isNetwork(code), hasNetwork())
        teardown()
        _state.value = RuListenState.Listening
        retryRun = Runnable { begin() }
            .also { mainHandler.postDelayed(it, com.hikari.app.domain.russian.RuSpeechRetry.REOPEN_DELAY_MS) }
        return true
    }

    private fun begin() {
        if (!available) {
            _state.value = RuListenState.Failed("Keine Spracherkennung auf diesem Gerät gefunden.")
            return
        }
        // Läuft noch ein Versuch, erst abräumen und dem Dienst einen Moment geben.
        if (recognizer != null) {
            teardown()
            _state.value = RuListenState.Listening
            retryRun = Runnable { begin() }.also { mainHandler.postDelayed(it, 250L) }
            return
        }
        teardown()
        userStopped = false
        lastHypotheses = emptyList()
        lastPartialText = null
        beganAt = android.os.SystemClock.elapsedRealtime()
        peakRms = Float.NEGATIVE_INFINITY

        val r = runCatching { SpeechRecognizer.createSpeechRecognizer(context) }.getOrNull()
        if (r == null) {
            _state.value = RuListenState.Failed("Spracherkennung konnte nicht initialisiert werden.")
            return
        }

        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _state.value = RuListenState.Listening
                _ready.value = true
            }
            // Manche Erkenner melden »bereit« nicht — spätestens wenn Sprache ankommt, ist das Mikrofon offen.
            override fun onBeginningOfSpeech() { _ready.value = true }
            override fun onRmsChanged(rmsdB: Float) {
                if (rmsdB > peakRms) peakRms = rmsdB
                if (rmsdB > peakSinceTap) peakSinceTap = rmsdB
            }
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
                if (recognizer !== r) return // Nachzügler einer schon abgeräumten Sitzung
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
                logEnd("Ergebnis mit ${finalResults.size} Lesarten")
                if (onDevice) RussianSpeechPack.installed = true
                // Leeres Ergebnis, ohne dass jemand gesprochen oder gestoppt hat: weiter warten.
                if (finalResults.isEmpty() && !userStopped && keepWaiting(SpeechRecognizer.ERROR_NO_MATCH)) return
                _state.value = RuListenState.Done(finalResults, heard = com.hikari.app.domain.russian.RuSpeechRetry.heard(peakSinceTap))
                releaseWhenDone(r)
            }

            override fun onError(error: Int) {
                if (recognizer !== r) return // Nachzügler einer schon abgeräumten Sitzung
                clearWatchdog()
                logEnd("Fehler $error")
                val retry = com.hikari.app.domain.russian.RuSpeechRetry
                if (onDevice && retry.isPackMissing(error)) {
                    // Kein russisches Sprachpaket: merken und sofort übers Internet weitermachen.
                    RussianSpeechPack.installed = false
                    if (!userStopped) {
                        onDevice = false
                        teardown()
                        _state.value = RuListenState.Listening
                        retryRun = Runnable { begin() }.also { mainHandler.postDelayed(it, 50L) }
                        return
                    }
                } else if (onDevice) {
                    RussianSpeechPack.installed = true
                }
                if (retry.isNetwork(error)) sawNetworkError = true
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
                    releaseWhenDone(r)
                    return
                }

                // Typisch beim zweiten Versuch kurz nach dem ersten: der Dienst meldet sofort
                // »Netzwerk«/»Server«, ist noch belegt oder hat die Verbindung verloren, obwohl
                // alles da ist. Mit frischer Instanz still nochmal starten (siehe RuSpeechRetry).
                val elapsed = android.os.SystemClock.elapsedRealtime() - beganAt
                // Hängt das Netz und das Sprachpaket ist da, übernimmt gleich das Gerät (keepWaiting).
                val deviceTakesOver = retry.isNetwork(error) && RussianSpeechPack.installed == true
                if (!deviceTakesOver && retry.shouldRetry(error, elapsed, retries)) {
                    val wait = com.hikari.app.domain.russian.RuSpeechRetry.delayMs(retries)
                    retries++
                    teardown()
                    _state.value = RuListenState.Listening
                    retryRun = Runnable { begin() }.also { mainHandler.postDelayed(it, wait) }
                    return
                }
                // Die Erkennung hat ohne Text aufgegeben: still wieder öffnen, solange die Wartezeit läuft.
                if (keepWaiting(error)) return
                val heard = com.hikari.app.domain.russian.RuSpeechRetry.heard(peakSinceTap)
                releaseWhenDone(r)

                // Bei manuellem Client-Stopp oder No-Match ohne Sprache: als Done(leer) werten, nie abbrechen!
                _state.value = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                        RuListenState.Done(emptyList(), heard = heard)
                    SpeechRecognizer.ERROR_CLIENT ->
                        RuListenState.Done(emptyList())
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                        RuListenState.Failed("Mikrofon-Berechtigung fehlt.")
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER ->
                        NETWORK_FAILED
                    12, 13 -> // ERROR_LANGUAGE_NOT_SUPPORTED / ERROR_LANGUAGE_UNAVAILABLE (API 31)
                        RuListenState.Failed("Russisch ist in der Spracherkennung nicht installiert.", missingLanguage = true)
                    8, 10, 11 -> RuListenState.Failed("Die Spracherkennung ist gerade nicht erreichbar — bitte gleich nochmal versuchen.")
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
            if (onDevice) putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            // Bewusst KEINE eigenen Stille-Zeiten (EXTRA_SPEECH_INPUT_*_SILENCE_LENGTH_MILLIS):
            // Mit 1500/1200 ms endete am Handy jede Sitzung nach rund 2 s. Der Verdacht, dass der
            // Dienst sie auch auf die Stille VOR dem ersten Wort anwendet, ist nicht bewiesen —
            // »Ende: …« im Logcat zeigt, wie lange die Sitzungen ohne diese Vorgaben dauern.
        }
        _state.value = RuListenState.Listening
        runCatching { r.startListening(intent) }.onFailure { e ->
            Log.w(TAG, "startListening failed", e)
            _state.value = RuListenState.Failed("Spracherkennung konnte nicht gestartet werden.")
        }
    }

    fun stopListening() {
        userStopped = true
        // Zwischen zwei Anläufen läuft keine Sitzung, die noch antworten könnte: sofort abschließen,
        // sonst ginge das Mikrofon nach dem Stopp von selbst wieder auf.
        if (recognizer == null) {
            retryRun?.let { mainHandler.removeCallbacks(it) }
            retryRun = null
            clearWatchdog()
            _state.value = RuListenState.Done(emptyList())
            return
        }
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
        patienceRun?.let { mainHandler.removeCallbacks(it) }
        patienceRun = null
        userStopped = false
        lastHypotheses = emptyList()
        lastPartialText = null
        _state.value = RuListenState.Idle
    }

    /** Von außen: alles beenden, auch die laufende Wartezeit. */
    fun destroy() {
        patienceRun?.let { mainHandler.removeCallbacks(it) }
        patienceRun = null
        teardown()
    }

    /** Die laufende Sitzung abräumen; die Wartezeit seit dem Tippen läuft weiter. */
    private fun teardown() {
        _ready.value = false
        clearWatchdog()
        retryRun?.let { mainHandler.removeCallbacks(it) }
        retryRun = null
        runCatching { recognizer?.destroy() }
        recognizer = null
        userStopped = false
        lastHypotheses = emptyList()
        lastPartialText = null
    }

    private companion object {
        const val TAG = "RussianSpeechRecognizer"
        val NETWORK_FAILED = RuListenState.Failed(
            "Die Spracherkennung braucht Internet oder das russische Offline-Sprachpaket.",
            missingLanguage = true,
        )
    }
}
