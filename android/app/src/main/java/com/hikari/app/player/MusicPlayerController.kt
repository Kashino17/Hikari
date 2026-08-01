package com.hikari.app.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.hikari.app.data.net.ConnectivityObserver
import com.hikari.app.domain.download.LocalMusicDownloadManager
import com.hikari.app.domain.model.MusicSong
import com.hikari.app.domain.repo.MusicRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * App-wide music playback. One ExoPlayer instance, one queue — survives
 * navigation because it is a @Singleton, not tied to any screen's ViewModel.
 */
@Singleton
class MusicPlayerController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: MusicRepository,
    private val downloads: LocalMusicDownloadManager,
    private val connectivity: ConnectivityObserver,
) {
    companion object {
        const val REPEAT_OFF = 0
        const val REPEAT_ALL = 1
        const val REPEAT_ONE = 2
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var player: ExoPlayer? = null
    private var loadJob: Job? = null
    private var progressJob: Job? = null
    private var autoplayJob: Job? = null

    /** Vorausgeladene Stream-URL des nächsten Songs (siehe maybePrefetchNext). */
    private var prefetchJob: Job? = null
    private var prefetchedFor: String? = null
    private var prefetchedUrl: String? = null

    private val _currentSong = MutableStateFlow<MusicSong?>(null)
    val currentSong: StateFlow<MusicSong?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _queue = MutableStateFlow<List<MusicSong>>(emptyList())
    val queue: StateFlow<List<MusicSong>> = _queue.asStateFlow()

    private val _repeatMode = MutableStateFlow(REPEAT_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _shuffle = MutableStateFlow(false)
    val shuffle: StateFlow<Boolean> = _shuffle.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var queueIndex = -1

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            _isPlaying.value = playing
        }

        override fun onPlaybackStateChanged(state: Int) {
            _isBuffering.value = state == Player.STATE_BUFFERING
            if (state == Player.STATE_READY) {
                _durationMs.value = player?.duration?.coerceAtLeast(0) ?: 0
            }
            if (state == Player.STATE_ENDED) onTrackEnded()
        }

        override fun onPlayerError(e: PlaybackException) {
            retryOrSkip()
        }
    }

    /** After a load/playback failure move on, but never loop forever. */
    private var consecutiveFailures = 0

    /** Wiederholversuche mit frischer Stream-URL für den aktuellen Song. */
    private var streamRetries = 0

    /**
     * Stirbt die Wiedergabe mitten im Song (typisch: gecachte googlevideo-URL
     * wird vom CDN abgelehnt), bekommt derselbe Song bis zu zwei neue Chancen
     * mit frisch extrahierter URL — erst dann wird weitergeschaltet. Ein
     * sofortiges Überspringen wäre für den Hörer unverständlich.
     */
    private fun retryOrSkip() {
        val current = _currentSong.value
        if (current != null && streamRetries < 2) {
            streamRetries++
            _error.value = "Verbindung unterbrochen — lade neu"
            loadAndPlay(current, forceRefresh = true)
            return
        }
        streamRetries = 0
        _error.value = "Wiedergabe fehlgeschlagen — Song wird übersprungen"
        skipAfterFailure()
    }

    private fun ensurePlayer(): ExoPlayer {
        player?.let { return it }
        val built = ExoPlayer.Builder(context)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        built.addListener(listener)
        player = built
        startProgressUpdates()
        return built
    }

    /** Der [MusicPlaybackService] teilt sich die Player-Instanz des Controllers. */
    fun playerForSession(): ExoPlayer = ensurePlayer()

    private var mediaControllerFuture: ListenableFuture<MediaController>? = null

    /**
     * Hält eine MediaController-Bindung an den PlaybackService. Erst durch
     * diese Verbindung postet Media3 die System-Notification und hält den
     * Service im Vordergrund — ein bloßes startService() lässt ihn als
     * Hintergrund-Service, den Android nach ~90 s als idle killt (dann ist
     * auch das Media-Widget weg).
     */
    private fun ensureSessionConnection() {
        if (mediaControllerFuture != null) return
        val token = SessionToken(context, ComponentName(context, MusicPlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        mediaControllerFuture = future
        future.addListener(
            { runCatching { future.get() } },
            ContextCompat.getMainExecutor(context),
        )
    }

    fun play(song: MusicSong, contextQueue: List<MusicSong> = emptyList()) {
        autoplayJob?.cancel() // eigene Auswahl schlägt laufenden Nachschub
        consecutiveFailures = 0
        streamRetries = 0
        val newQueue = if (contextQueue.isNotEmpty()) contextQueue else listOf(song)
        _queue.value = newQueue
        queueIndex = newQueue.indexOfFirst { it.videoId == song.videoId }.coerceAtLeast(0)
        loadAndPlay(song)
    }

    fun toggle() {
        val p = player ?: return
        if (p.isPlaying) p.pause() else p.play()
    }

    fun next() = advance(forward = true, manual = true)

    fun previous() {
        val p = player
        // First 3 s of a track: go to the previous song, otherwise restart.
        if (p != null && p.currentPosition > 3_000) {
            p.seekTo(0)
            return
        }
        advance(forward = false, manual = true)
    }

    fun seekTo(ms: Long) {
        player?.seekTo(ms)
        _positionMs.value = ms
    }

    fun toggleShuffle() {
        _shuffle.value = !_shuffle.value
    }

    fun cycleRepeat() {
        _repeatMode.value = (_repeatMode.value + 1) % 3
    }

    fun clearError() {
        _error.value = null
    }

    fun stop() {
        loadJob?.cancel()
        autoplayJob?.cancel()
        prefetchJob?.cancel()
        prefetchedFor = null
        prefetchedUrl = null
        player?.stop()
        player?.clearMediaItems()
        _currentSong.value = null
        _isPlaying.value = false
        _positionMs.value = 0
        _durationMs.value = 0
    }

    private fun loadAndPlay(song: MusicSong, forceRefresh: Boolean = false) {
        loadJob?.cancel()
        _currentSong.value = song
        _positionMs.value = 0
        _durationMs.value = 0
        _error.value = null
        _isBuffering.value = true
        scope.launch { repo.recordPlayed(song) }
        loadJob = scope.launch {
            // Heruntergeladene Datei schlägt den Stream immer — funktioniert
            // auch ohne Netz und spart Daten.
            val localFile = downloads.localFile(song.videoId)
            val uri = if (localFile != null) {
                "file://${localFile.absolutePath}"
            } else if (!connectivity.currentlyOnline()) {
                _isBuffering.value = false
                _error.value = "Offline — „${song.title}“ wurde nicht heruntergeladen"
                _isPlaying.value = false
                return@launch
            } else if (!forceRefresh && prefetchedFor == song.videoId && prefetchedUrl != null) {
                // Vorausgeladene URL — der Wechsel braucht keinen Netz-Call mehr.
                prefetchedUrl
            } else {
                repo.getAudioStream(song.videoId, forceRefresh)
            }
            if (uri == null) {
                _isBuffering.value = false
                _error.value = "„${song.title}“ ist nicht abspielbar"
                // Kein Sofort-Skip: erst mit frisch extrahierter URL erneut versuchen.
                retryOrSkip()
                return@launch
            }
            val p = ensurePlayer()
            // Bindet den PlaybackService (MediaController) — erst damit darf
            // Media3 die Systemsteuerung posten und der Service im
            // Vordergrund überleben.
            ensureSessionConnection()
            // Titel/Artist/Cover als Metadaten — daraus baut das System das
            // Widget auf Sperrbildschirm und in der Benachrichtigungsleiste.
            p.setMediaItem(
                MediaItem.Builder()
                    .setUri(uri)
                    .setMediaId(song.videoId)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(song.title)
                            .setArtist(song.uploader.ifBlank { "Hikari" })
                            .setArtworkUri(
                                song.thumbnailUrl.takeIf { it.isNotBlank() }?.let(Uri::parse),
                            )
                            .build(),
                    )
                    .build(),
            )
            p.prepare()
            p.play()
            if (prefetchedFor == song.videoId) {
                prefetchedFor = null
                prefetchedUrl = null
            }
        }
    }

    private fun onTrackEnded() {
        if (_repeatMode.value == REPEAT_ONE) {
            player?.seekTo(0)
            player?.play()
            return
        }
        advance(forward = true, manual = false)
    }

    private fun skipAfterFailure() {
        consecutiveFailures++
        if (consecutiveFailures >= 3 || _queue.value.size <= 1) {
            consecutiveFailures = 0
            _isPlaying.value = false
            return
        }
        advance(forward = true, manual = false)
    }

    private fun advance(forward: Boolean, manual: Boolean) {
        val q = _queue.value
        if (q.isEmpty()) return
        if (manual) {
            consecutiveFailures = 0
            streamRetries = 0
        }

        val nextIndex = when {
            _shuffle.value && q.size > 1 -> {
                var i: Int
                do { i = q.indices.random() } while (i == queueIndex)
                i
            }
            forward -> {
                val n = queueIndex + 1
                when {
                    n < q.size -> n
                    _repeatMode.value == REPEAT_ALL -> 0
                    else -> {
                        // Ende der Liste: passende Stücke nachladen, statt
                        // einfach zu verstummen.
                        extendQueueAndContinue()
                        return
                    }
                }
            }
            else -> if (queueIndex - 1 >= 0) queueIndex - 1 else q.size - 1
        }
        queueIndex = nextIndex
        loadAndPlay(q[nextIndex])
    }

    /**
     * Autoplay am Listenende. Der Nachschub kommt aus dem Repository und ist
     * damit automatisch so gefiltert wie der Rest — im Instrumental-Modus
     * folgen also auch hier nur Stücke ohne Gesang.
     *
     * Im Hintergrund ist das Netz oft träge oder kurz weg (Doze). Ein einziger
     * Versuch ließe das Autoplay dann lautlos sterben — der Player bliebe nach
     * dem Songende einfach stehen. Deshalb mehrere Versuche mit wachsender
     * Pause, bevor auf den Queue-Anfang zurückgefallen wird.
     */
    private fun extendQueueAndContinue() {
        if (autoplayJob?.isActive == true) return
        val seed = _currentSong.value ?: run {
            _isPlaying.value = false
            return
        }
        _isBuffering.value = true
        autoplayJob = scope.launch {
            val current = _queue.value
            var more = emptyList<MusicSong>()
            if (connectivity.currentlyOnline()) {
                for (attempt in 1..3) {
                    more = runCatching {
                        repo.getAutoplaySongs(seed, current.map { it.videoId }.toSet())
                    }.getOrDefault(emptyList())
                    if (more.isNotEmpty()) break
                    if (attempt < 3) delay(3_000L * attempt)
                }
            }

            if (more.isEmpty()) {
                _isBuffering.value = false
                // Nichts Passendes gefunden — lieber von vorn als abwürgen.
                if (current.size > 1) {
                    queueIndex = 0
                    loadAndPlay(current[0])
                } else {
                    _isPlaying.value = false
                }
                return@launch
            }
            _queue.value = current + more
            queueIndex = current.size
            loadAndPlay(more.first())
        }
    }

    /**
     * Lädt die Stream-URL des nächsten Songs vor, solange der aktuelle noch
     * läuft. Ohne das Vorziehen entsteht beim Wechsel eine Lücke von bis zu
     * 45 s (yt-dlp), in der der Player keine Wake-Lock hält — bei ausgeschaltetem
     * Bildschirm friert das System die App mitten im Übergang ein und die
     * Wiedergabe stirbt nach wenigen Songs.
     */
    private fun maybePrefetchNext() {
        val next = nextLinearSong() ?: return
        if (prefetchedFor == next.videoId || prefetchJob?.isActive == true) return
        if (!connectivity.currentlyOnline()) return
        prefetchJob = scope.launch {
            if (downloads.localFile(next.videoId) != null) return@launch // lokale Datei braucht keine URL
            val url = runCatching { repo.getAudioStream(next.videoId) }.getOrNull()
            if (url != null) {
                prefetchedFor = next.videoId
                prefetchedUrl = url
            }
        }
    }

    /** Nächster Song bei linearem Abspielen; bei Shuffle/Listenende nicht vorhersagbar. */
    private fun nextLinearSong(): MusicSong? {
        if (_shuffle.value) return null
        val q = _queue.value
        val nextIndex = queueIndex + 1
        return when {
            nextIndex < q.size -> q[nextIndex]
            _repeatMode.value == REPEAT_ALL && q.isNotEmpty() -> q[0]
            else -> null // Autoplay-Nachschub ist nicht vorhersagbar
        }
    }

    private fun startProgressUpdates() {
        if (progressJob?.isActive == true) return
        progressJob = scope.launch {
            while (isActive) {
                player?.let {
                    if (it.isPlaying) {
                        _positionMs.value = it.currentPosition.coerceAtLeast(0)
                        val d = it.duration
                        if (d > 0) {
                            _durationMs.value = d
                            // Letzte Minute: den nächsten Übergang vorbereiten.
                            if (d - it.currentPosition < 60_000) maybePrefetchNext()
                        }
                        // Läuft ein Song gesund, waren frühere Fehler nur
                        // vorübergehend — Zähler zurücksetzen.
                        if (it.currentPosition > 15_000) {
                            streamRetries = 0
                            consecutiveFailures = 0
                        }
                    }
                }
                delay(500)
            }
        }
    }
}
