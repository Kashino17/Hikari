package com.hikari.app.ui.manga

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hikari.app.data.api.dto.MangaPageDto
import com.hikari.app.data.prefs.SettingsStore
import com.hikari.app.domain.download.LocalMangaDownloadManager
import com.hikari.app.domain.repo.MangaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface ReaderUiState {
    object Loading : ReaderUiState
    data class Syncing(val chapterId: String) : ReaderUiState
    data class Success(
        val pages: List<MangaPageDto>,
        val nextChapterId: String?,
    ) : ReaderUiState
    data class Error(val message: String) : ReaderUiState
}

@HiltViewModel
class MangaReaderViewModel @Inject constructor(
    private val repo: MangaRepository,
    private val mangaDownloads: LocalMangaDownloadManager,
    settings: SettingsStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()
    val backendUrl: StateFlow<String> = settings.backendUrl
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    /**
     * pageId → absolute path of an offline copy on this device (sourced from
     * LocalMangaDownloadManager). Populated before ReaderUiState.Success is
     * emitted so the first composition uses the correct file:// URL — no
     * Coil cache-flip from backend → local mid-session.
     */
    private val _localPagePaths = MutableStateFlow<Map<String, String>>(emptyMap())

    private var seriesId: String = ""
    private var chapterId: String = ""
    private var saveJob: Job? = null
    private var pendingPage: Int? = null
    private var pollJob: Job? = null

    private suspend fun resolveLocalPaths(pages: List<MangaPageDto>): Map<String, String> {
        val out = mutableMapOf<String, String>()
        for (p in pages) {
            mangaDownloads.localPageFile(p.id)?.let { out[p.id] = it.absolutePath }
        }
        return out
    }

    fun load(seriesId: String, chapterId: String) {
        this.seriesId = seriesId
        this.chapterId = chapterId
        viewModelScope.launch {
            _uiState.value = ReaderUiState.Loading
            runCatching {
                val pages = repo.getChapterPages(chapterId)
                val detail = repo.getSeries(seriesId)
                pages to detail
            }.onSuccess { (pages, detail) ->
                if (pages.isEmpty()) {
                    _uiState.value = ReaderUiState.Syncing(chapterId)
                    runCatching { repo.startChapterSync(chapterId) }
                    startPollingForPages()
                } else {
                    val sorted = detail.chapters.sortedBy { it.number }
                    val idx = sorted.indexOfFirst { it.id == chapterId }
                    val next = if (idx >= 0 && idx < sorted.size - 1) sorted[idx + 1].id else null
                    _localPagePaths.value = resolveLocalPaths(pages)
                    _uiState.value = ReaderUiState.Success(pages, next)
                }
            }.onFailure {
                _uiState.value = ReaderUiState.Error(it.message ?: "Nicht gefunden")
            }
        }
    }

    private fun startPollingForPages() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                delay(3_000)
                runCatching { repo.getChapterPages(chapterId) }.onSuccess { pages ->
                    if (pages.isNotEmpty()) {
                        val detail = runCatching { repo.getSeries(seriesId) }.getOrNull()
                        val sorted = detail?.chapters?.sortedBy { it.number } ?: emptyList()
                        val idx = sorted.indexOfFirst { it.id == chapterId }
                        val next = if (idx >= 0 && idx < sorted.size - 1) sorted[idx + 1].id else null
                        _localPagePaths.value = resolveLocalPaths(pages)
                        _uiState.value = ReaderUiState.Success(pages, next)
                        return@launch
                    }
                }
            }
        }
    }

    /** Debounced — emits the latest call's pageNumber 1.5s after the last call. */
    fun savePosition(pageNumber: Int) {
        pendingPage = pageNumber
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(1_500)
            val p = pendingPage ?: return@launch
            runCatching { repo.setProgress(seriesId, chapterId, p) }
        }
    }

    /** Synchronous final flush — call from `DisposableEffect.onDispose`. */
    fun flushProgress() {
        val p = pendingPage ?: return
        viewModelScope.launch {
            runCatching { repo.setProgress(seriesId, chapterId, p) }
        }
    }

    fun markChapterRead() {
        viewModelScope.launch {
            runCatching { repo.markChapterRead(chapterId) }
        }
    }

    fun pageImageUrl(baseUrl: String, pageId: String): String {
        val local = _localPagePaths.value[pageId]
        return if (local != null) "file://$local" else repo.pageImageUrl(baseUrl, pageId)
    }

    /** Cancels the page-availability poll loop. Called by the screen on
     *  dispose and by tests so `runTest` can settle. */
    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    override fun onCleared() {
        super.onCleared()
        pollJob?.cancel()
        saveJob?.cancel()
    }
}
