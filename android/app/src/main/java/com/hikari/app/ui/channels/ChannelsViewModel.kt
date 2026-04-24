package com.hikari.app.ui.channels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hikari.app.data.api.dto.ChannelStatsDto
import com.hikari.app.domain.model.Channel
import com.hikari.app.domain.repo.ChannelsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ChannelsViewModel @Inject constructor(
    private val repo: ChannelsRepository,
) : ViewModel() {

    private val _channels = MutableStateFlow<List<Pair<Channel, ChannelStatsDto?>>>(emptyList())
    val channels: StateFlow<List<Pair<Channel, ChannelStatsDto?>>> = _channels.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _pollStatus = MutableStateFlow<String?>(null)
    val pollStatus: StateFlow<String?> = _pollStatus.asStateFlow()

    init { load() }

    fun load() = viewModelScope.launch {
        _busy.value = true
        runCatching { repo.listWithStats() }
            .onSuccess { _channels.value = it; _error.value = null }
            .onFailure { _error.value = it.message ?: "Unknown error" }
        _busy.value = false
    }

    fun add(url: String) = viewModelScope.launch {
        _busy.value = true
        runCatching { repo.add(url) }
            .onSuccess { _error.value = null }
            .onFailure { _error.value = it.message ?: "Couldn't add channel" }
        _busy.value = false
        load()
    }

    fun remove(channelId: String) = viewModelScope.launch {
        runCatching { repo.remove(channelId) }
        load()
    }

    fun poll(channelId: String) = viewModelScope.launch {
        _pollStatus.value = "Prüfe Kanal…"
        runCatching { repo.poll(channelId) }
            .onSuccess { resp ->
                _pollStatus.value = when {
                    resp.queued == 0 && resp.skipped > 0 ->
                        "Keine neuen Videos (${resp.skipped} schon erfasst)"
                    resp.queued == 1 ->
                        "1 neues Video wird verarbeitet…"
                    resp.queued > 1 ->
                        "${resp.queued} neue Videos werden verarbeitet…"
                    else ->
                        "Kanal-RSS leer"
                }
                // Auto-dismiss after 5 seconds
                kotlinx.coroutines.delay(5_000)
                _pollStatus.value = null
                // Reload stats so user sees the updated numbers
                load()
            }
            .onFailure {
                _pollStatus.value = "Fehler: ${it.message}"
                kotlinx.coroutines.delay(5_000)
                _pollStatus.value = null
            }
    }
}
