package com.hikari.app.ui.music

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hikari.app.data.db.LocalMusicDownloadEntity
import com.hikari.app.data.net.ConnectivityObserver
import com.hikari.app.data.prefs.ProfileStore
import com.hikari.app.data.prefs.SettingsStore
import com.hikari.app.domain.download.LocalMusicDownloadManager
import com.hikari.app.domain.model.ArtistPage
import com.hikari.app.domain.model.FullSearchResults
import com.hikari.app.domain.model.HomeSection
import com.hikari.app.domain.model.MusicAlbum
import com.hikari.app.domain.model.MusicPlaylist
import com.hikari.app.domain.model.MusicSong
import com.hikari.app.domain.model.RemotePlaylist
import com.hikari.app.domain.model.SearchArtist
import com.hikari.app.domain.repo.ChapterGroup
import com.hikari.app.domain.repo.DiscoverSection
import com.hikari.app.domain.repo.MusicRepository
import com.hikari.app.domain.repo.MusicSearchFilter
import com.hikari.app.domain.repo.MusicSearchMode
import com.hikari.app.domain.repo.PlaylistWithSongs
import com.hikari.app.player.MusicPlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
@HiltViewModel
class MusicViewModel @Inject constructor(
    private val repo: MusicRepository,
    private val downloads: LocalMusicDownloadManager,
    private val settings: SettingsStore,
    profile: ProfileStore,
    connectivity: ConnectivityObserver,
    val player: MusicPlayerController,
) : ViewModel() {

    val isOnline: StateFlow<Boolean> = connectivity.isOnline

    /** Profilbild der App — derselbe Avatar wie auf der Profil-Seite. */
    val avatarPath: StateFlow<String?> = profile.avatarPath
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val instrumentalOnly: StateFlow<Boolean> = settings.instrumentalOnly
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    var searchQuery by mutableStateOf("")
    var searchResults by mutableStateOf<List<MusicSong>>(emptyList())
    var searchLoading by mutableStateOf(false)
    var searchAttempted by mutableStateOf(false)

    /** Musik, Hörbücher oder Podcasts — steuert Filter und Dauerheuristik der Suche. */
    var searchMode by mutableStateOf(MusicSearchMode.MUSIC)
        private set

    /** Erkannte Hörbücher/Podcast-Shows in den Suchergebnissen (nur außerhalb des Musik-Modus). */
    var searchGroups by mutableStateOf<List<ChapterGroup>>(emptyList())
        private set

    // --- Smart-Search (alle Modi) ---

    /** true, solange das Suchfeld aktiv ist und noch nicht abgeschickt wurde. */
    var searchActive by mutableStateOf(false)
        private set

    /** Vorschläge des Backends zur aktuellen Eingabe. */
    var suggestions by mutableStateOf<List<String>>(emptyList())
        private set

    /** Gespeicherter Suchverlauf — aktualisiert sich über den DB-Flow selbst. */
    val searchHistory: StateFlow<List<String>> = repo.observeSearchHistory()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Gewählter Ergebnisfilter der Musik-Suche. */
    var activeFilter by mutableStateOf(MusicSearchFilter.ALLE)
        private set

    /** Ergebnis der Vollsuche über Songs, Künstler, Alben und Playlists. */
    var fullResults by mutableStateOf<FullSearchResults?>(null)
        private set

    /** Lazy nachgeladene Treffer der Filter-Tabs (Filter ≠ „Alle“). */
    var typedSongs by mutableStateOf<List<MusicSong>>(emptyList())
        private set
    var typedAlbums by mutableStateOf<List<MusicAlbum>>(emptyList())
        private set
    var typedArtists by mutableStateOf<List<SearchArtist>>(emptyList())
        private set
    var typedPlaylists by mutableStateOf<List<RemotePlaylist>>(emptyList())
        private set
    var typedLoading by mutableStateOf(false)
        private set

    /** Tracks der geöffneten Remote-Playlist bzw. des Albums (Detail-Seite). */
    var remotePlaylistTracks by mutableStateOf<List<MusicSong>>(emptyList())
        private set
    var remotePlaylistLoading by mutableStateOf(false)
        private set

    /** Songs des gerade geöffneten Mixes — der Mix wird über seine Suche neu
     *  geladen, damit die Detailseite ohne Zustandsübergabe auskommt. */
    var mixSongs by mutableStateOf<List<MusicSong>>(emptyList())
    var mixLoading by mutableStateOf(false)

    /** Kapitel/Folgen der gerade geöffneten Gruppe — werden wie beim Mix
     *  über die ursprüngliche Suche neu geladen. */
    var groupSongs by mutableStateOf<List<MusicSong>>(emptyList())
        private set
    var groupLoading by mutableStateOf(false)
        private set

    var discoverSections by mutableStateOf<List<DiscoverSection>>(emptyList())
    var discoverLoading by mutableStateOf(false)
    var discoverFailed by mutableStateOf(false)

    /** Personalisierter Home-Feed des Musik-Modus (Mixe, Related, Backend-Sektionen). */
    var homeSections by mutableStateOf<List<HomeSection>>(emptyList())
        private set

    /** Zustand der Artist-Seite — kommt komplett aus einem Backend-Call. */
    var artistPage by mutableStateOf<ArtistPage?>(null)
        private set
    var artistLoading by mutableStateOf(false)
        private set
    var artistFailed by mutableStateOf(false)
        private set

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

    /**
     * Scroll-Zustände der Tabs leben hier statt in der Composition: der
     * Crossfade beim Tab-Wechsel entsorgt die Tab-Inhalte komplett, und beim
     * Zurücknavigieren soll die Liste an der alten Position stehen.
     */
    val discoverListState = LazyListState()
    val playlistsListState = LazyListState()
    val downloadsListState = LazyListState()
    val favoritesListState = LazyListState()

    var message by mutableStateOf<String?>(null)

    val downloadProgress: StateFlow<Map<String, Float>> = downloads.progress

    val downloadedIds: StateFlow<Set<String>> = downloads.downloadedIds
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val downloadedSongs: StateFlow<List<MusicSong>> = downloads.downloads
        .map { rows -> rows.map { it.toSong() } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var searchJob: Job? = null
    private var mixJob: Job? = null
    private var groupJob: Job? = null
    private var artistJob: Job? = null
    private var typedJob: Job? = null
    private var remotePlaylistJob: Job? = null

    // Merker, wofür die Detail-States bereits geladen wurden: die
    // LaunchedEffects der Detailseiten feuern beim Zurücknavigieren erneut —
    // ohne Guard würde jedes Mal neu geladen und die Scroll-Position verworfen.
    private var loadedRemotePlaylistId: String? = null
    private var loadedMixKey: String? = null
    private var loadedGroupKey: String? = null
    private var loadedArtistChannelId: String? = null

    /** Springt die Entdecken-Liste nach oben — für frische Inhalte (neue Suche, Moduswechsel). */
    private fun resetDiscoverScroll() {
        viewModelScope.launch { runCatching { discoverListState.scrollToItem(0) } }
    }

    /** Pro Filter die Query, für die seine Liste bereits geladen wurde. */
    private val typedQueries = mutableMapOf<MusicSearchFilter, String>()

    init {
        loadDiscover()
        refreshLibrary()
        // Kommt das Netz zurück, sind die Entdecken-Vorschläge nachholbar.
        viewModelScope.launch {
            isOnline.collect { online ->
                val empty = if (searchMode == MusicSearchMode.MUSIC) homeSections.isEmpty() else discoverSections.isEmpty()
                if (online && empty && !discoverLoading) loadDiscover()
            }
        }
        // Vorschläge zur Eingabe mit kurzer Verzögerung nachladen — in allen
        // Modi, solange das Suchfeld aktiv ist. Das Repo mutiert den Query
        // nicht, es komplettiert bloß.
        viewModelScope.launch {
            snapshotFlow { searchQuery }
                .debounce(250)
                .distinctUntilChanged()
                .collectLatest { q ->
                    suggestions = if (searchActive && q.trim().length >= 2) {
                        repo.getSuggestions(q)
                    } else {
                        emptyList()
                    }
                }
        }
    }

    /** Schaltet zwischen normalen und rein instrumentalen Vorschlägen um. */
    fun toggleInstrumental() {
        viewModelScope.launch {
            val next = !instrumentalOnly.value
            settings.setInstrumentalOnly(next)
            discoverSections = emptyList()
            homeSections = emptyList()
            loadDiscover(force = true)
            if (searchAttempted && searchQuery.isNotBlank()) search(searchQuery)
            message = if (next) "Nur noch Musik ohne Gesang" else "Alle Musik"
        }
    }

    fun loadDiscover(force: Boolean = false) {
        if (discoverLoading && !force) return
        viewModelScope.launch {
            discoverLoading = true
            discoverFailed = false
            if (searchMode == MusicSearchMode.MUSIC) {
                // Musik bekommt den personalisierten Feed; die kuratierten
                // Sektionen stecken dort als Fallback bereits drin.
                val sections = repo.getPersonalizedHome(force)
                homeSections = sections
                discoverFailed = sections.isEmpty()
            } else {
                val sections = repo.getDiscoverSections(searchMode)
                discoverSections = sections
                discoverFailed = sections.isEmpty()
            }
            discoverLoading = false
        }
    }

    fun search(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        searchJob?.cancel()
        resetDiscoverScroll()
        searchJob = viewModelScope.launch {
            searchLoading = true
            searchAttempted = true
            // Smart-Search in allen Modi: Verlauf mitschreiben, Volltext über
            // alle Kategorien (bei Hörbuch/Podcast: Inhalte, Kanäle, Playlists).
            resetSmartSearch()
            repo.recordSearch(q)
            val full = repo.searchFullMusic(q, searchMode)
            fullResults = full
            if (searchMode == MusicSearchMode.MUSIC) {
                // searchResults bleibt der Play-Kontext der Song-Zeilen.
                searchResults = full?.songs ?: emptyList()
                searchGroups = emptyList()
            } else {
                // Kapitel und Folgen desselben Kanals gehören in eine Gruppe —
                // einzeln gelistet wäre ein Hörbuch Dutzende lose Zeilen.
                val (groups, singles) = repo.groupIntoShows(full?.songs ?: emptyList())
                searchGroups = groups
                searchResults = singles
            }
            searchLoading = false
        }
    }

    /** Fokus aufs Suchfeld aktiviert die Smart-Search — in allen Modi. */
    fun onSearchFocus() {
        searchActive = true
    }

    /** Tippen im Suchfeld aktiviert die Smart-Search — in allen Modi. */
    fun onSearchQueryChange(query: String) {
        if (query.isNotEmpty()) searchActive = true
    }

    /** Wechselt den Ergebnisfilter und lädt dessen Treffer bei Bedarf nach. */
    fun selectFilter(filter: MusicSearchFilter) {
        if (filter == activeFilter) return
        activeFilter = filter
        if (filter == MusicSearchFilter.ALLE) return
        // Außerhalb des Musik-Modus filtern die Chips die schon geladene
        // Vollsuche clientseitig — die typed-Endpunkte sind musikspezifisch.
        if (searchMode != MusicSearchMode.MUSIC) return
        val q = searchQuery.trim()
        // Lazy laden: dieselbe Query wird pro Filter nur einmal geholt.
        if (q.isEmpty() || typedQueries[filter] == q) return
        typedQueries[filter] = q
        typedJob?.cancel()
        typedJob = viewModelScope.launch {
            typedLoading = true
            when (filter) {
                MusicSearchFilter.SONGS -> typedSongs = repo.searchTypedSongs(q)
                MusicSearchFilter.ALBEN -> typedAlbums = repo.searchTypedAlbums(q)
                MusicSearchFilter.KUENSTLER -> typedArtists = repo.searchTypedArtists(q)
                MusicSearchFilter.PLAYLISTS -> typedPlaylists = repo.searchTypedPlaylists(q)
                MusicSearchFilter.ALLE -> Unit
            }
            typedLoading = false
        }
    }

    fun removeHistoryEntry(query: String) {
        viewModelScope.launch { repo.deleteSearchHistoryEntry(query) }
    }

    fun clearHistory() {
        viewModelScope.launch { repo.clearSearchHistory() }
    }

    /** Lädt die Tracks einer Remote-Playlist oder eines Albums für die Detail-Seite. */
    fun loadRemotePlaylist(playlistId: String) {
        if (loadedRemotePlaylistId == playlistId && remotePlaylistTracks.isNotEmpty()) return
        remotePlaylistJob?.cancel()
        loadedRemotePlaylistId = playlistId
        remotePlaylistJob = viewModelScope.launch {
            remotePlaylistLoading = true
            remotePlaylistTracks = repo.getRemotePlaylistTracks(playlistId)
            remotePlaylistLoading = false
        }
    }

    /** Setzt den Smart-Search-Zustand zurück (Moduswechsel, neue Suche, Löschen). */
    private fun resetSmartSearch() {
        searchActive = false
        suggestions = emptyList()
        fullResults = null
        typedSongs = emptyList()
        typedAlbums = emptyList()
        typedArtists = emptyList()
        typedPlaylists = emptyList()
        typedLoading = false
        activeFilter = MusicSearchFilter.ALLE
        typedQueries.clear()
    }

    /**
     * Lädt die Kapitel/Folgen einer Gruppe anhand ihrer Suche erneut — wie beim
     * Mix bekommt die Detailseite ihr eigenes ViewModel und kann keinen State
     * aus der Suche mitbringen. [uploader] ist der Gruppenname aus der Suche.
     */
    fun loadGroup(query: String, uploader: String, mode: MusicSearchMode) {
        val key = "$query|$uploader|${mode.apiValue}"
        if (loadedGroupKey == key && groupSongs.isNotEmpty()) return
        groupJob?.cancel()
        loadedGroupKey = key
        groupJob = viewModelScope.launch {
            groupLoading = true
            val results = repo.searchMusic(query, mode)
            val (groups, singles) = repo.groupIntoShows(results)
            groupSongs = groups.firstOrNull { it.uploader.equals(uploader, ignoreCase = true) }?.chapters
                ?: singles.filter { it.uploader.equals(uploader, ignoreCase = true) }
            groupLoading = false
        }
    }

    /** Lädt alle noch fehlenden Kapitel/Folgen der offenen Gruppe herunter. */
    fun downloadGroup(title: String) {
        bulkDownloadJob = viewModelScope.launch {
            val missing = groupSongs.filter { it.videoId !in downloadedIds.value }
            if (missing.isEmpty()) {
                message = "Alle Kapitel sind schon heruntergeladen"
                return@launch
            }
            message = "Lade ${missing.size} Kapitel herunter…"
            var ok = 0
            missing.forEach { song -> if (downloads.download(song).isSuccess) ok++ }
            message = if (ok == missing.size) {
                "„$title“ ist offline verfügbar"
            } else {
                "$ok von ${missing.size} Kapiteln geladen"
            }
        }
    }

    /** DE/EN-Badge für den True-Crime-Modus — Heuristik aus Titel und Uploader. */
    fun languageBadge(song: MusicSong): String = repo.languageBadgeOf(song)

    /** Wechselt den Suchmodus und lädt Suche und Entdecken-Vorschläge neu. */
    fun selectSearchMode(mode: MusicSearchMode) {
        if (mode == searchMode) return
        searchMode = mode
        // Anderer Modus = andere Treffer: Smart-Search-Zustand zurücksetzen.
        resetSmartSearch()
        discoverSections = emptyList()
        homeSections = emptyList()
        resetDiscoverScroll()
        loadDiscover(force = true)
        if (searchAttempted && searchQuery.isNotBlank()) search(searchQuery)
    }

    fun loadMix(query: String, mode: MusicSearchMode = searchMode) {
        val key = "$query|${mode.apiValue}"
        if (loadedMixKey == key && mixSongs.isNotEmpty()) return
        mixJob?.cancel()
        loadedMixKey = key
        mixJob = viewModelScope.launch {
            mixLoading = true
            // Zweistufig: erst die schnellen Suchtreffer zeigen, dann die
            // Radio-Expansion nachschieben (Suche ist backend-gecacht — der
            // zweite Aufruf kostet praktisch nichts extra).
            mixSongs = repo.getMixSongs(query, mode)
            mixLoading = false
            val expanded = repo.getMixSongs(query, mode, expand = true)
            if (expanded.size > mixSongs.size) mixSongs = expanded
        }
    }

    /**
     * Lädt die komplette Artist-Seite in einem Call. [name] dient nur noch dem
     * Fallback-Pfad gegen alte Backends, die den Page-Endpunkt nicht kennen.
     */
    fun loadArtist(channelId: String, name: String) {
        if (loadedArtistChannelId == channelId && artistPage != null) return
        artistJob?.cancel()
        loadedArtistChannelId = channelId
        artistJob = viewModelScope.launch {
            artistLoading = true
            artistFailed = false
            artistPage = null
            try {
                artistPage = repo.getArtistPage(channelId, name)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                artistFailed = true
            }
            artistLoading = false
        }
    }

    /** Lädt alle noch fehlenden Songs des offenen Mixes herunter. */
    fun downloadMix(title: String) {
        bulkDownloadJob = viewModelScope.launch {
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
        searchGroups = emptyList()
        searchAttempted = false
        searchLoading = false
        resetSmartSearch()
    }

    /** History, Favoriten und Playlists werden parallel geladen. */
    fun refreshLibrary() {
        viewModelScope.launch {
            coroutineScope {
                val h = async { repo.getHistory() }
                val f = async { repo.getFavorites() }
                val p = async { repo.getPlaylists() }
                history = h.await()
                favorites = f.await()
                playlists = p.await()
            }
            favoriteIds = favorites.map { it.videoId }.toSet()
            libraryLoaded = true
        }
    }

    fun play(song: MusicSong, contextQueue: List<MusicSong>) {
        player.play(song, contextQueue)
        // Verlauf lokal nachziehen statt Komplett-Reload — das Schreiben in
        // die DB übernimmt repo.recordPlayed im Player-Controller.
        history = listOf(song) + history.filter { it.videoId != song.videoId }
    }

    fun toggleFavorite(song: MusicSong) {
        viewModelScope.launch {
            val nowFavorite = repo.toggleFavorite(song)
            favoriteIds = if (nowFavorite) favoriteIds + song.videoId else favoriteIds - song.videoId
            // Nur die Favoriten-Liste pflegen — Herz-Status in allen anderen
            // Listen läuft ohnehin über favoriteIds.
            favorites = if (nowFavorite) {
                listOf(song.copy(isFavorite = true)) + favorites.filter { it.videoId != song.videoId }
            } else {
                favorites.filter { it.videoId != song.videoId }
            }
        }
    }

    // --- Downloads ---

    /** Laufende Massen-Downloads (Playlist/Mix/Gruppe) — für den Abbruch. */
    private var bulkDownloadJob: Job? = null

    fun downloadSong(song: MusicSong) {
        viewModelScope.launch {
            downloads.download(song)
                .onSuccess { message = "„${song.title}“ ist jetzt offline verfügbar" }
                .onFailure {
                    message = if (it.message == "Abgebrochen") "Download abgebrochen"
                    else "Download fehlgeschlagen: ${it.message}"
                }
        }
    }

    /** Bricht einen einzelnen laufenden Download ab. */
    fun cancelDownload(videoId: String) {
        downloads.cancel(videoId)
    }

    /** Bricht alle laufenden Downloads inklusive Massen-Download ab. */
    fun cancelAllDownloads() {
        bulkDownloadJob?.cancel()
        bulkDownloadJob = null
        downloads.cancelAll()
        message = "Downloads abgebrochen"
    }

    fun deleteDownload(videoId: String) {
        viewModelScope.launch {
            downloads.delete(videoId)
            message = "Download entfernt"
        }
    }

    /** Lädt alle noch fehlenden Songs einer Playlist nacheinander herunter. */
    fun downloadPlaylist(entry: PlaylistWithSongs) {
        bulkDownloadJob = viewModelScope.launch {
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

    /**
     * Speichert eine Remote-Playlist (oder ein Album) als lokale Playlist —
     * existiert schon eine gleichnamige, wird sie wiederverwendet statt
     * dupliziert (der Song-Link-Insert ignoriert Duplikate ohnehin).
     * Mit [thenDownload] werden anschließend alle fehlenden Songs geladen.
     */
    fun saveRemotePlaylist(name: String, songs: List<MusicSong>, thenDownload: Boolean = false) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || songs.isEmpty()) return
        bulkDownloadJob = viewModelScope.launch {
            val existing = repo.getPlaylists()
                .firstOrNull { it.playlist.name.equals(trimmed, ignoreCase = true) }
            val id = existing?.playlist?.id ?: repo.createPlaylist(trimmed)
            songs.forEach { repo.addToPlaylist(id, it) }
            refreshLibrary()
            if (!thenDownload) {
                message = "„$trimmed“ gespeichert"
                return@launch
            }
            val missing = songs.filter { it.videoId !in downloadedIds.value }
            if (missing.isEmpty()) {
                message = "„$trimmed“ gespeichert — alles schon offline"
                return@launch
            }
            message = "„$trimmed“ gespeichert — lade ${missing.size} Songs…"
            var ok = 0
            missing.forEach { song -> if (downloads.download(song).isSuccess) ok++ }
            message = if (ok == missing.size) {
                "„$trimmed“ ist offline verfügbar"
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
