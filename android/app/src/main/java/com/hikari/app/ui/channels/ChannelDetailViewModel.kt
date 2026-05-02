package com.hikari.app.ui.channels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hikari.app.data.api.dto.ChannelVideoDto
import com.hikari.app.domain.model.Channel
import com.hikari.app.domain.repo.ChannelsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ChannelDetailViewModel @Inject constructor(
    private val repo: ChannelsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val channelId: String = checkNotNull(savedStateHandle["channelId"])

    private val _channel = MutableStateFlow<Channel?>(null)
    val channel: StateFlow<Channel?> = _channel.asStateFlow()

    private val _videos = MutableStateFlow<List<ChannelVideoDto>>(emptyList())
    val videos: StateFlow<List<ChannelVideoDto>> = _videos.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    fun dismissSyncMessage() { _syncMessage.value = null }

    init { load() }

    fun load() = viewModelScope.launch {
        _loading.value = true
        runCatching {
            // Fetch channel header (from list — no per-id endpoint) + videos in parallel.
            val all = repo.list()
            val ch = all.firstOrNull { it.id == channelId }
            _channel.value = ch
            if (ch == null) _error.value = "Kanal nicht gefunden"
            _videos.value = repo.listVideos(channelId)
        }.onFailure { _error.value = it.message ?: "Konnte Videos nicht laden" }
        _loading.value = false
    }

    fun deleteVideo(videoId: String) = viewModelScope.launch {
        runCatching { repo.deleteVideo(videoId) }
            .onSuccess {
                _videos.value = _videos.value.filterNot { it.videoId == videoId }
            }
            .onFailure { _error.value = it.message ?: "Löschen fehlgeschlagen" }
    }

    fun toggleAutoApprove() = viewModelScope.launch {
        val current = _channel.value ?: return@launch
        val target = !current.autoApprove
        // Optimistic update — flip locally, revert on failure.
        _channel.value = current.copy(autoApprove = target)
        runCatching { repo.setAutoApprove(channelId, target) }
            .onFailure {
                _channel.value = current
                _error.value = it.message ?: "Konnte Vertrauenskanal nicht umschalten"
            }
    }

    fun syncAndClip() = viewModelScope.launch {
        if (_syncing.value) return@launch
        _syncing.value = true
        _error.value = null
        runCatching {
            val pollResult = repo.pollChannel(channelId)
            repo.forceClipperWindow()
            pollResult
        }.onSuccess { result ->
            _syncMessage.value = if (result.queued > 0)
                "Sync: ${result.queued} neu, ${result.skipped} bekannt · Clipper läuft jetzt"
            else
                "Keine neuen Videos · Clipper läuft (${result.skipped} bekannt)"
            // Refresh the videos list — new ones should appear shortly via fire-and-forget pipeline
            load()
        }.onFailure {
            _error.value = it.message ?: "Sync fehlgeschlagen"
        }
        _syncing.value = false
    }
}
