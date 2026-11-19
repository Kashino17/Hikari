package com.hikari.app.ui.manga

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hikari.app.data.api.dto.MangaContinueDto
import com.hikari.app.data.api.dto.MangaSeriesDto
import com.hikari.app.data.prefs.SettingsStore
import com.hikari.app.domain.repo.MangaRepository
import com.hikari.app.domain.sync.MangaSyncObserver
import com.hikari.app.domain.sync.SyncStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface MangaListUiState {
    object Loading : MangaListUiState
    data class Success(
        val series: List<MangaSeriesDto>,
        val continueItems: List<MangaContinueDto>,
    ) : MangaListUiState
    data class Error(val message: String) : MangaListUiState
}

data class OfflineChapter(val id: String, val number: String, val lang: String?)
data class OfflineSeries(val seriesId: String, val title: String, val chapters: List<OfflineChapter>)

@HiltViewModel
class MangaListViewModel @Inject constructor(
    private val repo: MangaRepository,
    private val observer: MangaSyncObserver,
    settings: SettingsStore,
    private val downloads: com.hikari.app.domain.download.LocalMangaDownloadManager,
) : ViewModel() {

    /** Heruntergeladene Reihen mit ihren Kapiteln — das, was ohne Netz lesbar ist. */
    val offline: StateFlow<List<OfflineSeries>> = downloads.arcs
        .map { arcs ->
            arcs.groupBy { it.seriesId }.map { (seriesId, list) ->
                val chapters = runCatching { downloads.localChapterIds(seriesId) }.getOrDefault(emptyList())
                OfflineSeries(
                    seriesId = seriesId,
                    title = list.first().seriesTitle,
                    chapters = chapters.map { (id, n) ->
                        OfflineChapter(id, if (n % 1.0 == 0.0) n.toInt().toString() else n.toString(), id.split(":").getOrNull(2)?.takeIf { it == "de" || it == "en" })
                    },
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _uiState = MutableStateFlow<MangaListUiState>(MangaListUiState.Loading)
    val uiState: StateFlow<MangaListUiState> = _uiState.asStateFlow()
    val syncStatus: StateFlow<SyncStatus> = observer.status
    val backendUrl: StateFlow<String> = settings.backendUrl
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    init { reload() }

    fun reload() {
        viewModelScope.launch {
            _uiState.value = MangaListUiState.Loading
            runCatching {
                coroutineScope {
                    val s = async { repo.listSeries() }
                    val c = async { repo.getContinue() }
                    s.await() to c.await()
                }
            }.onSuccess { (series, cont) ->
                _uiState.value = MangaListUiState.Success(series, cont)
            }.onFailure {
                _uiState.value = MangaListUiState.Error(it.message ?: "Unbekannter Fehler")
            }
        }
    }

    fun startSyncPolling() = observer.startPolling()
    fun stopSyncPolling() = observer.stopPolling()
    fun coverUrl(baseUrl: String, coverPath: String): String =
        repo.coverImageUrl(baseUrl, coverPath)
}
