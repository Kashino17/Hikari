package com.hikari.app.ui.music

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hikari.app.domain.model.MusicPlaylist
import com.hikari.app.domain.model.MusicSong
import com.hikari.app.domain.model.PlaylistSong
import com.hikari.app.domain.repo.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MusicViewModel @Inject constructor(
    private val repo: MusicRepository,
) : ViewModel() {

    var searchQuery by mutableStateOf("")
    var searchResults by mutableStateOf<List<MusicSong>>(emptyList())
    var searchLoading by mutableStateOf(false)
    var searchError by mutableStateOf<String?>(null)

    var suggestions by mutableStateOf<List<MusicSong>>(emptyList())
    var suggestionsLoading by mutableStateOf(false)

    var allSongs by mutableStateOf<List<MusicSong>>(emptyList())
    var favorites by mutableStateOf<List<MusicSong>>(emptyList())

    var playlists by mutableStateOf<List<MusicPlaylist>>(emptyList())
    var playlistSongs by mutableStateOf<List<PlaylistSong>>(emptyList())
    var currentPlaylist by mutableStateOf<MusicPlaylist?>(null)

    // Playback state
    var currentSong by mutableStateOf<MusicSong?>(null)
    var isPlaying by mutableStateOf(false)
    var isShuffled by mutableStateOf(false)
    var repeatMode by mutableStateOf(0) // 0=off, 1=all, 2=one
    var queue by mutableStateOf<List<MusicSong>>(emptyList())
    var queueIndex by mutableStateOf(-1)

    var newPlaylistName by mutableStateOf("")
    var showCreatePlaylistDialog by mutableStateOf(false)

    // Audio URL loaded
    var audioUrl by mutableStateOf<String?>(null)

    fun search(query: String) {
        searchQuery = query
        viewModelScope.launch {
            searchLoading = true
            searchError = null
            try {
                searchResults = repo.searchMusic(query)
            } catch (e: Exception) {
                searchError = e.message ?: "Search failed"
                searchResults = emptyList()
            }
            searchLoading = false
        }
    }

    fun loadSuggestions() {
        viewModelScope.launch {
            suggestionsLoading = true
            try {
                suggestions = repo.getMusicSuggestions()
            } catch (_: Exception) {
                suggestions = emptyList()
            }
            suggestionsLoading = false
        }
    }

    fun loadAllSongs() {
        viewModelScope.launch {
            allSongs = repo.getAllSongs()
        }
    }

    fun loadFavorites() {
        viewModelScope.launch {
            favorites = repo.getFavorites()
        }
    }

    fun loadPlaylists() {
        viewModelScope.launch {
            playlists = repo.getPlaylists()
        }
    }

    fun openPlaylist(playlist: MusicPlaylist) {
        currentPlaylist = playlist
        viewModelScope.launch {
            playlistSongs = repo.getPlaylistSongs(playlist.id)
        }
    }

    fun createPlaylist(name: String, description: String = "") {
        viewModelScope.launch {
            val p = repo.createPlaylist(name, description)
            playlists = repo.getPlaylists()
        }
    }

    fun deletePlaylist(playlist: MusicPlaylist) {
        viewModelScope.launch {
            repo.deletePlaylist(playlist)
            playlists = repo.getPlaylists()
        }
    }

    fun addToPlaylist(playlist: MusicPlaylist, song: MusicSong) {
        viewModelScope.launch {
            repo.addSongToPlaylist(playlist.id, song)
            if (currentPlaylist?.id == playlist.id) {
                playlistSongs = repo.getPlaylistSongs(playlist.id)
            }
        }
    }

    fun removeFromPlaylist(song: MusicSong) {
        currentPlaylist?.let { pl ->
            viewModelScope.launch {
                repo.removeSongFromPlaylist(pl.id, song)
                playlistSongs = repo.getPlaylistSongs(pl.id)
            }
        }
    }

    fun toggleFavorite(song: MusicSong) {
        viewModelScope.launch {
            repo.toggleFavorite(song.videoId)
            if (song.isFavorite) {
                favorites = favorites.filter { it.videoId != song.videoId }
            } else {
                loadFavorites()
            }
        }
    }

    fun playSong(song: MusicSong, queue: List<MusicSong> = emptyList(), index: Int = -1) {
        currentSong = song
        isPlaying = true
        if (queue.isNotEmpty()) {
            this.queue = queue
            queueIndex = if (index >= 0) index else this.queue.indexOfFirst { it.videoId == song.videoId }
        } else {
            this.queue = listOf(song)
            queueIndex = 0
        }
        // Load audio stream
        viewModelScope.launch {
            audioUrl = repo.getAudioStream(song.videoId)
        }
    }

    fun playNext() {
        if (queue.isEmpty()) return
        val nextIdx = if (queueIndex + 1 < queue.size) queueIndex + 1
        else if (repeatMode == 1) 0
        else return
        currentSong = queue[nextIdx]
        queueIndex = nextIdx
        viewModelScope.launch {
            audioUrl = repo.getAudioStream(currentSong!!.videoId)
        }
    }

    fun playPrevious() {
        if (queue.isEmpty()) return
        val prevIdx = if (queueIndex - 1 >= 0) queueIndex - 1
        else if (repeatMode == 1) queue.size - 1
        else return
        currentSong = queue[prevIdx]
        queueIndex = prevIdx
        viewModelScope.launch {
            audioUrl = repo.getAudioStream(currentSong!!.videoId)
        }
    }

    fun togglePlayPause() {
        isPlaying = !isPlaying
    }

    fun removeFromLibrary(song: MusicSong) {
        viewModelScope.launch {
            repo.removeSong(song)
            loadAllSongs()
        }
    }
}
