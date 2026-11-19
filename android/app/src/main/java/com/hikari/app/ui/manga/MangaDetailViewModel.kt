package com.hikari.app.ui.manga

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hikari.app.data.api.dto.MangaContinueDto
import com.hikari.app.data.api.dto.MangaSeriesDetailDto
import com.hikari.app.data.prefs.SettingsStore
import com.hikari.app.domain.download.LocalMangaDownloadManager
import com.hikari.app.domain.repo.MangaRepository
import com.hikari.app.domain.sync.MangaSyncObserver
import com.hikari.app.domain.sync.SyncStatus
import kotlinx.coroutines.flow.first
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface MangaDetailUiState {
    object Loading : MangaDetailUiState
    data class Success(
        val detail: MangaSeriesDetailDto,
        val continueItem: MangaContinueDto?,
    ) : MangaDetailUiState
    data class Error(val message: String) : MangaDetailUiState
}

@HiltViewModel
class MangaDetailViewModel @Inject constructor(
    private val repo: MangaRepository,
    private val downloads: LocalMangaDownloadManager,
    settings: SettingsStore,
    private val syncObserver: MangaSyncObserver,
) : ViewModel() {

    /** Läuft gerade ein Abgleich mit der Quelle (nur One Piece hat einen)? */
    val syncStatus: StateFlow<SyncStatus> = syncObserver.status
    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    fun startSyncPolling() = syncObserver.startPolling()
    fun stopSyncPolling() = syncObserver.stopPolling()

    /** Holt neue Kapitel von der Quelle; danach wird die Kapitelliste neu geladen. */
    fun startSync(seriesId: String) {
        viewModelScope.launch {
            _syncMessage.value = null
            val started = runCatching { repo.startSync() }
            val busy = started.exceptionOrNull()?.message.orEmpty().contains("409")
            if (started.isFailure && !busy) {
                _syncMessage.value = "Server nicht erreichbar"
                return@launch
            }
            _syncMessage.value = if (busy) "Abgleich läuft bereits" else "Abgleich läuft"
            syncObserver.startPolling()
            // Auf den Lauf warten: erst „läuft“ sehen (oder er war schon vorbei), dann „fertig“.
            kotlinx.coroutines.withTimeoutOrNull(10_000) { syncObserver.status.first { it is SyncStatus.Active } }
            val done = kotlinx.coroutines.withTimeoutOrNull(30 * 60_000L) { syncObserver.status.first { it is SyncStatus.Idle } } != null
            _syncMessage.value = if (done) "Fertig abgeglichen" else null
            load(seriesId)
            kotlinx.coroutines.delay(4_000)
            _syncMessage.value = null
        }
    }

    private val _uiState = MutableStateFlow<MangaDetailUiState>(MangaDetailUiState.Loading)
    val uiState: StateFlow<MangaDetailUiState> = _uiState.asStateFlow()
    val backendUrl: StateFlow<String> = settings.backendUrl
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    /** Set of arcIds, derived from the per-arc DAO, for cheap "downloaded?" lookups in the row. */
    val downloadedArcs: StateFlow<Set<String>> = downloads.downloadedArcIds
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** Per-arcId progress 0f..1f. Empty entry = not currently downloading. */
    val arcProgress: StateFlow<Map<String, Float>> = downloads.progress
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /**
     * Fire-and-forget Error-Events. Channel statt StateFlow, damit ein Recompose
     * keinen schon-gezeigten Snackbar nochmal triggert. DROP_OLDEST falls der
     * User gerade nicht hinschaut — letzter Fehler überschreibt ältere.
     */
    private val _errors = Channel<String>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val errors: Flow<String> = _errors.receiveAsFlow()

    fun load(seriesId: String) {
        viewModelScope.launch {
            _uiState.value = MangaDetailUiState.Loading
            runCatching {
                coroutineScope {
                    val d = async { repo.getSeries(seriesId) }
                    val c = async { repo.getContinue() }
                    d.await() to c.await().firstOrNull { it.seriesId == seriesId }
                }
            }.onSuccess { (detail, cont) ->
                _uiState.value = MangaDetailUiState.Success(detail, cont)
            }.onFailure {
                _uiState.value = MangaDetailUiState.Error(it.message ?: "Nicht gefunden")
            }
        }
    }

    /** Nach dem Lesen still neu laden (ohne „Lade…“), damit Stelle und gelesene Kapitel stimmen. */
    fun refresh(seriesId: String) {
        if (_uiState.value !is MangaDetailUiState.Success) return
        viewModelScope.launch {
            runCatching {
                coroutineScope {
                    val d = async { repo.getSeries(seriesId) }
                    val c = async { repo.getContinue() }
                    d.await() to c.await().firstOrNull { it.seriesId == seriesId }
                }
            }.onSuccess { (detail, cont) -> _uiState.value = MangaDetailUiState.Success(detail, cont) }
        }
    }

    private val prefetched = HashSet<String>()

    /** MangaDex: das Kapitel, mit dem es weitergeht, schon im Hintergrund holen lassen — dann öffnet es sofort. */
    fun prefetch(chapterId: String?) {
        if (chapterId == null || !chapterId.startsWith("mangadex:") || !prefetched.add(chapterId)) return
        viewModelScope.launch { runCatching { repo.startChapterSync(chapterId) } }
    }

    fun downloadArc(arcId: String) {
        viewModelScope.launch {
            downloads.download(arcId).onFailure { e ->
                _errors.trySend("Download fehlgeschlagen: ${e.message ?: e::class.simpleName}")
            }
        }
    }

    fun coverUrl(baseUrl: String, coverPath: String): String =
        repo.coverImageUrl(baseUrl, coverPath)
}
