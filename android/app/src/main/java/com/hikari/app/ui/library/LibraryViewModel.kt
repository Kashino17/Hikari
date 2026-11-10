package com.hikari.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hikari.app.data.api.dto.LibraryResponse
import com.hikari.app.data.api.dto.SeriesDetailResponse
import com.hikari.app.data.api.dto.SeriesItemDto
import com.hikari.app.data.db.LocalDownloadDao
import com.hikari.app.data.db.LocalMangaDao
import com.hikari.app.data.net.ConnectivityObserver
import com.hikari.app.domain.model.FeedItem
import com.hikari.app.domain.model.MusicSong
import com.hikari.app.domain.repo.FeedRepository
import com.hikari.app.domain.repo.MusicRepository
import com.hikari.app.domain.repo.PlaybackRepository
import com.hikari.app.player.MusicPlayerController
import com.hikari.app.data.api.dto.LibraryVideoDto
import com.hikari.app.data.api.dto.isFilm
import com.hikari.app.ui.library.components.TopTenCharts
import com.hikari.app.ui.library.components.TopTenItem
import com.hikari.app.data.db.LocalDownloadKind
import com.hikari.app.domain.download.LocalDownloadManager
import com.hikari.app.domain.download.LocalDownloadMetadata
import com.hikari.app.domain.download.OfflineArtworkStore
import com.hikari.app.domain.download.OfflineArtworkSync
import com.hikari.app.domain.library.OfflineLibrary
import com.hikari.app.domain.library.OfflineLibraryBuilder
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import retrofit2.HttpException

/**
 * Zustand der Bibliothek.
 *
 * Bewusst als *ein* sealed interface modelliert statt `offline: Boolean` +
 * separate StateFlows: Online-Daten und Offline-Fallback schließen sich
 * gegenseitig aus. Ein einziger State macht es unmöglich, aus Versehen beides
 * gleichzeitig (oder gar nichts) zu rendern — der `when`-Block im Screen ist
 * erschöpfend und der Compiler erzwingt die Behandlung.
 *
 * `Error` gibt es absichtlich nicht mehr: jeder fehlgeschlagene Call landet in
 * [Offline]. Rohe Exception-Texte ("Unable to resolve host …") sind für Nutzer
 * wertlos, und heruntergeladene Inhalte sind auch ohne Server abspielbar.
 */
sealed interface LibraryUiState {
    object Loading : LibraryUiState
    data class Success(val data: LibraryResponse) : LibraryUiState

    /** Lokal vorhandene Inhalte — kein Netz oder Backend nicht erreichbar. */
    data class Offline(val library: OfflineLibrary) : LibraryUiState
}

sealed interface SeriesUiState {
    object Loading : SeriesUiState
    data class Success(val data: SeriesDetailResponse) : SeriesUiState
    data class Error(val message: String) : SeriesUiState
}

sealed interface CoverEditState {
    object Idle : CoverEditState
    object Saving : CoverEditState
    data class Error(val message: String) : CoverEditState
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repo: FeedRepository,
    private val connectivity: ConnectivityObserver,
    private val downloadDao: LocalDownloadDao,
    private val mangaDao: LocalMangaDao,
    private val musicRepo: MusicRepository,
    private val musicPlayer: MusicPlayerController,
    private val playbackRepo: PlaybackRepository,
    private val localDownloads: LocalDownloadManager,
    private val artworkStore: OfflineArtworkStore,
    private val artworkSync: OfflineArtworkSync,
) : ViewModel() {

    private val _uiState = MutableStateFlow<LibraryUiState>(LibraryUiState.Loading)
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private val _topCharts = MutableStateFlow(TopTenCharts())
    val topCharts: StateFlow<TopTenCharts> = _topCharts.asStateFlow()

    private val _seriesState = MutableStateFlow<SeriesUiState>(SeriesUiState.Loading)
    val seriesState: StateFlow<SeriesUiState> = _seriesState.asStateFlow()

    private val _coverEditState = MutableStateFlow<CoverEditState>(CoverEditState.Idle)
    val coverEditState: StateFlow<CoverEditState> = _coverEditState.asStateFlow()

    /** Alle Serien — Auswahlliste für den Merge-Dialog im Serien-Detail. */
    private val _allSeries = MutableStateFlow<List<SeriesItemDto>>(emptyList())
    val allSeries: StateFlow<List<SeriesItemDto>> = _allSeries.asStateFlow()

    /** videoIds, die tatsächlich auf dem Gerät liegen — für das Download-Icon. */
    val localIds: StateFlow<Set<String>> = localDownloads.downloadedIds
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** Laufender Download pro videoId (0f..1f), absent = kein Download. */
    val downloadProgress: StateFlow<Map<String, Float>> = localDownloads.progress

    private val _seriesBulkDownloading = MutableStateFlow(false)
    val seriesBulkDownloading: StateFlow<Boolean> = _seriesBulkDownloading.asStateFlow()

    private val _downloadActionError = MutableStateFlow<String?>(null)
    val downloadActionError: StateFlow<String?> = _downloadActionError.asStateFlow()

    init {
        loadLibrary()
        observeConnectivity()
    }

    /**
     * Sobald das Gerät wieder online geht, holen wir die Server-Bibliothek
     * automatisch nach — der Nutzer soll nicht selbst neu laden müssen.
     * `drop(1)` überspringt den Startwert (init lädt bereits), `filter { it }`
     * lässt nur den Wechsel offline → online durch. `isOnline` ist bereits
     * `distinctUntilChanged`, es feuert also wirklich nur bei echten Wechseln.
     */
    private fun observeConnectivity() {
        viewModelScope.launch {
            connectivity.isOnline
                .drop(1)
                .filter { it }
                .collect { loadLibrary() }
        }
    }

    fun loadLibrary() {
        viewModelScope.launch {
            // Ohne Netz gar nicht erst einen Call absetzen: spart den Timeout
            // und zeigt die lokalen Inhalte sofort (kein Loading-Flackern).
            if (!connectivity.currentlyOnline()) {
                showOffline()
                return@launch
            }
            // Offline entstandene Positionen zuerst nachziehen, damit die
            // Bibliothek direkt denselben Fortschritt zeigt wie der Player.
            // Der WorkManager-Lauf ist die Rückfallebene, falls das hier
            // timeoutet oder das Backend kurz nicht antwortet.
            runCatching { withTimeoutOrNull(4_000) { playbackRepo.syncPending() } }
            // Beim Auffrischen (z. B. Rückkehr aus dem Player) bleibt der bisherige
            // Inhalt stehen — sonst blitzt jedes Mal der Ladekreis auf und die
            // Seite springt an den Anfang. Das gilt auch für die Offline-Ansicht:
            // sie bleibt, bis feststeht, ob der Server wieder antwortet.
            runCatching {
                repo.getLibrary()
            }.onSuccess {
                artworkStore.preferLocal = false
                _uiState.value = LibraryUiState.Success(it)
                updateTopCharts(it)
                // Plakate der Downloads gleich mit abgleichen und aufs Gerät sichern.
                artworkSync.onLibraryLoaded(it)
            }.onFailure {
                // Netz da, aber Backend antwortet nicht → trotzdem Offline-Modus
                // statt technischem Fehlertext.
                showOffline()
            }
        }
    }

    /**
     * Sammelt alles, was lokal auf dem Gerät liegt: Videos, Manga-Arcs, Musik.
     * Jede Quelle wird einzeln abgesichert — fällt eine aus, bleiben die
     * anderen sichtbar.
     */
    private suspend fun showOffline() {
        // Ab jetzt zeigt jede Ansicht sofort die aufs Gerät gesicherten Bilder.
        artworkStore.preferLocal = true
        _uiState.value = LibraryUiState.Offline(buildOfflineLibrary())
    }

    private suspend fun buildOfflineLibrary(): OfflineLibrary = OfflineLibraryBuilder.build(
        downloads = runCatching { downloadDao.all() }.getOrDefault(emptyList()),
        positions = runCatching { playbackRepo.allPositions() }.getOrDefault(emptyList()),
        mangaArcs = runCatching { mangaDao.observeArcs().first() }.getOrDefault(emptyList()),
        downloadedSongs = runCatching { musicRepo.getDownloadedSongs() }.getOrDefault(emptyList()),
        favorites = runCatching { musicRepo.getFavorites() }.getOrDefault(emptyList()),
        playlists = runCatching { musicRepo.getPlaylists() }.getOrDefault(emptyList()),
    )

    /**
     * Spielt [song] aus einer Offline-Liste. Die abspielbaren Songs der Liste
     * gehen als Warteschlange mit, damit „Weiter" auch ohne Netz funktioniert —
     * der [MusicPlayerController] greift automatisch auf die lokale Datei zu.
     */
    fun playSong(song: MusicSong, queue: List<MusicSong>) {
        musicPlayer.play(song, queue.ifEmpty { listOf(song) })
    }

    /** Spielt eine ganze Offline-Liste — der Reihe nach oder gemischt. */
    fun playAll(queue: List<MusicSong>, shuffled: Boolean = false) {
        if (queue.isEmpty()) return
        val order = if (shuffled) queue.shuffled() else queue
        musicPlayer.play(order.first(), order)
    }

    /**
     * Berechnet die TOP 10 Serien und TOP 10 Filme der letzten 30 Tage
     * basierend auf realen Wiedergabepositionen und Historie.
     * Füllt bei weniger als 10 Einträgen automatisch mit den höchstbewerteten Titeln auf.
     */
    private fun updateTopCharts(data: LibraryResponse) {
        viewModelScope.launch {
            val thirtyDaysAgo = System.currentTimeMillis() - 30L * 24 * 3600 * 1000L
            val recentPositions = runCatching { playbackRepo.getRecentPositions(thirtyDaysAgo) }.getOrDefault(emptyList())
            val recentWatchedVideoIds = recentPositions.map { it.videoId }.toSet()

            // ── TOP 10 SERIEN (Letzte 30 Tage) ──────────────────────────────
            val watchedSeriesCount = mutableMapOf<String, Int>()
            data.recentlyAdded.forEach { v ->
                val sId = v.series_id
                if (sId != null && v.id in recentWatchedVideoIds) {
                    watchedSeriesCount[sId] = (watchedSeriesCount[sId] ?: 0) + 1
                }
            }

            val seriesById = data.series.associateBy { it.id }
            val rankedWatchedSeries = watchedSeriesCount.entries
                .sortedByDescending { it.value }
                .mapNotNull { seriesById[it.key] }

            val remainingSeries = data.series
                .filter { it !in rankedWatchedSeries }
                .sortedByDescending { it.added_at }

            val finalTopSeries = (rankedWatchedSeries + remainingSeries)
                .take(10)
                .mapIndexed { idx, s ->
                    TopTenItem(
                        rank = idx + 1,
                        id = s.id,
                        title = s.title,
                        subtitle = s.description ?: "Serie",
                        thumbnailUrl = s.thumbnail_url,
                        posterUrl = s.poster_url,
                        isSeries = true,
                        seriesId = s.id,
                    )
                }

            // ── TOP 10 FILME (Letzte 30 Tage) ───────────────────────────────
            val allMovies = data.recentlyAdded.filter { it.isFilm() }
            val watchedMovies = allMovies
                .filter { it.id in recentWatchedVideoIds }
                .sortedByDescending { it.progress_seconds ?: 0f }

            val unwatchedMovies = allMovies
                .filter { it !in watchedMovies }
                .sortedWith(compareByDescending<LibraryVideoDto> { it.overall_score ?: 0 }.thenByDescending { it.duration_seconds })

            val finalTopMovies = (watchedMovies + unwatchedMovies)
                .take(10)
                .mapIndexed { idx, v ->
                    val min = v.duration_seconds / 60
                    val durStr = if (min >= 60) "${min / 60}h ${min % 60}m" else "${min}m"
                    TopTenItem(
                        rank = idx + 1,
                        id = v.id,
                        title = v.title,
                        subtitle = "$durStr · ${v.channelTitle ?: "Film"}",
                        thumbnailUrl = v.thumbnail_url,
                        posterUrl = v.poster_url,
                        isSeries = false,
                        channelTitle = v.channelTitle ?: "",
                        durationSeconds = v.duration_seconds,
                        matchScore = v.overall_score,
                        isMovie = true,
                        progressSeconds = v.progress_seconds,
                    )
                }

            _topCharts.value = TopTenCharts(
                topSeries = finalTopSeries,
                topMovies = finalTopMovies,
            )
        }
    }

    /**
     * Verlauf kommt mit der Bibliothek (LibraryResponse.watchHistory). Sofort
     * lokal entfernen, damit die Karte ohne Neuladen verschwindet; scheitert
     * der Server-Call, zeigt das nächste Laden den echten Stand.
     */
    fun removeFromHistory(videoId: String) {
        updateLibrary { it.copy(watchHistory = it.watchHistory.filterNot { v -> v.id == videoId }) }
        viewModelScope.launch { runCatching { repo.removeFromHistory(videoId) } }
    }

    fun clearHistory() {
        updateLibrary { it.copy(watchHistory = emptyList()) }
        viewModelScope.launch { runCatching { repo.clearHistory() } }
    }

    private fun updateLibrary(transform: (LibraryResponse) -> LibraryResponse) {
        val current = _uiState.value as? LibraryUiState.Success ?: return
        _uiState.value = LibraryUiState.Success(transform(current.data))
    }

    fun loadSeries(id: String) {
        viewModelScope.launch {
            // Dieselbe Serie wird nur aufgefrischt (Rückkehr aus dem Player):
            // den bisherigen Stand stehen lassen, statt kurz den Ladekreis zu zeigen.
            val shown = (_seriesState.value as? SeriesUiState.Success)?.data?.id
            if (shown != id) _seriesState.value = SeriesUiState.Loading
            if (connectivity.currentlyOnline()) {
                // Gleiches Prinzip wie in der Bibliothek: erst lokalen
                // Fortschritt sichern, dann die Server-Folgenliste holen.
                runCatching { withTimeoutOrNull(4_000) { playbackRepo.syncPending() } }
            }
            runCatching {
                repo.getSeries(id)
            }.onSuccess {
                _seriesState.value = SeriesUiState.Success(it)
            }.onFailure {
                // Server nicht erreichbar: dieselbe Seite, gefüllt mit den Folgen
                // auf dem Gerät. Nur wenn auch davon keine da ist, bleibt ein Hinweis.
                val local = buildOfflineLibrary().seriesDetail(id)
                _seriesState.value = if (local != null) {
                    SeriesUiState.Success(local)
                } else {
                    SeriesUiState.Error("Offline — von dieser Serie liegt keine Folge auf dem Gerät.")
                }
            }
        }
    }

    fun loadAllSeries() {
        viewModelScope.launch {
            runCatching { repo.listSeries() }
                .onSuccess { _allSeries.value = it }
        }
    }

    /**
     * Lädt eine einzelne Folge der gerade offenen Serie auf das Gerät.
     * Die Metadaten der Serie wandern mit, damit der Offline-Fallback in
     * Downloads die Folge später korrekt gruppieren kann.
     */
    fun downloadEpisode(series: SeriesDetailResponse, video: LibraryVideoDto) {
        viewModelScope.launch {
            _downloadActionError.value = null
            localDownloads.download(video.toDownloadMetadata(series))
                .onFailure {
                    _downloadActionError.value =
                        "Download fehlgeschlagen — ist das Gerät online?"
                }
        }
    }

    /** Lädt alle noch fehlenden Folgen nacheinander — für „Staffel laden“. */
    fun downloadEpisodes(series: SeriesDetailResponse, episodes: List<LibraryVideoDto>) {
        if (_seriesBulkDownloading.value) return
        viewModelScope.launch {
            _seriesBulkDownloading.value = true
            _downloadActionError.value = null
            val metas = episodes.map { it.toDownloadMetadata(series) }
            if (localDownloads.downloadAll(metas).isFailure) {
                _downloadActionError.value =
                    "Download fehlgeschlagen — ist das Gerät online?"
            }
            _seriesBulkDownloading.value = false
        }
    }

    fun removeLocalDownload(videoId: String) {
        viewModelScope.launch { localDownloads.delete(videoId) }
    }

    fun clearDownloadActionError() {
        _downloadActionError.value = null
    }

    private fun LibraryVideoDto.toDownloadMetadata(series: SeriesDetailResponse) =
        LocalDownloadMetadata(
            videoId = id,
            kind = LocalDownloadKind.SERIES,
            title = title,
            durationSeconds = duration_seconds,
            thumbnailUrl = thumbnail_url,
            seriesId = series.id,
            seriesTitle = series.title,
            seriesThumbnailUrl = series.thumbnail_url,
            season = season,
            episode = episode,
            seriesPosterUrl = series.poster_url,
            seriesBackdropUrl = series.backdrop_url,
        )

    /**
     * Führt die aktuelle Serie (source) in eine andere (target) zusammen.
     * Bei Erfolg meldet der Screen sich über [onSuccess] zurück und navigiert
     * weg — die Quell-Serie existiert danach nicht mehr.
     */
    fun mergeSeries(
        sourceId: String,
        targetId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            runCatching { repo.mergeSeries(sourceId, targetId) }
                .onSuccess {
                    loadLibrary()
                    onSuccess()
                }
                .onFailure { e ->
                    val msg = when {
                        e is HttpException && e.code() == 404 -> "Serie nicht gefunden"
                        e is HttpException && e.code() == 400 ->
                            "Quelle und Ziel dürfen nicht identisch sein"
                        else -> e.message ?: "Zusammenführen fehlgeschlagen"
                    }
                    onError(msg)
                }
        }
    }

    fun setSeriesCoverUrl(seriesId: String, url: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            _coverEditState.value = CoverEditState.Saving
            runCatching {
                repo.updateSeries(seriesId, thumbnailUrl = url.ifBlank { null }, description = null)
            }.onSuccess {
                _coverEditState.value = CoverEditState.Idle
                loadLibrary()
                onDone()
            }.onFailure {
                _coverEditState.value = CoverEditState.Error(it.message ?: "Speichern fehlgeschlagen")
            }
        }
    }

    fun uploadSeriesCover(seriesId: String, bytes: ByteArray, mime: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            _coverEditState.value = CoverEditState.Saving
            runCatching {
                repo.uploadSeriesCover(seriesId, bytes, mime)
            }.onSuccess {
                _coverEditState.value = CoverEditState.Idle
                loadLibrary()
                onDone()
            }.onFailure {
                _coverEditState.value = CoverEditState.Error(it.message ?: "Upload fehlgeschlagen")
            }
        }
    }

    fun resetCoverEdit() {
        _coverEditState.value = CoverEditState.Idle
    }
}
