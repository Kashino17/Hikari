package com.hikari.app.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
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
            _error.value = "Wiedergabe fehlgeschlagen — Song wird übersprungen"
            skipAfterFailure()
        }
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

    fun play(song: MusicSong, contextQueue: List<MusicSong> = emptyList()) {
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
        player?.stop()
        player?.clearMediaItems()
        _currentSong.value = null
        _isPlaying.value = false
        _positionMs.value = 0
        _durationMs.value = 0
    }

    private fun loadAndPlay(song: MusicSong) {
        loadJob?.cancel()
        _currentSong.value = song
        _positionMs.value = 0
        _durationMs.value = 0
        _error.value = null
        _isBuffering.value = true
        loadJob = scope.launch {
            val url = repo.getAudioStream(song.videoId)
            if (url == null) {
                _isBuffering.value = false
                _error.value = "„${song.title}“ ist nicht abspielbar"
                skipAfterFailure()
                return@launch
            }
            scope.launch { repo.recordPlayed(song) }
            val p = ensurePlayer()
            p.setMediaItem(MediaItem.fromUri(url))
            p.prepare()
            p.play()
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

    /** After a load/playback failure move on, but never loop forever. */
    private var consecutiveFailures = 0

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
        if (manual) consecutiveFailures = 0

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
                    _repeatMode.value == REPEAT_ALL || manual -> 0
                    else -> {
                        _isPlaying.value = false
                        return // end of queue, repeat off
                    }
                }
            }
            else -> if (queueIndex - 1 >= 0) queueIndex - 1 else q.size - 1
        }
        queueIndex = nextIndex
        loadAndPlay(q[nextIndex])
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
                    }
                }
                delay(500)
            }
        }
    }
}
