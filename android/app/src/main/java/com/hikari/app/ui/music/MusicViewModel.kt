package com.hikari.app.ui.music

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hikari.app.data.db.LocalMusicDownloadEntity
import com.hikari.app.data.net.BackendReachability
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
import com.hikari.app.domain.model.SearchSuggestion
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
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
    private val connectivity: ConnectivityObserver,
    private val backend: BackendReachability,
    val player: MusicPlayerController,
) : ViewModel() {

    /**
     * Streamen ist möglich: das Handy ist im Netz UND der Server antwortet.
     * Fehlt eines von beiden, zeigt der ganze Musik-Bereich nur noch, was auf
     * dem Gerät liegt — ein „online" ohne Server brächte lauter Titel, die
     * sich nicht abspielen lassen.
     */
    val isOnline: StateFlow<Boolean> =
        combine(connectivity.isOnline, backend.reachable) { network, server -> network && server }
            .stateIn(viewModelScope, SharingStarted.Eagerly, connectivity.currentlyOnline() && backend.reachable.value)

    /** Offline-Bereich: welcher Bereich der Downloads gezeigt wird (`null` = alles) und die Suche darin. */
    var offlineMode by mutableStateOf<MusicSearchMode?>(null)
    var offlineQuery by mutableStateOf("")

    /** Die im Offline-Bereich geöffnete Sammlung (Playlist, Favoriten, Interpret) — überlebt die Navigation. */
    var offlineCollectionKey by mutableStateOf<String?>(null)

    private var serverCheck: Job? = null

    /**
     * Fragt nach, ob der Server (wieder) antwortet. [announce] meldet einen
     * Fehlschlag als Hinweis — für den Tipp auf „Offline", nicht für die
     * stillen Nachfragen im Hintergrund.
     */
    fun checkServer(announce: Boolean = false) {
        if (serverCheck?.isActive == true) return
        serverCheck = viewModelScope.launch {
            val ok = backend.check()
            if (!ok && announce) message = "Noch keine Verbindung zum Server"
        }
    }

    /**
     * Spielt aus dem Offline-Bereich. Der Song behält dabei seinen eigenen
     * Bereich (Musik, Hörbuch …) — die Offline-Ansicht mischt alle, der gerade
     * eingestellte Modus der Online-Seite wäre für viele Titel der falsche.
     */
    fun playOffline(song: MusicSong, queue: List<MusicSong>) {
        player.queueMode = song.listenMode ?: MusicSearchMode.MUSIC.apiValue
        player.play(song, queue.ifEmpty { listOf(song) })
        history = listOf(song) + history.filter { it.videoId != song.videoId }
    }

    /** Spielt eine ganze Offline-Sammlung — der Reihe nach oder gemischt. */
    fun playAllOffline(songs: List<MusicSong>, shuffled: Boolean = false) {
        if (songs.isEmpty()) return
        val order = if (shuffled) songs.shuffled() else songs
        playOffline(order.first(), order)
    }

    /** Profilbild der App — derselbe Avatar wie auf der Profil-Seite. */
    val avatarPath: StateFlow<String?> = profile.avatarPath
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    var searchQuery by mutableStateOf("")
    var searchResults by mutableStateOf<List<MusicSong>>(emptyList())
    var searchLoading by mutableStateOf(false)
    var searchAttempted by mutableStateOf(false)

    /**
     * Musik, Instrumental, Hörbücher, Podcasts oder True Crime — steuert Feed,
     * Suche, Verlauf und Autoplay. Gespeichert in den Settings und damit für
     * alle Musik-Screens (und den Player-Nachschub) derselbe Modus.
     */
    var searchMode by mutableStateOf(MusicSearchMode.MUSIC)
        private set

    /** Erst wenn der gespeicherte Modus gelesen ist, darf der Feed laden. */
    private var modeReady = false

    /** Erkannte Hörbücher/Podcast-Shows in den Suchergebnissen (nur außerhalb des Musik-Modus). */
    var searchGroups by mutableStateOf<List<ChapterGroup>>(emptyList())
        private set

    // --- Smart-Search (alle Modi) ---

    /** true, solange das Suchfeld aktiv ist und noch nicht abgeschickt wurde. */
    var searchActive by mutableStateOf(false)
        private set

    /** Vorschläge des Backends zur aktuellen Eingabe — Queries und Entities. */
    var suggestions by mutableStateOf<List<SearchSuggestion>>(emptyList())
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

    /** "Zuletzt gehört" des aktiven Modus — leer heißt: Leiste ausblenden. */
    val modeHistory: List<MusicSong>
        get() = history.filter { it.isFromMode(searchMode) }
    var favorites by mutableStateOf<List<MusicSong>>(emptyList())
    var playlists by mutableStateOf<List<PlaylistWithSongs>>(emptyList())

    /** Meistgehörte Songs der letzten 7 Tage — Schnellauswahl im Entdecken. */
    var topWeekSongs by mutableStateOf<List<MusicSong>>(emptyList())
        private set

    /** Schnellauswahl des aktiven Modus. */
    val modeTopWeek: List<MusicSong>
        get() = topWeekSongs.filter { it.isFromMode(searchMode) }

    private fun MusicSong.isFromMode(mode: MusicSearchMode) =
        (listenMode ?: MusicSearchMode.MUSIC.apiValue) == mode.apiValue

    /** Erst nach dem ersten Laden darf die UI aus "nicht gefunden" Schlüsse ziehen. */
    var libraryLoaded by mutableStateOf(false)
        private set

    /** Single source of truth for hearts across all lists. */
    var favoriteIds by mutableStateOf<Set<String>>(emptySet())

    /** Song, für den gerade das "Zu Playlist hinzufügen"-Sheet offen ist. */
    var addToPlaylistTarget by mutableStateOf<MusicSong?>(null)

    /** Song, dessen Offline-Datei nach Rückfrage vom Handy gelöscht werden soll. */
    var deleteDownloadTarget by mutableStateOf<MusicSong?>(null)

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
        viewModelScope.launch {
            searchMode = MusicSearchMode.fromApiValue(runCatching { settings.musicMode.first() }.getOrNull())
            modeReady = true
            loadDiscover()
        }
        refreshLibrary()
        // Der Server wartet bei einer YouTube-Drossel, statt aufzugeben —
        // das einmal sagen, sonst sieht der stehende Balken wie ein Hänger aus.
        viewModelScope.launch {
            downloads.throttleNotice.filterNotNull().distinctUntilChanged().collect { message = it }
        }
        // Sobald das Handy wieder im Netz ist, gleich nachfragen, ob auch der
        // Server antwortet — sonst bliebe die Seite bis zur nächsten stillen
        // Nachfrage im Offline-Bereich stehen.
        viewModelScope.launch {
            connectivity.isOnline.drop(1).filter { it }.collect { checkServer() }
        }
        // Kommt das Netz zurück, sind die Entdecken-Vorschläge nachholbar.
        viewModelScope.launch {
            isOnline.collect { online ->
                if (online && feedEmpty() && !discoverLoading) loadDiscover()
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

    /** Pull-to-Refresh auf der Entdecken-Seite läuft. */
    var discoverRefreshing by mutableStateOf(false)
        private set

    private var discoverJob: Job? = null

    /** Wofür und wann der Feed zuletzt gebaut wurde — Rückkehr baut nicht neu. */
    private var discoverBuiltFor: MusicSearchMode? = null
    private var discoverBuiltAt = 0L

    /** Nachschub beim Runterscrollen läuft bzw. der Pool des Modus ist erschöpft. */
    var moreLoading by mutableStateOf(false)
        private set
    var feedExhausted by mutableStateOf(false)
        private set
    private var moreJob: Job? = null

    /**
     * Endloser Feed: kurz vor dem Listenende weitere Mixe anhängen, bis der
     * Pool des Modus aufgebraucht ist.
     */
    fun loadMoreFeed() {
        if (moreLoading || feedExhausted || discoverLoading || discoverRefreshing || feedEmpty()) return
        val mode = searchMode
        moreJob = viewModelScope.launch {
            moreLoading = true
            try {
                val added = if (mode == MusicSearchMode.MUSIC || mode == MusicSearchMode.INSTRUMENTAL) {
                    val shown = homeSections.mapTo(HashSet()) { it.query }
                    repo.getMoreHomeSections(mode, shown).also { if (mode == searchMode) homeSections = homeSections + it }
                } else {
                    val shown = discoverSections.mapTo(HashSet()) { it.query }
                    repo.getMoreDiscoverSections(mode, shown)
                        .also { if (mode == searchMode) discoverSections = discoverSections + it }
                }
                if (added.isEmpty()) feedExhausted = true
            } finally {
                moreLoading = false
            }
        }
    }

    /** Legt die sichtbaren Songs einer Feed-Sektion für ihre "Alle"-Seite bereit. */
    fun stageCollection(key: String, songs: List<MusicSong>) = repo.stageCollection(key, songs)

    /** Feed des aktiven Modus ist leer. */
    private fun feedEmpty(): Boolean =
        if (searchMode == MusicSearchMode.MUSIC || searchMode == MusicSearchMode.INSTRUMENTAL) {
            homeSections.isEmpty()
        } else {
            discoverSections.isEmpty()
        }

    /** Baut den Feed des aktiven Modus. [force] umgeht Caches und zieht neue Vorschläge. */
    private suspend fun buildFeed(force: Boolean) {
        val mode = searchMode
        moreJob?.cancel()
        moreLoading = false
        feedExhausted = false
        when (mode) {
            MusicSearchMode.MUSIC -> homeSections = repo.getPersonalizedHome(force)
            MusicSearchMode.INSTRUMENTAL -> homeSections = repo.getInstrumentalHome(force)
            else -> discoverSections = repo.getDiscoverSections(mode)
        }
        discoverFailed = feedEmpty()
        // Ein leerer Feed heißt fast immer: der Server antwortet nicht. Nachfragen —
        // dann wechselt die Seite von selbst in den Offline-Bereich.
        if (discoverFailed) checkServer()
        if (!discoverFailed) {
            discoverBuiltFor = mode
            discoverBuiltAt = System.currentTimeMillis()
        }
    }

    /**
     * Pull-to-Refresh: neuer Aufbau mit frischer Auswahl — andere Mixe,
     * Playlists und Radio-Songs — und Bibliothek + Schnellauswahl gleich mit.
     */
    fun refreshDiscover() {
        if (discoverRefreshing || !modeReady) return
        discoverJob?.cancel()
        discoverJob = viewModelScope.launch {
            discoverRefreshing = true
            try {
                refreshLibrary()
                buildFeed(force = true)
            } finally {
                discoverRefreshing = false
            }
        }
    }

    fun loadDiscover(force: Boolean = false) {
        if (!modeReady) return
        if (discoverLoading && !force) return
        // Zurück aus einer Detailseite: der Feed steht noch — nicht neu
        // würfeln, sonst springt die Seite unter dem Daumen weg.
        val fresh = discoverBuiltFor == searchMode && !feedEmpty() &&
            System.currentTimeMillis() - discoverBuiltAt < FEED_TTL_MS
        if (!force && fresh) return
        discoverJob?.cancel()
        discoverJob = viewModelScope.launch {
            discoverLoading = true
            discoverFailed = false
            try {
                buildFeed(force)
            } finally {
                discoverLoading = false
            }
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
            if (searchMode.isMusical) {
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
            prewarmSongs(searchResults)
        }
    }

    fun prewarm(videoId: String) {
        viewModelScope.launch {
            repo.prewarmAudio(videoId)
        }
    }

    fun prewarmSongs(songs: List<MusicSong>, count: Int = 2) {
        viewModelScope.launch {
            songs.take(count).forEach { song ->
                if (song.videoId !in downloadedIds.value) {
                    repo.prewarmAudio(song.videoId)
                }
            }
        }
    }

    suspend fun searchForPicker(query: String): List<MusicSong> {
        val q = query.trim()
        if (q.isBlank()) return emptyList()
        return try {
            repo.searchFullMusic(q, MusicSearchMode.MUSIC)?.songs ?: emptyList()
        } catch (_: Exception) {
            emptyList()
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

    /**
     * Schreibt eine Suche in den Verlauf, ohne sie auszuführen — für
     * Entity-Vorschläge, die direkt abspielen oder navigieren.
     */
    fun noteSearch(text: String) {
        val q = text.trim()
        if (q.isEmpty()) return
        viewModelScope.launch { repo.recordSearch(q) }
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
            prewarmSongs(remotePlaylistTracks)
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
            prewarmSongs(groupSongs)
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
        viewModelScope.launch { settings.setMusicMode(mode.apiValue) }
        moreJob?.cancel()
        moreLoading = false
        feedExhausted = false
        // Anderer Modus = andere Treffer: Smart-Search-Zustand zurücksetzen.
        resetSmartSearch()
        discoverSections = emptyList()
        homeSections = emptyList()
        resetDiscoverScroll()
        // Ohne force: der Repo-Cache macht schnelles Hin- und Herwechseln billig.
        loadDiscover()
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
            prewarmSongs(mixSongs)
            val expanded = repo.getMixSongs(query, mode, expand = true)
            if (expanded.size > mixSongs.size) {
                mixSongs = expanded
                prewarmSongs(mixSongs)
            }
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
            artistPage?.topSongs?.let { prewarmSongs(it) }
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
                val t = async { repo.getTopPlayedOfWeek() }
                history = h.await()
                favorites = f.await()
                playlists = p.await()
                topWeekSongs = t.await()
            }
            favoriteIds = favorites.map { it.videoId }.toSet()
            libraryLoaded = true
        }
    }

    fun play(song: MusicSong, contextQueue: List<MusicSong>) {
        player.queueMode = searchMode.apiValue
        player.play(song, contextQueue)
        // Verlauf lokal nachziehen statt Komplett-Reload — das Schreiben in
        // die DB übernimmt repo.recordPlayed im Player-Controller.
        history = listOf(song.copy(listenMode = searchMode.apiValue)) +
            history.filter { it.videoId != song.videoId }
        // Schnellauswahl aktuell halten: das Play-Event ist gerade geschrieben
        // worden — ohne Nachladen erschiene die Karte erst beim App-Neustart.
        viewModelScope.launch {
            runCatching { topWeekSongs = repo.getTopPlayedOfWeek() }
        }
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

    /** Öffnet die Rückfrage — gelöscht wird erst über [deleteDownload]. */
    fun requestDeleteDownload(song: MusicSong) {
        deleteDownloadTarget = song
    }

    fun deleteDownload(videoId: String) {
        viewModelScope.launch {
            downloads.delete(videoId)
            message = "Vom Handy gelöscht"
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
            addToPlaylistTarget = null
            message = "Playlist „$trimmed“ erstellt"
        }
    }

    /** Persistiert die im Bearbeiten-Modus gezogene Reihenfolge. */
    fun reorderPlaylist(playlistId: Int, orderedVideoIds: List<String>) {
        viewModelScope.launch {
            repo.reorderPlaylist(playlistId, orderedVideoIds)
            refreshLibrary()
            message = "Reihenfolge gespeichert"
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
            val added = repo.addToPlaylist(playlistId, song)
            refreshLibrary()
            message = if (added) "Zur Playlist hinzugefügt" else "Bereits in der Playlist"
        }
    }

    fun removeFromPlaylist(playlistId: Int, song: MusicSong) {
        viewModelScope.launch {
            repo.removeFromPlaylist(playlistId, song)
            refreshLibrary()
        }
    }

    companion object {
        /** So lange bleibt ein gebauter Feed beim Zurückkehren stehen. */
        private const val FEED_TTL_MS = 15 * 60 * 1000L
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
