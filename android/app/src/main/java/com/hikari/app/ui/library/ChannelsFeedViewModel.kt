package com.hikari.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hikari.app.data.api.dto.LibraryVideoDto
import com.hikari.app.domain.repo.FeedRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Was der Kanal-Bereich gerade zeigt. */
data class ChannelsFeedState(
    /** null = Startseite mit den Videos aller Kanäle. */
    val channelId: String? = null,
    val query: String = "",
    val videos: List<LibraryVideoDto> = emptyList(),
    val loading: Boolean = false,
    /** Es gibt nichts mehr nachzuladen. */
    val endReached: Boolean = false,
)

/**
 * Lädt die Videos der abonnierten Kanäle seitenweise — fünf auf einmal, die
 * nächsten erst, wenn man ans Ende scrollt. Kanal-Filter und Suche starten
 * die Liste neu.
 */
@HiltViewModel
class ChannelsFeedViewModel @Inject constructor(
    private val repo: FeedRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ChannelsFeedState())
    val state: StateFlow<ChannelsFeedState> = _state.asStateFlow()

    /** Rückfall für ältere Server ohne Seiten-Endpunkt: die Uploads aus der Bibliothek. */
    private var fallback: List<LibraryVideoDto> = emptyList()
    private var loadJob: Job? = null
    private var searchJob: Job? = null

    fun start(fallbackUploads: List<LibraryVideoDto>) {
        fallback = fallbackUploads
        if (_state.value.videos.isEmpty() && !_state.value.loading) reload()
    }

    fun selectChannel(channelId: String?) {
        if (_state.value.channelId == channelId) return
        _state.update { it.copy(channelId = channelId) }
        reload()
    }

    /** Sucht beim Tippen — mit kurzer Pause, damit nicht jeder Buchstabe eine Anfrage auslöst. */
    fun setQuery(query: String) {
        if (_state.value.query == query) return
        _state.update { it.copy(query = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            reload()
        }
    }

    private fun reload() {
        loadJob?.cancel()
        _state.update { it.copy(videos = emptyList(), endReached = false, loading = false) }
        loadMore()
    }

    fun loadMore() {
        val current = _state.value
        if (current.loading || current.endReached) return
        _state.update { it.copy(loading = true) }
        loadJob = viewModelScope.launch {
            val offset = current.videos.size
            val page = runCatching {
                repo.getUploads(current.channelId, current.query, offset, PAGE_SIZE)
            }.getOrElse {
                // Server kennt den Endpunkt nicht oder ist kurz weg: aus dem vorhandenen Bestand bedienen.
                localPage(current, offset)
            }
            _state.update {
                // Nur übernehmen, wenn Filter und Suche inzwischen dieselben sind.
                if (it.channelId != current.channelId || it.query != current.query) it
                else it.copy(
                    videos = (it.videos + page).distinctBy { v -> v.id },
                    loading = false,
                    endReached = page.size < PAGE_SIZE,
                )
            }
        }
    }

    private fun localPage(state: ChannelsFeedState, offset: Int): List<LibraryVideoDto> =
        fallback
            .filter { state.channelId == null || it.channel_id == state.channelId }
            .filter {
                state.query.isBlank() ||
                    it.title.contains(state.query, ignoreCase = true) ||
                    it.channelTitle?.contains(state.query, ignoreCase = true) == true
            }
            .sortedByDescending { it.published_at }
            .drop(offset)
            .take(PAGE_SIZE)

    private companion object {
        const val PAGE_SIZE = 5
        const val SEARCH_DEBOUNCE_MS = 300L
    }
}
