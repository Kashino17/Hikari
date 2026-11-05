package com.hikari.app.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.hikari.app.data.prefs.SettingsStore
import com.hikari.app.domain.russian.RuPodcastPlayback
import com.hikari.app.domain.russian.RussianProgressStore
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

data class PodcastPlayback(
    val day: Int? = null,
    val title: String = "",
    val videoId: String = "",
    val playing: Boolean = false,
    val loading: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val bufferedMs: Long = 0,
    val speed: Float = 1f,
    /** Gesetzt, wenn die Folge an einer gespeicherten Stelle fortgesetzt wurde. */
    val resumedFromMs: Long? = null,
    val error: Boolean = false,
) {
    val coverUrl: String get() = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
    val fraction: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

/** Gespeicherter Hörstand einer Lektion. */
data class PodcastSpot(val positionMs: Long, val durationMs: Long)

/**
 * Der Podcast-Player des Russisch-Kurses. Gehört der App, nicht einem
 * Bildschirm: Scrollen, Zurückgehen oder der Hintergrund können ihn nicht
 * zerstören (früher war es eine WebView als LazyColumn-Item, die beim
 * Wegscrollen verworfen wurde). Spielt nur den Ton — die Podcast-"Videos"
 * sind ein Standbild — über `/music/audio`, das fertig geladene Dateien
 * direkt von der Server-Platte liefert.
 */
@Singleton
@OptIn(UnstableApi::class)
class RussianPodcastPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttp: OkHttpClient,
    private val settings: SettingsStore,
    private val store: RussianProgressStore,
    private val music: MusicPlayerController,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val prefs = context.getSharedPreferences("hikari_russian", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(PodcastPlayback(speed = prefs.getFloat(KEY_SPEED, 1f)))
    val state: StateFlow<PodcastPlayback> = _state.asStateFlow()

    private val _spots = MutableStateFlow(loadSpots())
    /** Hörstand je Lektion — für Fortschrittsringe und "Weiterhören". */
    val spots: StateFlow<Map<Int, PodcastSpot>> = _spots.asStateFlow()

    val lastDay: Int? get() = prefs.getInt(KEY_LAST, 0).takeIf { it > 0 }

    private var player: ExoPlayer? = null
    private var ticker: Job? = null
    private var retries = 0
    private var lastSaveAt = 0L
    private var controllerFuture: ListenableFuture<MediaController>? = null

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(playing = isPlaying) }
            if (isPlaying) startTicker() else saveNow()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val p = player ?: return
            _state.update {
                it.copy(
                    loading = playbackState == Player.STATE_BUFFERING,
                    durationMs = p.duration.takeIf { d -> d > 0 } ?: it.durationMs,
                )
            }
            if (playbackState == Player.STATE_READY) retries = 0
            if (playbackState == Player.STATE_ENDED) {
                saveNow()
                _state.value.day?.let(::markListened)
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            // YouTube drosselt in Wellen (403/503) — mit Abstand neu versuchen,
            // an derselben Stelle.
            val p = player ?: return
            if (retries < MAX_RETRIES) {
                retries++
                val at = p.currentPosition
                scope.launch {
                    delay(RETRY_DELAY_MS * retries)
                    if (player === p) {
                        p.seekTo(at)
                        p.prepare()
                    }
                }
            } else {
                _state.update { it.copy(loading = false, error = true) }
            }
        }
    }

    /** Für den MediaSessionService (Sperrbildschirm, Benachrichtigung, Kopfhörertasten). */
    fun playerForSession(): ExoPlayer = ensurePlayer()

    private fun ensurePlayer(): ExoPlayer = player ?: ExoPlayer.Builder(context)
        .setMediaSourceFactory(
            DefaultMediaSourceFactory(DefaultDataSource.Factory(context, OkHttpDataSource.Factory(okHttp))),
        )
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                .build(),
            /* handleAudioFocus = */ true,
        )
        .setHandleAudioBecomingNoisy(true)
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .setSeekBackIncrementMs(5_000)
        .setSeekForwardIncrementMs(10_000)
        .build()
        .also {
            it.addListener(listener)
            it.playbackParameters = PlaybackParameters(_state.value.speed)
            player = it
        }

    /**
     * Lädt eine Lektion. Ist sie schon geladen, bleibt alles wie es ist
     * (läuft weiter) — nur [autoplay] startet ggf. die Wiedergabe.
     */
    fun load(day: Int, videoId: String, title: String, autoplay: Boolean) {
        if (_state.value.day == day && player?.currentMediaItem != null && !_state.value.error) {
            if (autoplay) play()
            return
        }
        saveNow()
        val saved = _spots.value[day]
        val start = saved?.let { RuPodcastPlayback.resumeStartMs(it.positionMs, it.durationMs) } ?: 0L
        prefs.edit().putInt(KEY_LAST, day).apply()
        retries = 0
        _state.update {
            it.copy(
                day = day, title = title, videoId = videoId, playing = false, loading = true,
                positionMs = start, durationMs = saved?.durationMs ?: 0, bufferedMs = 0,
                resumedFromMs = start.takeIf { s -> s > 0 }, error = false,
            )
        }
        scope.launch {
            val base = runCatching { settings.backendUrl.first().trimEnd('/') }.getOrNull().orEmpty()
            if (base.isBlank()) {
                _state.update { it.copy(loading = false, error = true) }
                return@launch
            }
            val p = ensurePlayer()
            val item = MediaItem.Builder()
                .setUri("$base/music/audio/$videoId")
                .setMediaId("russian-podcast-$day")
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("Lektion $day · $title")
                        .setArtist("RusslandJournal")
                        .setAlbumTitle("Hikari · Podcast-Kurs")
                        .setArtworkUri(Uri.parse(_state.value.coverUrl))
                        .build(),
                )
                .build()
            p.setMediaItem(item, start)
            p.prepare()
            if (autoplay) play()
            connectSession()
            queueServerDownload(base, videoId)
        }
    }

    /** Server lädt die Folge(n) auf die Platte — spätere Wiedergaben und Sprünge brauchen YouTube nicht mehr. */
    fun prefetch(videoIds: List<String>) {
        scope.launch {
            val base = runCatching { settings.backendUrl.first().trimEnd('/') }.getOrNull().orEmpty()
            if (base.isNotBlank()) videoIds.forEach { queueServerDownload(base, it) }
        }
    }

    private suspend fun queueServerDownload(base: String, videoId: String) = withContext(Dispatchers.IO) {
        runCatching {
            okHttp.newCall(
                Request.Builder().url("$base/music/download/$videoId")
                    .post("".toRequestBody("application/json".toMediaType()))
                    .build(),
            ).execute().close()
        }
    }

    fun play() {
        val p = player ?: return
        if (music.isPlaying.value) music.toggle()
        if (_state.value.error) {
            _state.update { it.copy(error = false, loading = true) }
            retries = 0
            p.prepare()
        }
        if (p.playbackState == Player.STATE_ENDED) p.seekTo(0)
        p.play()
    }

    fun pause() {
        player?.pause()
    }

    fun toggle() {
        if (player?.isPlaying == true) pause() else play()
    }

    fun seekTo(ms: Long) {
        val p = player ?: return
        val dur = p.duration.takeIf { it > 0 } ?: _state.value.durationMs
        val target = if (dur > 0) ms.coerceIn(0, dur - 500) else ms.coerceAtLeast(0)
        p.seekTo(target)
        _state.update { it.copy(positionMs = target, resumedFromMs = null) }
        saveNow()
    }

    fun skipBy(deltaMs: Long) {
        val p = player ?: return
        seekTo(p.currentPosition + deltaMs)
    }

    fun restart() {
        seekTo(0)
        play()
    }

    fun cycleSpeed() {
        val next = RuPodcastPlayback.nextSpeed(_state.value.speed)
        player?.playbackParameters = PlaybackParameters(next)
        prefs.edit().putFloat(KEY_SPEED, next).apply()
        _state.update { it.copy(speed = next) }
    }

    fun dismissResumeHint() {
        _state.update { it.copy(resumedFromMs = null) }
    }

    /** Mini-Player schließen: stoppen, Stand sichern, Benachrichtigung weg. */
    fun close() {
        saveNow()
        player?.let {
            it.stop()
            it.clearMediaItems()
        }
        ticker?.cancel()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        _state.update { PodcastPlayback(speed = it.speed) }
    }

    fun markListened(day: Int) {
        if (day in store.state.value.podcastListened) return
        store.update { it.copy(podcastListened = it.podcastListened + day) }
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (isActive) {
                val p = player ?: break
                val dur = p.duration.takeIf { it > 0 } ?: 0
                _state.update {
                    it.copy(
                        positionMs = p.currentPosition,
                        bufferedMs = p.bufferedPosition,
                        durationMs = if (dur > 0) dur else it.durationMs,
                    )
                }
                if (System.currentTimeMillis() - lastSaveAt > SAVE_EVERY_MS) saveNow()
                if (!p.isPlaying) break
                delay(TICK_MS)
            }
        }
    }

    /** Hörstand sichern — alle paar Sekunden, bei Pause, beim Schließen. */
    fun saveNow() {
        val p = player ?: return
        val day = _state.value.day ?: return
        val pos = p.currentPosition
        val dur = p.duration.takeIf { it > 0 } ?: _state.value.durationMs
        if (pos < 1_000 && dur <= 0) return
        lastSaveAt = System.currentTimeMillis()
        prefs.edit().putLong("$KEY_POS$day", pos).putLong("$KEY_DUR$day", dur).apply()
        _spots.update { it + (day to PodcastSpot(pos, dur)) }
        if (RuPodcastPlayback.isListened(pos, dur)) markListened(day)
    }

    private fun loadSpots(): Map<Int, PodcastSpot> = prefs.all.keys
        .filter { it.startsWith(KEY_POS) }
        .mapNotNull { key ->
            val day = key.removePrefix(KEY_POS).toIntOrNull() ?: return@mapNotNull null
            day to PodcastSpot(prefs.getLong(key, 0), prefs.getLong("$KEY_DUR$day", 0))
        }
        .toMap()

    /**
     * Erst die Bindung eines MediaControllers an den Service holt ihn in den
     * Vordergrund und zeigt die Benachrichtigung — sonst killt Android ihn
     * im Hintergrund (siehe MusicPlayerController).
     */
    private fun connectSession() {
        if (controllerFuture != null) return
        val token = SessionToken(context, ComponentName(context, RussianPodcastService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        controllerFuture = future
        future.addListener({ runCatching { future.get() } }, ContextCompat.getMainExecutor(context))
    }

    private companion object {
        const val KEY_POS = "podcast_pos_"
        const val KEY_DUR = "podcast_dur_"
        const val KEY_SPEED = "podcast_speed"
        const val KEY_LAST = "podcast_last_day"
        const val TICK_MS = 250L
        const val SAVE_EVERY_MS = 3_000L
        const val MAX_RETRIES = 4
        const val RETRY_DELAY_MS = 2_000L
    }
}
