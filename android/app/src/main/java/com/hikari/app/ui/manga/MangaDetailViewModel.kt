package com.hikari.app.ui.manga

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hikari.app.data.api.dto.MangaContinueDto
import com.hikari.app.data.api.dto.MangaSeriesDetailDto
import com.hikari.app.data.prefs.SettingsStore
import com.hikari.app.domain.download.LocalMangaDownloadManager
import com.hikari.app.domain.repo.MangaRepository
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
) : ViewModel() {

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

    fun downloadArc(arcId: String) {
        viewModelScope.launch { downloads.download(arcId) }
    }

    fun coverUrl(baseUrl: String, coverPath: String): String =
        repo.coverImageUrl(baseUrl, coverPath)
}
