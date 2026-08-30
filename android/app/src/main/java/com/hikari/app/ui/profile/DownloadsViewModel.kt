package com.hikari.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hikari.app.data.api.dto.DownloadsResponse
import com.hikari.app.data.api.dto.PendingImportDto
import com.hikari.app.data.prefs.SettingsStore
import com.hikari.app.domain.download.SmartDownloadScheduler
import com.hikari.app.domain.repo.ChannelsRepository
import com.hikari.app.domain.repo.DownloadsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface DownloadsUiState {
    object Loading : DownloadsUiState
    data class Success(val data: DownloadsResponse) : DownloadsUiState
    data class Error(val message: String) : DownloadsUiState
}

data class LocalSummary(val count: Int, val totalBytes: Long)

data class MangaSummary(
    val arcCount: Int,
    val totalPages: Int,
    val totalBytes: Long,
    val arcIds: List<String>,
)

/**
 * Musik-Downloads leben — wie Mangas — ausschließlich lokal in Room, der
 * Server weiß nichts davon. [thumbnailUrls] dient nur der Cover-Vorschau auf
 * der CategoryCard (analog zu [MangaSummary.arcIds]).
 */
data class MusicSummary(
    val count: Int,
    val totalBytes: Long,
    val thumbnailUrls: List<String> = emptyList(),
    val videoIds: List<String> = emptyList(),
)

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloadsRepo: DownloadsRepository,
    private val channelsRepo: ChannelsRepository,
    private val settings: SettingsStore,
    private val scheduler: SmartDownloadScheduler,
    localDownloadDao: com.hikari.app.data.db.LocalDownloadDao,
    localMangaDao: com.hikari.app.data.db.LocalMangaDao,
    localMusicDao: com.hikari.app.data.db.LocalMusicDownloadDao,
) : ViewModel() {

    private val _state = MutableStateFlow<DownloadsUiState>(DownloadsUiState.Loading)
    val state: StateFlow<DownloadsUiState> = _state.asStateFlow()

    /** Gerade laufende Importe — der Tab pollt sie, solange er sichtbar ist. */
    private val _transfers = MutableStateFlow<List<PendingImportDto>>(emptyList())
    val transfers: StateFlow<List<PendingImportDto>> = _transfers.asStateFlow()

    /** Ein Abruf fürs Polling; bei Fehler bleibt der letzte Stand stehen. */
    suspend fun loadTransfers() {
        runCatching { channelsRepo.listImports() }
            .onSuccess { _transfers.value = it }
    }

    val smartDownloads: StateFlow<Boolean> = settings.smartDownloads
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val localSummary: StateFlow<LocalSummary> = localDownloadDao.observeAll()
        .map { rows ->
            LocalSummary(
                count = rows.size,
                totalBytes = rows.sumOf { it.byteSize },
            )
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, LocalSummary(0, 0L))

    val mangaSummary: StateFlow<MangaSummary> = localMangaDao.observeArcs()
        .map { arcs ->
            MangaSummary(
                arcCount = arcs.size,
                totalPages = arcs.sumOf { it.expectedPageCount },
                totalBytes = arcs.sumOf { it.totalByteSize },
                arcIds = arcs.map { it.arcId },
            )
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, MangaSummary(0, 0, 0L, emptyList()))

    // Room-getrieben wie mangaSummary: Musik kommt nie vom Server.
    val musicSummary: StateFlow<MusicSummary> = localMusicDao.observeAll()
        .map { rows ->
            MusicSummary(
                count = rows.size,
                totalBytes = rows.sumOf { it.byteSize },
                thumbnailUrls = rows.map { it.thumbnailUrl },
                videoIds = rows.map { it.videoId },
            )
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, MusicSummary(0, 0L))

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = DownloadsUiState.Loading
            // DownloadsRepository.load() versucht Server, fällt bei Fehler auf
            // den lokalen Bestand zurück. Damit kann die UI nie mehr in Error
            // landen — Offline = leer aber funktional.
            val data = downloadsRepo.load()
            _state.value = DownloadsUiState.Success(data)
        }
    }

    fun setSmartDownloads(enabled: Boolean) = viewModelScope.launch {
        settings.setSmartDownloads(enabled)
        if (enabled) {
            // Kick off a one-shot sync immediately so the user sees activity
            // (subject to WLAN constraint).
            scheduler.runOnceNow()
        }
    }
}
