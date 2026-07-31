package com.hikari.app.ui.music

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hikari.app.domain.model.MusicSong
import com.hikari.app.domain.repo.DiscoverSection
import com.hikari.app.domain.repo.MusicRepository
import com.hikari.app.player.MusicPlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@HiltViewModel
class MusicViewModel @Inject constructor(
    private val repo: MusicRepository,
    val player: MusicPlayerController,
) : ViewModel() {

    var searchQuery by mutableStateOf("")
    var searchResults by mutableStateOf<List<MusicSong>>(emptyList())
    var searchLoading by mutableStateOf(false)
    var searchAttempted by mutableStateOf(false)

    var discoverSections by mutableStateOf<List<DiscoverSection>>(emptyList())
    var discoverLoading by mutableStateOf(false)
    var discoverFailed by mutableStateOf(false)

    var history by mutableStateOf<List<MusicSong>>(emptyList())
    var favorites by mutableStateOf<List<MusicSong>>(emptyList())

    /** Single source of truth for hearts across all lists. */
    var favoriteIds by mutableStateOf<Set<String>>(emptySet())

    private var searchJob: Job? = null

    init {
        loadDiscover()
        refreshLibrary()
    }

    fun loadDiscover() {
        if (discoverLoading) return
        viewModelScope.launch {
            discoverLoading = true
            discoverFailed = false
            val sections = repo.getDiscoverSections()
            discoverSections = sections
            discoverFailed = sections.isEmpty()
            discoverLoading = false
        }
    }

    fun search(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            searchLoading = true
            searchAttempted = true
            searchResults = repo.searchMusic(q)
            searchLoading = false
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        searchQuery = ""
        searchResults = emptyList()
        searchAttempted = false
        searchLoading = false
    }

    fun refreshLibrary() {
        viewModelScope.launch {
            history = repo.getHistory()
            favorites = repo.getFavorites()
            favoriteIds = favorites.map { it.videoId }.toSet()
        }
    }

    fun play(song: MusicSong, contextQueue: List<MusicSong>) {
        player.play(song, contextQueue)
        // playback start writes to history — reflect it in the UI shortly after
        viewModelScope.launch {
            kotlinx.coroutines.delay(600)
            refreshLibrary()
        }
    }

    fun toggleFavorite(song: MusicSong) {
        viewModelScope.launch {
            val nowFavorite = repo.toggleFavorite(song)
            favoriteIds = if (nowFavorite) favoriteIds + song.videoId else favoriteIds - song.videoId
            history = repo.getHistory()
            favorites = repo.getFavorites()
        }
    }

    fun removeFromHistory(song: MusicSong) {
        viewModelScope.launch {
            repo.removeSong(song)
            refreshLibrary()
        }
    }
}
