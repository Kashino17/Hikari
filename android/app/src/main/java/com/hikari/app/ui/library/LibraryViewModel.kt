package com.hikari.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hikari.app.data.api.dto.LibraryResponse
import com.hikari.app.data.api.dto.SeriesDetailResponse
import com.hikari.app.data.api.dto.TodayCountResponse
import com.hikari.app.data.db.LocalDownloadDao
import com.hikari.app.data.db.LocalDownloadEntity
import com.hikari.app.data.db.LocalMangaArcEntity
import com.hikari.app.data.db.LocalMangaDao
import com.hikari.app.data.net.ConnectivityObserver
import com.hikari.app.domain.model.FeedItem
import com.hikari.app.domain.model.MusicSong
import com.hikari.app.domain.repo.FeedRepository
import com.hikari.app.domain.repo.MusicRepository
import com.hikari.app.player.MusicPlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
    data class Offline(
        val videos: List<LocalDownloadEntity>,
        val mangaArcs: List<LocalMangaArcEntity>,
        val songs: List<MusicSong>,
    ) : LibraryUiState {
        val isEmpty: Boolean
            get() = videos.isEmpty() && mangaArcs.isEmpty() && songs.isEmpty()
    }
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
) : ViewModel() {

    private val _uiState = MutableStateFlow<LibraryUiState>(LibraryUiState.Loading)
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private val _seriesState = MutableStateFlow<SeriesUiState>(SeriesUiState.Loading)
    val seriesState: StateFlow<SeriesUiState> = _seriesState.asStateFlow()

    private val _coverEditState = MutableStateFlow<CoverEditState>(CoverEditState.Idle)
    val coverEditState: StateFlow<CoverEditState> = _coverEditState.asStateFlow()

    private val _savedItems = MutableStateFlow<List<FeedItem>>(emptyList())
    val savedItems: StateFlow<List<FeedItem>> = _savedItems.asStateFlow()

    private val _today = MutableStateFlow<TodayCountResponse?>(null)
    val today: StateFlow<TodayCountResponse?> = _today.asStateFlow()

    private val _queueItems = MutableStateFlow<List<FeedItem>>(emptyList())
    val queueItems: StateFlow<List<FeedItem>> = _queueItems.asStateFlow()

    // Etappe 5: die Sammlung — Später ansehen + Verlauf.
    private val _watchLater = MutableStateFlow<List<FeedItem>>(emptyList())
    val watchLater: StateFlow<List<FeedItem>> = _watchLater.asStateFlow()

    private val _history = MutableStateFlow<List<FeedItem>>(emptyList())
    val history: StateFlow<List<FeedItem>> = _history.asStateFlow()

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
            _uiState.value = LibraryUiState.Loading
            runCatching {
                repo.getLibrary()
            }.onSuccess {
                _uiState.value = LibraryUiState.Success(it)
                loadBriefingExtras()
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
        val videos = runCatching { downloadDao.observeAll().first() }.getOrDefault(emptyList())
        val arcs = runCatching { mangaDao.observeArcs().first() }.getOrDefault(emptyList())
        val songs = runCatching { musicRepo.getDownloadedSongs() }.getOrDefault(emptyList())
        _uiState.value = LibraryUiState.Offline(
            videos = videos,
            mangaArcs = arcs,
            songs = songs,
        )
    }

    /**
     * Spielt einen heruntergeladenen Song ab. Die komplette Offline-Liste geht
     * als Queue mit, damit "Weiter" auch ohne Netz funktioniert — der
     * [MusicPlayerController] greift automatisch auf die lokale Datei zu.
     */
    fun playSong(song: MusicSong) {
        val queue = (_uiState.value as? LibraryUiState.Offline)?.songs ?: listOf(song)
        musicPlayer.play(song, queue)
    }

    private fun loadBriefingExtras() {
        viewModelScope.launch {
            runCatching { repo.fetchSaved() }
                .onSuccess { _savedItems.value = it.distinctBy { item -> item.videoId } }
        }
        viewModelScope.launch {
            runCatching { repo.todayCount() }
                .onSuccess { _today.value = it }
        }
        viewModelScope.launch {
            runCatching { repo.fetchQueue() }
                .onSuccess { _queueItems.value = it.distinctBy { item -> item.videoId } }
        }
        viewModelScope.launch {
            runCatching { repo.fetchWatchLater() }
                .onSuccess { _watchLater.value = it.distinctBy { item -> item.videoId } }
        }
        viewModelScope.launch {
            runCatching { repo.fetchOld() }
                .onSuccess { _history.value = it.distinctBy { item -> item.videoId } }
        }
    }

    fun removeWatchLater(videoId: String) {
        viewModelScope.launch {
            repo.removeWatchLater(videoId)
            _watchLater.value = _watchLater.value.filter { it.videoId != videoId }
        }
    }

    fun loadSeries(id: String) {
        viewModelScope.launch {
            _seriesState.value = SeriesUiState.Loading
            runCatching {
                repo.getSeries(id)
            }.onSuccess {
                _seriesState.value = SeriesUiState.Success(it)
            }.onFailure {
                _seriesState.value = SeriesUiState.Error(it.message ?: "Unbekannter Fehler")
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
