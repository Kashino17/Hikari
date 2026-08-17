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
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
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

    /** Bei Shuffle einmalig gewürfelter Index des nächsten Songs — Prefetch
     *  und Übergang müssen denselben Song treffen. */
    private var plannedNextIndex: Int? = null

    /** Früh gestarteter Autoplay-Nachschub (siehe maybeExtendQueueEarly). */
    private var earlyExtendDone = false
    private var resumeAfterExtend = false

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

    /** true = der Player spielt den muxed Video-Stream statt nur Audio. */
    private val _videoMode = MutableStateFlow(false)
    val videoMode: StateFlow<Boolean> = _videoMode.asStateFlow()

    /** true, sobald das erste Video-Frame auf der Surface steht — bis dahin
     *  zeigt die UI das Thumbnail als Poster statt einer schwarzen Fläche. */
    private val _videoFrameReady = MutableStateFlow(false)
    val videoFrameReady: StateFlow<Boolean> = _videoFrameReady.asStateFlow()

    /** Die UI hat eine (neue) TextureView angebunden — bis zum nächsten
     *  gerenderten Frame wieder das Poster zeigen statt Schwarz. */
    fun notifyVideoSurfaceChanged() {
        _videoFrameReady.value = false
    }

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

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // AUTO = ExoPlayer ist selbständig in den vorbufferten nächsten
            // Song gewechselt; andere Gründe (setMediaItem, Seek) pflegen
            // ihren State selbst.
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) onAutoAdvanced()
        }

        override fun onRenderedFirstFrame() {
            _videoFrameReady.value = true
        }
    }

    /** After a load/playback failure move on, but never loop forever. */
    private var consecutiveFailures = 0

    /** Wiederholversuche mit frischer Stream-URL für den aktuellen Song. */
    private var streamRetries = 0

    /** Wiederholversuche des Video-Streams, bevor auf Audio zurückgefallen wird. */
    private var videoRetries = 0

    /**
     * Stirbt die Wiedergabe mitten im Song (typisch: gecachte googlevideo-URL
     * wird vom CDN abgelehnt), bekommt derselbe Song bis zu zwei neue Chancen
     * mit frisch extrahierter URL — erst dann wird weitergeschaltet. Ein
     * sofortiges Überspringen wäre für den Hörer unverständlich.
     *
     * Wichtig: die Fehlermeldung IMMER erst nach loadAndPlay setzen —
     * loadAndPlay räumt _error zu Beginn ab, eine vorher gesetzte Meldung
     * wäre nie sichtbar (genau so blieb der stille Video-Fallback unbemerkt).
     */
    private fun retryOrSkip() {
        val current = _currentSong.value
        // Zickt der Video-Stream: erst ein zweiter Versuch (die Erstauflösung
        // am Proxy kann träge sein), dann zurück zu Audio statt zu skippen —
        // die Tonspur ist wichtiger als das Bild. Position bleibt erhalten.
        if (current != null && _videoMode.value) {
            val resumeAt = player?.currentPosition?.coerceAtLeast(0) ?: 0
            if (videoRetries < 1) {
                videoRetries++
                loadAndPlay(current, forceRefresh = true, startPositionMs = resumeAt)
                _error.value = "Video stockt — neuer Versuch"
            } else {
                videoRetries = 0
                _videoMode.value = false
                loadAndPlay(current, forceRefresh = true, startPositionMs = resumeAt)
                _error.value = "Video-Wiedergabe gestört — weiter mit Audio"
            }
            return
        }
        if (current != null && streamRetries < 2) {
            streamRetries++
            loadAndPlay(current, forceRefresh = true)
            _error.value = "Verbindung unterbrochen — lade neu"
            return
        }
        streamRetries = 0
        _error.value = "Wiedergabe fehlgeschlagen — Song wird übersprungen"
        skipAfterFailure()
    }

    private fun ensurePlayer(): ExoPlayer {
        player?.let { return it }
        // Großzügige HTTP-Timeouts: der Backend-Proxy löst Audio/Video erst
        // beim ersten Byte über yt-dlp auf — das dauert bei kalten Videos
        // deutlich länger als ExoPlayers 8-s-Defaults. Mit den Defaults flog
        // der Video-Modus regelmäßig per Timeout in den Audio-Fallback.
        val httpFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(30_000)
            .setAllowCrossProtocolRedirects(true)
        val built = ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(DefaultDataSource.Factory(context, httpFactory)),
            )
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            // Großzügiger Puffer: das nächste Playlist-Item wird schon während
            // der laufenden Wiedergabe vorgeladen — dafür muss maxBuffer über
            // die Default-50 s hinaus reichen (ein ganzer Song).
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
                        /* maxBufferMs = */ 5 * 60_000,
                        /* bufferForPlaybackMs = */ 2_500,
                        /* bufferForPlaybackAfterRebufferMs = */ 5_000,
                    )
                    .build(),
            )
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        built.addListener(listener)
        player = built
        startProgressUpdates()
        return built
    }

    /**
     * Repeat-One läuft über den ExoPlayer-eigenen Repeat-Modus: Ist schon ein
     * nächstes Item eingeplant, wiederholt der Player trotzdem den aktuellen
     * Song, statt in den eingeplanten überzugehen.
     */
    private fun syncRepeatMode(p: ExoPlayer) {
        p.repeatMode = when (_repeatMode.value) {
            REPEAT_ONE -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
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
        videoRetries = 0
        earlyExtendDone = false
        resumeAfterExtend = false
        plannedNextIndex = null
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
        clearPlannedNext()
    }

    fun cycleRepeat() {
        _repeatMode.value = (_repeatMode.value + 1) % 3
        player?.let { syncRepeatMode(it) }
        clearPlannedNext()
    }

    /**
     * Shuffle/Repeat wurde mitten im Song umgeschaltet — ein bereits
     * eingeplantes Folge-Item (Prefetch-URL + an die ExoPlayer-Playlist
     * angehängtes MediaItem) passt nicht mehr zur Auswahl. Verwerfen, damit
     * [maybePrefetchNext] frisch plant; sonst zeigen _currentSong/queueIndex
     * nach einem AUTO-Übergang auf einen anderen Song als der Player spielt.
     */
    private fun clearPlannedNext() {
        prefetchJob?.cancel()
        prefetchedFor = null
        prefetchedUrl = null
        plannedNextIndex = null
        val p = player ?: return
        val nextSlot = p.currentMediaItemIndex + 1
        if (p.mediaItemCount > nextSlot) p.removeMediaItem(nextSlot)
    }

    fun clearError() {
        _error.value = null
    }

    /**
     * Wechselt zwischen Audio (Thumbnail) und Video — Position und
     * Wiedergabezustand bleiben erhalten, der Ton läuft konzeptionell einfach
     * weiter. Video braucht Netz und Backend; ohne beides bleibt es bei Audio.
     */
    fun toggleVideoMode() {
        val song = _currentSong.value ?: return
        videoRetries = 0
        if (_videoMode.value) {
            _videoMode.value = false
            scope.launch {
                val localFile = downloads.localFile(song.videoId)
                val uri = if (localFile != null) {
                    "file://${localFile.absolutePath}"
                } else {
                    runCatching { repo.getAudioStream(song.videoId) }.getOrNull()
                }
                if (uri == null) {
                    _error.value = "„${song.title}“ ist nicht abspielbar"
                    return@launch
                }
                applyStreamSwap(song, uri)
            }
        } else {
            scope.launch {
                if (!connectivity.currentlyOnline()) {
                    _error.value = "Video braucht eine Internetverbindung"
                    return@launch
                }
                val uri = runCatching { repo.getVideoStream(song.videoId) }.getOrNull()
                if (uri == null) {
                    _error.value = "Video ist für diesen Titel nicht verfügbar"
                    return@launch
                }
                _videoMode.value = true
                applyStreamSwap(song, uri)
            }
        }
    }

    /** Quellenwechsel ohne Fortschrittsverlust: gleiche Position, gleicher
     *  Play-Zustand — nur die MediaSource wird getauscht. */
    private fun applyStreamSwap(song: MusicSong, uri: String) {
        // Song hat inzwischen gewechselt — der Tausch gehört zum alten Titel.
        if (_currentSong.value?.videoId != song.videoId) return
        val p = ensurePlayer()
        val pos = p.currentPosition.coerceAtLeast(0)
        val wasPlaying = p.playWhenReady
        // Eingeplantes Folge-Item stammt aus dem anderen Modus — verwerfen.
        clearPlannedNext()
        prefetchedFor = null
        prefetchedUrl = null
        _isBuffering.value = true
        _videoFrameReady.value = false
        p.setMediaItem(mediaItemFor(song, uri), pos)
        p.prepare()
        p.playWhenReady = wasPlaying
    }

    fun stop() {
        loadJob?.cancel()
        autoplayJob?.cancel()
        prefetchJob?.cancel()
        prefetchedFor = null
        prefetchedUrl = null
        plannedNextIndex = null
        earlyExtendDone = false
        resumeAfterExtend = false
        _videoMode.value = false
        _videoFrameReady.value = false
        videoRetries = 0
        player?.stop()
        player?.clearMediaItems()
        _currentSong.value = null
        _isPlaying.value = false
        _positionMs.value = 0
        _durationMs.value = 0
    }

    /** Baut das MediaItem samt Titel/Artist/Cover — daraus erstellt das System
     *  das Widget auf Sperrbildschirm und in der Benachrichtigungsleiste. */
    private fun mediaItemFor(song: MusicSong, uri: String): MediaItem =
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
            .build()

    private fun loadAndPlay(song: MusicSong, forceRefresh: Boolean = false, startPositionMs: Long = 0) {
        loadJob?.cancel()
        plannedNextIndex = null // Playlist wird ersetzt — etwaige Planung ist hinfällig
        _currentSong.value = song
        _positionMs.value = startPositionMs
        _durationMs.value = 0
        _error.value = null
        _isBuffering.value = true
        _videoFrameReady.value = false
        scope.launch { repo.recordPlayed(song) }
        loadJob = scope.launch {
            // Video-Modus: der nächste Titel läuft direkt als Video weiter.
            // Ohne Video-Quelle (kein Backend/offline) fällt der Player
            // automatisch auf Audio zurück, statt stumm zu bleiben.
            if (_videoMode.value) {
                val videoUri = if (connectivity.currentlyOnline()) {
                    runCatching { repo.getVideoStream(song.videoId) }.getOrNull()
                } else {
                    null
                }
                if (videoUri != null) {
                    val p = ensurePlayer()
                    syncRepeatMode(p)
                    ensureSessionConnection()
                    p.setMediaItem(mediaItemFor(song, videoUri), startPositionMs)
                    p.prepare()
                    p.play()
                    return@launch
                }
                _videoMode.value = false
                _error.value = "Video nicht verfügbar — weiter mit Audio"
            }
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
            syncRepeatMode(p)
            // Bindet den PlaybackService (MediaController) — erst damit darf
            // Media3 die Systemsteuerung posten und der Service im
            // Vordergrund überleben.
            ensureSessionConnection()
            // setMediaItem ersetzt die komplette Playlist — ein zuvor
            // eingeplantes nächstes Item gehört damit der Vergangenheit an.
            p.setMediaItem(mediaItemFor(song, uri), startPositionMs)
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

        val nextIndex = QueueNavigator.advanceIndex(
            queueSize = q.size,
            currentIndex = queueIndex,
            forward = forward,
            shuffle = _shuffle.value,
            repeatAll = _repeatMode.value == REPEAT_ALL,
            // Vorwärts den gewürfelten Plan konsumieren (Prefetch trifft denselben
            // Song); rückwärts bleibt der Plan für den nächsten Vorwärtsschritt.
            plannedNextIndex = if (forward) plannedNextIndex else null,
        )
        if (nextIndex == QueueNavigator.EXTEND) {
            // Ende der Liste: passende Stücke nachladen, statt einfach zu verstummen.
            extendQueueAndContinue()
            return
        }
        // Manuelles Weiterschalten nimmt den vorgebufferten nächsten Song mit,
        // wenn er schon als Item in der ExoPlayer-Playlist liegt — kein erneutes
        // Laden, keine Hörlücke. Der Player meldet den Wechsel als REASON_SEEK,
        // der AUTO-Listener greift also nicht — den State hier selbst nachziehen.
        if (manual && forward) {
            val p = player
            if (p != null && p.mediaItemCount > p.currentMediaItemIndex + 1 &&
                p.getMediaItemAt(p.currentMediaItemIndex + 1).mediaId == q[nextIndex].videoId
            ) {
                queueIndex = nextIndex
                plannedNextIndex = null
                prefetchedFor = null
                prefetchedUrl = null
                _currentSong.value = q[nextIndex]
                _positionMs.value = 0
                _error.value = null
                scope.launch { repo.recordPlayed(q[nextIndex]) }
                p.seekToNextMediaItem()
                // Gleich den übernächsten Song vorbereiten.
                maybePrefetchNext()
                return
            }
        }
        queueIndex = nextIndex
        loadAndPlay(q[nextIndex])
    }

    /**
     * ExoPlayer ist selbständig in den vorbufferten nächsten Song gewechselt
     * (der von [maybePrefetchNext] in die Playlist gehängt wurde) — hier nur
     * noch den Controller-State hinterherziehen, kein erneutes Laden.
     */
    private fun onAutoAdvanced() {
        val q = _queue.value
        if (q.isEmpty() || queueIndex !in q.indices) return
        val p = player
        // Abgespieltes Item aus der Player-Playlist entfernen — ExoPlayer räumt
        // nicht selbst auf. Ohne das bliebe die Playlist dauerhaft bei 2 Items
        // und maybePrefetchNext könnte nie wieder anhängen (Muster „lückenlos,
        // Lücke, lückenlos, Lücke"). Danach: aktuell + höchstens 1 eingeplant.
        if (p != null && p.currentMediaItemIndex > 0) p.removeMediaItem(0)
        val nextIndex = QueueNavigator.autoAdvanceIndex(
            queue = q,
            currentIndex = queueIndex,
            playingMediaId = p?.currentMediaItem?.mediaId,
            plannedNextIndex = plannedNextIndex,
        )
        plannedNextIndex = null
        prefetchedFor = null
        prefetchedUrl = null
        queueIndex = nextIndex
        val song = q[nextIndex]
        _currentSong.value = song
        _positionMs.value = 0
        scope.launch { repo.recordPlayed(song) }
        // Gleich den übernächsten Song vorbereiten.
        maybePrefetchNext()
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
        launchAutoplayExtend(resumePlayback = true)
    }

    /**
     * Startet die Autoplay-Suche früh, damit die Suchkaskade (Backend-Suche +
     * Retries) nicht erst in der Hörlücke am Queue-Ende beginnt: sobald der
     * vorletzte Song läuft oder der letzte über die Hälfte hinaus ist.
     */
    private fun maybeExtendQueueEarly() {
        if (earlyExtendDone || autoplayJob?.isActive == true) return
        val q = _queue.value
        if (q.isEmpty() || queueIndex !in q.indices) return
        val p = player ?: return
        val nearTail = when {
            // Vorletzter Song: der Nachschub hat eine volle Songlänge Zeit.
            queueIndex == q.size - 2 -> true
            // Letzter Song: ab der Hälfte suchen, damit sie vor dem Ende durch ist.
            queueIndex == q.size - 1 -> p.duration > 0 && p.currentPosition > p.duration / 2
            else -> false
        }
        if (!nearTail) return
        if (!connectivity.currentlyOnline()) return
        launchAutoplayExtend(resumePlayback = false)
    }

    /**
     * Sucht Autoplay-Nachschub und hängt ihn an die Queue. Läuft bereits eine
     * früh gestartete Suche, wird mit [resumePlayback] nur vermerkt, dass am
     * Queue-Ende weitergespielt werden soll — keine doppelte Suche.
     */
    private fun launchAutoplayExtend(resumePlayback: Boolean) {
        if (autoplayJob?.isActive == true) {
            resumeAfterExtend = resumeAfterExtend || resumePlayback
            if (resumePlayback) _isBuffering.value = true
            return
        }
        val seed = _currentSong.value ?: run {
            if (resumePlayback) _isPlaying.value = false
            return
        }
        earlyExtendDone = true
        resumeAfterExtend = resumePlayback
        if (resumePlayback) _isBuffering.value = true
        autoplayJob = scope.launch {
            val current = _queue.value
            var more = emptyList<MusicSong>()
            if (connectivity.currentlyOnline()) {
                for (attempt in 1..3) {
                    more = try {
                        repo.getAutoplaySongs(seed, current.map { it.videoId }.toSet())
                    } catch (e: CancellationException) {
                        // Abbruch (z. B. eigene Songwahl via play()) ist kein
                        // Fetch-Fehler — nicht als „leeres Ergebnis" weiterlaufen.
                        throw e
                    } catch (e: Exception) {
                        emptyList()
                    }
                    if (more.isNotEmpty()) break
                    if (attempt < 3) delay(3_000L * attempt)
                }
            }
            // Ab hier nichts mehr schreiben, wenn der Job unterwegs abgebrochen
            // wurde: play() hat Queue und Player längst ersetzt — der tote Job
            // würde sonst die neue User-Queue mit der alten überschreiben und
            // ihr Buffering-Flag löschen (Player stumm, Next/Prev wirkungslos).
            ensureActive()

            val fresh = more.filter { n -> current.none { it.videoId == n.videoId } }
            if (fresh.isEmpty()) {
                _isBuffering.value = false
                if (resumeAfterExtend) {
                    // Nichts Passendes gefunden — lieber von vorn als abwürgen.
                    if (current.size > 1) {
                        queueIndex = 0
                        loadAndPlay(current[0])
                    } else {
                        _isPlaying.value = false
                    }
                }
                return@launch
            }
            _queue.value = current + fresh
            // Neues Queue-Ende — der Früh-Extend darf sich erneut armieren.
            earlyExtendDone = false
            val shouldResume = resumeAfterExtend
            resumeAfterExtend = false
            _isBuffering.value = false
            if (shouldResume && player?.isPlaying != true) {
                queueIndex = current.size
                loadAndPlay(fresh.first())
            } else {
                // URL des ersten neuen Songs direkt vorziehen, damit der
                // Übergang am Queue-Ende vorgebuffert ablaufen kann.
                maybePrefetchNext()
            }
        }
    }

    /**
     * Löst die Stream-URL des nächsten Songs vor und hängt ihn als MediaItem
     * an die ExoPlayer-Playlist — ExoPlayer buffert ihn dann schon, während
     * der aktuelle Song noch läuft, und der Übergang ist lückenlos. Läuft kurz
     * nach Songstart statt erst in der letzten Minute, weil die URL-Auflösung
     * selbst 1–2 s dauern kann und so auch der Prefetch mehr Vorlauf hat.
     */
    private fun maybePrefetchNext() {
        // Repeat-One spielt ohnehin denselben Song erneut — nichts einzuplanen.
        if (_repeatMode.value == REPEAT_ONE) return
        // Im Video-Modus keine Audio-Items einplanen — der Übergang löst die
        // passende Video-Quelle über loadAndPlay auf.
        if (_videoMode.value) return
        val currentId = _currentSong.value?.videoId ?: return
        val next = nextSong() ?: return
        if (prefetchedFor == next.videoId || prefetchJob?.isActive == true) return
        if (!connectivity.currentlyOnline()) return
        prefetchJob = scope.launch {
            val localFile = downloads.localFile(next.videoId)
            val uri = when {
                localFile != null -> "file://${localFile.absolutePath}" // lokale Datei braucht kein Netz
                else -> runCatching { repo.getAudioStream(next.videoId) }.getOrNull()
            }
            // Auch ein Fehlschlag wird vermerkt — sonst hämmert der 500-ms-Tick
            // bei totem Netz immer wieder dieselbe Auflösung. loadAndPlay löst
            // beim Übergang selbst noch einmal auf.
            prefetchedFor = next.videoId
            prefetchedUrl = uri
            if (uri == null) return@launch
            // Nur einreihen, wenn der Player noch denselben Song spielt —
            // sonst hinge das Item an einer fremden Playlist.
            val p = player ?: return@launch
            if (_currentSong.value?.videoId != currentId) return@launch
            if (p.currentMediaItem?.mediaId != currentId) return@launch
            // ID- statt Count-Check: die Playlist enthält aktuell + höchstens
            // ein eingeplantes Folge-Item (onAutoAdvanced räumt abgespielte
            // ab) — doppelt anhängen darf trotzdem nicht passieren.
            if (p.getMediaItemAt(p.mediaItemCount - 1).mediaId != next.videoId) {
                p.addMediaItem(mediaItemFor(next, uri))
            }
        }
    }

    /**
     * Tatsächlich nächster Song. Bei Shuffle wird er einmalig gewürfelt und in
     * [plannedNextIndex] gemerkt, damit Prefetch und Übergang (manuell wie
     * automatisch) denselben Song treffen.
     */
    private fun nextSong(): MusicSong? {
        val q = _queue.value
        if (q.isEmpty() || queueIndex !in q.indices) return null
        if (_shuffle.value && q.size > 1) {
            val i = plannedNextIndex?.takeIf { it in q.indices && it != queueIndex }
                ?: run {
                    var r: Int
                    do { r = q.indices.random() } while (r == queueIndex)
                    plannedNextIndex = r
                    r
                }
            return q[i]
        }
        plannedNextIndex = null
        val nextIndex = queueIndex + 1
        return when {
            nextIndex < q.size -> q[nextIndex]
            _repeatMode.value == REPEAT_ALL -> q[0]
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
                        if (d > 0) _durationMs.value = d
                        // Kurz nach Songstart den nächsten Übergang vorbereiten
                        // (URL auflösen + Item in die ExoPlayer-Playlist hängen).
                        if (it.currentPosition > 5_000) maybePrefetchNext()
                        // Läuft die Queue aus, den Autoplay-Nachschub schon
                        // suchen, bevor die Suchkaskade in der Hörlücke startet.
                        maybeExtendQueueEarly()
                        // Läuft ein Song gesund, waren frühere Fehler nur
                        // vorübergehend — Zähler zurücksetzen.
                        if (it.currentPosition > 15_000) {
                            streamRetries = 0
                            consecutiveFailures = 0
                            videoRetries = 0
                        }
                    }
                }
                delay(500)
            }
        }
    }
}
