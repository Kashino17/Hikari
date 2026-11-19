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
    /** Der Mac holt das Kapitel gerade; ready/total = schon geladene Seiten (0/0 = noch unbekannt). */
    data class Syncing(val chapterId: String, val ready: Int = 0, val total: Int = 0) : ReaderUiState
    data class Success(
        val pages: List<MangaPageDto>,
        val nextChapterId: String?,
        /** Gemerkte Seite in diesem Kapitel (1-basiert), sonst null = was der Aufruf mitgab. */
        val startPage: Int? = null,
    ) : ReaderUiState
    data class Error(val message: String) : ReaderUiState
}

@HiltViewModel
class MangaReaderViewModel @Inject constructor(
    private val repo: MangaRepository,
    private val mangaDownloads: LocalMangaDownloadManager,
    settings: SettingsStore,
    positions: com.hikari.app.domain.manga.MangaPositions,
) : ViewModel() {
    private val store = positions.store

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
                    _uiState.value = ReaderUiState.Syncing(chapterId, pages.count { it.ready || local.containsKey(it.id) }, pages.size)
                    syncStarted = runCatching { repo.startChapterSync(chapterId) }.isSuccess || pages.isNotEmpty()
                    startPollingForPages()
                } else {
                    val next = nextChapterId(detail.chapters, chapterId)
                    mergeFromMac(seriesId)
                    pushPending()
                    _localPagePaths.value = resolveLocalPaths(pages)
                    _uiState.value = ReaderUiState.Success(pages, next, storedPage(seriesId, chapterId))
                }
            }.onFailure {
                // Kein Netz (oder Mac aus): heruntergeladenes Kapitel vom Handy lesen.
                val offline = runCatching { offlineChapter(seriesId, chapterId) }.getOrNull()
                _uiState.value = offline ?: ReaderUiState.Error(
                    if (it is java.io.IOException) "Kein Netz — dieses Kapitel ist nicht heruntergeladen." else it.message ?: "Nicht gefunden",
                )
            }
        }
    }

    private suspend fun offlineChapter(seriesId: String, chapterId: String): ReaderUiState.Success? {
        val local = mangaDownloads.localChapterPages(chapterId)
        if (local.isEmpty()) return null
        _localPagePaths.value = local.associate { it.pageId to it.localFilePath }
        val pages = local.map { MangaPageDto(it.pageId, it.pageNumber, true) }
        // Nächstes heruntergeladenes Kapitel in derselben Sprache (Kennung …:<lang>:<nr>).
        val prefix = chapterId.substringBeforeLast(':')
        val current = local.first().chapterNumber
        val next = mangaDownloads.localChapterIds(seriesId)
            .filter { (id, n) -> id.substringBeforeLast(':') == prefix && n > current }
            .minByOrNull { it.second }?.first
        return ReaderUiState.Success(pages, next, storedPage(seriesId, chapterId))
    }

    /** Gemerkte Seite, wenn die zuletzt gelesene Stelle in genau diesem Kapitel liegt. */
    private fun storedPage(seriesId: String, chapterId: String): Int? =
        store.get(seriesId)?.takeIf { it.chapterId == chapterId }?.page

    /** Stand vom Mac übernehmen, wenn er neuer ist (z. B. auf einem anderen Gerät weitergelesen). */
    private suspend fun mergeFromMac(seriesId: String) {
        runCatching { repo.getContinue() }.getOrNull()
            ?.firstOrNull { it.seriesId == seriesId }
            ?.let { store.mergeServer(seriesId, it.chapterId, it.pageNumber, it.updatedAt) }
    }

    /** Offline Gelesenes, das der Mac noch nicht kennt, nachreichen (mit seiner Uhrzeit). */
    private fun pushPending() {
        for ((sId, pos) in store.pending()) {
            detached.launch {
                saveLock.withLock {
                    runCatching { repo.setProgress(sId, pos.chapterId, pos.page, pos.at) }
                        .onSuccess { store.markSynced(sId, pos.at) }
                }
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
                    val local = resolveLocalPaths(pages)
                    if (!allReady(pages, local)) {
                        _uiState.value = ReaderUiState.Syncing(chapterId, pages.count { it.ready || local.containsKey(it.id) }, pages.size)
                    }
                    if (allReady(pages, local)) {
                        val detail = runCatching { repo.getSeries(seriesId) }.getOrNull()
                        val next = nextChapterId(detail?.chapters.orEmpty(), chapterId)
                        _localPagePaths.value = resolveLocalPaths(pages)
                        mergeFromMac(seriesId)
                        _uiState.value = ReaderUiState.Success(pages, next, storedPage(seriesId, chapterId))
                        return@launch
                    }
                }
            }
        }
    }

    /** Debounced — emits the latest call's pageNumber 1.5s after the last call. */
    fun savePosition(pageNumber: Int) {
        pendingPage = pageNumber
        // Sofort auf dem Handy merken — hält auch beim Beenden der App und ohne Netz.
        store.put(seriesId, chapterId, pageNumber)
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
        val at = store.get(sId)?.takeIf { it.chapterId == cId && it.page == page }?.at ?: System.currentTimeMillis()
        detached.launch {
            saveLock.withLock {
                runCatching { repo.setProgress(sId, cId, page, at) }.onSuccess { store.markSynced(sId, at) }
            }
        }
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
