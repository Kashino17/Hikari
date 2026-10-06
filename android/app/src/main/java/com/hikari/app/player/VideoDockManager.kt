package com.hikari.app.player

import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.hikari.app.domain.repo.PlaybackRepository
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class ActiveVideoInfo(
    val videoId: String,
    val title: String,
    val channel: String,
    val thumbnailUrl: String = "https://img.youtube.com/vi/$videoId/mqdefault.jpg",
)

@Singleton
class VideoDockManager @Inject constructor(
    private val playbackRepository: Lazy<PlaybackRepository>,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _activeVideo = MutableStateFlow<ActiveVideoInfo?>(null)
    val activeVideo: StateFlow<ActiveVideoInfo?> = _activeVideo.asStateFlow()

    private val _isDocked = MutableStateFlow(false)
    val isDocked: StateFlow<Boolean> = _isDocked.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private var activePlayer: ExoPlayer? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            _isPlaying.value = playing
        }

        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_ENDED) {
                _isPlaying.value = false
            }
        }
    }

    fun getPlayer(): ExoPlayer? = activePlayer

    /** Attaches or updates the active video and its ExoPlayer. */
    fun setActivePlayer(videoId: String, title: String, channel: String, player: ExoPlayer) {
        if (activePlayer != player) {
            activePlayer?.removeListener(playerListener)
            activePlayer = player
            player.addListener(playerListener)
        }
        _activeVideo.value = ActiveVideoInfo(videoId, title, channel)
        _isPlaying.value = player.isPlaying
        _isDocked.value = false
    }

    /** Minimizes current video playback into docked bottom bar */
    fun dock() {
        if (_activeVideo.value != null && activePlayer != null) {
            _isDocked.value = true
        }
    }

    /** Expands from docked mini-player back to fullscreen */
    fun expand() {
        _isDocked.value = false
    }

    /** Toggles play/pause in mini-player */
    fun togglePlayPause() {
        val p = activePlayer ?: return
        if (p.isPlaying) {
            p.pause()
        } else {
            p.play()
        }
    }

    /** Closes mini-player and releases playback */
    fun closeAndRelease(onSavePosition: ((String, Long) -> Unit)? = null) {
        val vid = _activeVideo.value?.videoId
        val pos = activePlayer?.currentPosition ?: 0L
        if (vid != null && pos > 0L) {
            onSavePosition?.invoke(vid, pos)
            scope.launch { runCatching { playbackRepository.get().savePosition(vid, pos) } }
        }
        activePlayer?.removeListener(playerListener)
        activePlayer?.release()
        activePlayer = null
        _activeVideo.value = null
        _isDocked.value = false
        _isPlaying.value = false
    }
}
