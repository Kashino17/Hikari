package com.hikari.app.ui.channels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hikari.app.data.api.dto.ChannelSearchResultDto
import com.hikari.app.data.api.dto.ChannelStatsDto
import com.hikari.app.domain.model.Channel
import com.hikari.app.domain.repo.ChannelsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
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

    // ── Search ───────────────────────────────────────────────────────────────
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _searchResults = MutableStateFlow<List<ChannelSearchResultDto>>(emptyList())
    val searchResults: StateFlow<List<ChannelSearchResultDto>> = _searchResults.asStateFlow()

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    init {
        load()
        // Debounce so we don't fire a search on every keystroke
        viewModelScope.launch {
            _query
                .debounce(300)
                .distinctUntilChanged()
                .collectLatest { q ->
                    val trimmed = q.trim()
                    if (trimmed.length < 2) {
                        _searchResults.value = emptyList()
                        _searching.value = false
                        return@collectLatest
                    }
                    _searching.value = true
                    runCatching { repo.search(trimmed) }
                        .onSuccess { _searchResults.value = it }
                        .onFailure { _error.value = it.message ?: "Suche fehlgeschlagen" }
                    _searching.value = false
                }
        }
    }

    fun setQuery(q: String) { _query.value = q }
    fun clearQuery() {
        _query.value = ""
        _searchResults.value = emptyList()
    }

    fun load() = viewModelScope.launch {
        _busy.value = true
        runCatching { repo.listWithStats() }
            .onSuccess { _channels.value = it; _error.value = null }
            .onFailure { _error.value = it.message ?: "Unknown error" }
        _busy.value = false
    }

    /** Subscribe directly from a search result — uses the channelUrl as before. */
    fun follow(result: ChannelSearchResultDto) = viewModelScope.launch {
        _busy.value = true
        runCatching { repo.add(result.channelUrl) }
            .onSuccess {
                _error.value = null
                // Mark as subscribed in the search results so the button flips
                _searchResults.value = _searchResults.value.map {
                    if (it.channelId == result.channelId) it.copy(subscribed = true) else it
                }
            }
            .onFailure { _error.value = it.message ?: "Hinzufügen fehlgeschlagen" }
        _busy.value = false
        load()
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

    fun poll(channelId: String) = doFetch(channelId, deep = false)

    fun deepScan(channelId: String) = doFetch(channelId, deep = true)

    private fun doFetch(channelId: String, deep: Boolean) = viewModelScope.launch {
        _pollStatus.value = if (deep) "Tiefensuche läuft… (kann dauern)" else "Prüfe Kanal…"
        runCatching {
            if (deep) repo.deepScan(channelId) else repo.poll(channelId)
        }.onSuccess { resp ->
            _pollStatus.value = when {
                resp.queued == 0 && resp.skipped > 0 ->
                    "Keine neuen Videos (${resp.skipped} schon erfasst)"
                resp.queued == 1 ->
                    "1 neues Video wird verarbeitet…"
                resp.queued > 1 ->
                    "${resp.queued} neue Videos werden verarbeitet…"
                else ->
                    "Kein Video gefunden"
            }
            kotlinx.coroutines.delay(5_000)
            _pollStatus.value = null
            load()
        }.onFailure {
            _pollStatus.value = "Fehler: ${it.message}"
            kotlinx.coroutines.delay(5_000)
            _pollStatus.value = null
        }
    }
}
