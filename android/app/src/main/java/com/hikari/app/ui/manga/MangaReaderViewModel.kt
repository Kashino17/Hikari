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
import kotlinx.coroutines.sync.withLock

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

    private var syncStarted = false

    /** Überlebt das Schließen des Lesers (nur für das letzte Speichern). */
    private val detached by lazy { kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + saveDispatcher) }

    /** Wo das Speichern läuft — Tests setzen hier ihre steuerbare Uhr ein. */
    internal var saveDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO

    /** Erst lesen, wenn jede Seite auf dem Mac oder auf dem Handy liegt — sonst bleiben Seiten leer. */
    private fun allReady(pages: List<MangaPageDto>, local: Map<String, String>): Boolean =
        pages.isNotEmpty() && pages.all { it.ready || local.containsKey(it.id) }

    /** Nächstes Kapitel in derselben Sprache (MangaDex hat de und en nebeneinander). */
    private fun nextChapterId(chapters: List<com.hikari.app.data.api.dto.MangaChapterDto>, current: String): String? {
        val lang = chapters.firstOrNull { it.id == current }?.lang
        val sorted = chapters.filter { it.lang == lang && it.isAvailable == 1 || it.id == current }.sortedBy { it.number }
        val idx = sorted.indexOfFirst { it.id == current }
        return if (idx >= 0 && idx < sorted.size - 1) sorted[idx + 1].id else null
    }

    fun load(seriesId: String, chapterId: String) {
        this.seriesId = seriesId
        this.chapterId = chapterId
        viewModelScope.launch {
            _uiState.value = ReaderUiState.Loading
            runCatching {
                // 404 = der Mac hat die Seiten noch nie geholt (z. B. neue MangaDex-Kapitel) → wie leer behandeln.
                val pages = try {
                    repo.getChapterPages(chapterId)
                } catch (e: retrofit2.HttpException) {
                    if (e.code() == 404) emptyList() else throw e
                }
                val detail = repo.getSeries(seriesId)
                pages to detail
            }.onSuccess { (pages, detail) ->
                val local = resolveLocalPaths(pages)
                if (!allReady(pages, local)) {
                    _uiState.value = ReaderUiState.Syncing(chapterId)
                    syncStarted = runCatching { repo.startChapterSync(chapterId) }.isSuccess || pages.isNotEmpty()
                    startPollingForPages()
                } else {
                    val next = nextChapterId(detail.chapters, chapterId)
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
                // Läuft gerade ein anderer Abgleich (409), später noch einmal anstoßen.
                if (!syncStarted) syncStarted = runCatching { repo.startChapterSync(chapterId) }.isSuccess
                runCatching { repo.getChapterPages(chapterId) }.onSuccess { pages ->
                    if (allReady(pages, resolveLocalPaths(pages))) {
                        val detail = runCatching { repo.getSeries(seriesId) }.getOrNull()
                        val next = nextChapterId(detail?.chapters.orEmpty(), chapterId)
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
            pendingPage?.let(::persist)
        }
    }

    private val saveLock = kotlinx.coroutines.sync.Mutex()

    /** Speichern immer der Reihe nach (Mutex ist fair): eine ältere Anfrage kann die letzte Seite nicht überholen. */
    private fun persist(page: Int) {
        val sId = seriesId
        val cId = chapterId
        detached.launch { saveLock.withLock { runCatching { repo.setProgress(sId, cId, page) } } }
    }

    /**
     * Letzte Stelle sofort sichern — beim Verlassen des Lesers. Läuft in einem eigenen Ablauf, weil der
     * des Bildschirms beim Verlassen abgebrochen wird (sonst ging die letzte Seite verloren).
     */
    fun flushProgress() {
        val p = pendingPage ?: return
        saveJob?.cancel()
        persist(p)
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
