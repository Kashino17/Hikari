package com.hikari.app.ui.music

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hikari.app.data.db.LocalMusicDownloadEntity
import com.hikari.app.data.net.ConnectivityObserver
import com.hikari.app.domain.download.LocalMusicDownloadManager
import com.hikari.app.domain.model.MusicPlaylist
import com.hikari.app.domain.model.MusicSong
import com.hikari.app.domain.repo.DiscoverSection
import com.hikari.app.domain.repo.MusicRepository
import com.hikari.app.domain.repo.PlaylistWithSongs
import com.hikari.app.player.MusicPlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class MusicViewModel @Inject constructor(
    private val repo: MusicRepository,
    private val downloads: LocalMusicDownloadManager,
    connectivity: ConnectivityObserver,
    val player: MusicPlayerController,
) : ViewModel() {

    val isOnline: StateFlow<Boolean> = connectivity.isOnline

    var searchQuery by mutableStateOf("")
    var searchResults by mutableStateOf<List<MusicSong>>(emptyList())
    var searchLoading by mutableStateOf(false)
    var searchAttempted by mutableStateOf(false)

    var discoverSections by mutableStateOf<List<DiscoverSection>>(emptyList())
    var discoverLoading by mutableStateOf(false)
    var discoverFailed by mutableStateOf(false)

    var history by mutableStateOf<List<MusicSong>>(emptyList())
    var favorites by mutableStateOf<List<MusicSong>>(emptyList())
    var playlists by mutableStateOf<List<PlaylistWithSongs>>(emptyList())

    /** Erst nach dem ersten Laden darf die UI aus "nicht gefunden" Schlüsse ziehen. */
    var libraryLoaded by mutableStateOf(false)
        private set

    /** Single source of truth for hearts across all lists. */
    var favoriteIds by mutableStateOf<Set<String>>(emptySet())

    /** Song, für den gerade das "Zu Playlist hinzufügen"-Sheet offen ist. */
    var addToPlaylistTarget by mutableStateOf<MusicSong?>(null)

    var message by mutableStateOf<String?>(null)

    val downloadProgress: StateFlow<Map<String, Float>> = downloads.progress

    val downloadedIds: StateFlow<Set<String>> = downloads.downloadedIds
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val downloadedSongs: StateFlow<List<MusicSong>> = downloads.downloads
        .map { rows -> rows.map { it.toSong() } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Songs des gerade geöffneten Mixes — der Mix wird über seine Suche neu
     *  geladen, damit die Detailseite ohne Zustandsübergabe auskommt. */
    var mixSongs by mutableStateOf<List<MusicSong>>(emptyList())
    var mixLoading by mutableStateOf(false)

    private var searchJob: Job? = null
    private var mixJob: Job? = null

    init {
        loadDiscover()
        refreshLibrary()
        // Kommt das Netz zurück, sind die Entdecken-Vorschläge nachholbar.
        viewModelScope.launch {
            isOnline.collect { online ->
                if (online && discoverSections.isEmpty() && !discoverLoading) loadDiscover()
            }
        }
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

    fun loadMix(query: String) {
        mixJob?.cancel()
        mixJob = viewModelScope.launch {
            mixLoading = true
            mixSongs = repo.getMixSongs(query)
            mixLoading = false
        }
    }

    /** Lädt alle noch fehlenden Songs des offenen Mixes herunter. */
    fun downloadMix(title: String) {
        viewModelScope.launch {
            val missing = mixSongs.filter { it.videoId !in downloadedIds.value }
            if (missing.isEmpty()) {
                message = "Alle Songs sind schon heruntergeladen"
                return@launch
            }
            message = "Lade ${missing.size} Songs herunter…"
            var ok = 0
            missing.forEach { song -> if (downloads.download(song).isSuccess) ok++ }
            message = if (ok == missing.size) {
                "„$title“ ist offline verfügbar"
            } else {
                "$ok von ${missing.size} Songs geladen"
            }
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
            playlists = repo.getPlaylists()
            libraryLoaded = true
        }
    }

    fun play(song: MusicSong, contextQueue: List<MusicSong>) {
        player.play(song, contextQueue)
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

    // --- Downloads ---

    fun downloadSong(song: MusicSong) {
        viewModelScope.launch {
            downloads.download(song)
                .onSuccess { message = "„${song.title}“ ist jetzt offline verfügbar" }
                .onFailure { message = "Download fehlgeschlagen: ${it.message}" }
        }
    }

    fun deleteDownload(videoId: String) {
        viewModelScope.launch {
            downloads.delete(videoId)
            message = "Download entfernt"
        }
    }

    /** Lädt alle noch fehlenden Songs einer Playlist nacheinander herunter. */
    fun downloadPlaylist(entry: PlaylistWithSongs) {
        viewModelScope.launch {
            val missing = entry.songs.filter { it.videoId !in downloadedIds.value }
            if (missing.isEmpty()) {
                message = "Alle Songs sind schon heruntergeladen"
                return@launch
            }
            message = "Lade ${missing.size} Songs herunter…"
            var ok = 0
            missing.forEach { song ->
                if (downloads.download(song).isSuccess) ok++
            }
            message = if (ok == missing.size) {
                "„${entry.playlist.name}“ ist offline verfügbar"
            } else {
                "$ok von ${missing.size} Songs geladen"
            }
            refreshLibrary()
        }
    }

    // --- Playlists ---

    fun createPlaylist(name: String, addAfterwards: MusicSong? = null) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val id = repo.createPlaylist(trimmed)
            addAfterwards?.let { repo.addToPlaylist(id, it) }
            refreshLibrary()
            message = "Playlist „$trimmed“ erstellt"
        }
    }

    fun renamePlaylist(playlist: MusicPlaylist, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            repo.renamePlaylist(playlist, trimmed)
            refreshLibrary()
        }
    }

    fun deletePlaylist(playlist: MusicPlaylist) {
        viewModelScope.launch {
            repo.deletePlaylist(playlist)
            refreshLibrary()
            message = "Playlist gelöscht"
        }
    }

    fun addToPlaylist(playlistId: Int, song: MusicSong) {
        viewModelScope.launch {
            repo.addToPlaylist(playlistId, song)
            refreshLibrary()
            addToPlaylistTarget = null
            message = "Zur Playlist hinzugefügt"
        }
    }

    fun removeFromPlaylist(playlistId: Int, song: MusicSong) {
        viewModelScope.launch {
            repo.removeFromPlaylist(playlistId, song)
            refreshLibrary()
        }
    }

    fun clearMessage() {
        message = null
    }

    private fun LocalMusicDownloadEntity.toSong() = MusicSong(
        videoId = videoId,
        title = title,
        uploader = uploader,
        uploaderUrl = "",
        thumbnailUrl = thumbnailUrl,
        duration = durationSeconds,
        views = 0,
        addedAt = downloadedAt,
    )
}
