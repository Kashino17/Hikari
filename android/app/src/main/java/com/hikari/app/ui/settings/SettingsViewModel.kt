package com.hikari.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hikari.app.data.api.dto.DownloadsResponse
import com.hikari.app.data.db.LocalDownloadDao
import com.hikari.app.data.db.LocalDownloadEntity
import com.hikari.app.data.db.LocalDownloadSource
import com.hikari.app.data.db.LocalMusicDownloadDao
import com.hikari.app.data.prefs.SettingsStore
import com.hikari.app.data.prefs.SponsorBlockPrefs
import com.hikari.app.data.sponsor.SegmentBehavior
import com.hikari.app.domain.download.SmartDownloadScheduler
import com.hikari.app.domain.download.SmartDownloadStorage
import com.hikari.app.domain.repo.FeedRepository
import com.hikari.app.domain.repo.MangaRepository
import com.hikari.app.domain.update.UpdateCoordinator
import com.hikari.app.domain.update.UpdateState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Erreichbarkeit des eigenen Servers, wie sie die Einstellungen zeigen. */
sealed interface ServerStatus {
    data object Checking : ServerStatus
    data class Connected(val disk: DownloadsResponse) : ServerStatus
    data object Unreachable : ServerStatus
}

/** Video-Downloads auf diesem Gerät, getrennt nach Herkunft. */
data class DeviceDownloads(
    val manual: List<LocalDownloadEntity> = emptyList(),
    val smart: List<LocalDownloadEntity> = emptyList(),
) {
    val manualBytes: Long get() = manual.sumOf { it.byteSize }
    val smartBytes: Long get() = smart.sumOf { it.byteSize }
    val count: Int get() = manual.size + smart.size
    val totalBytes: Long get() = manualBytes + smartBytes
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val store: SettingsStore,
    private val feedRepo: FeedRepository,
    private val mangaRepo: MangaRepository,
    private val sbPrefs: SponsorBlockPrefs,
    private val updates: UpdateCoordinator,
    private val smartScheduler: SmartDownloadScheduler,
    private val smartStorage: SmartDownloadStorage,
    localDownloadDao: LocalDownloadDao,
    localMusicDao: LocalMusicDownloadDao,
) : ViewModel() {

    // ── Server ───────────────────────────────────────────────────────────
    val backendUrl: Flow<String> = store.backendUrl
    val authToken: Flow<String> = store.authToken

    private val _server = MutableStateFlow<ServerStatus>(ServerStatus.Checking)
    val server: StateFlow<ServerStatus> = _server.asStateFlow()

    private val _mangaSyncStatus = MutableStateFlow<String?>(null)
    val mangaSyncStatus: StateFlow<String?> = _mangaSyncStatus.asStateFlow()

    // ── Wiedergabe ───────────────────────────────────────────────────────
    val sbBehaviors: Flow<Map<String, SegmentBehavior>> = sbPrefs.behaviors
    val skippedCount: Flow<Long> = sbPrefs.totalSkippedCount
    val skippedMs: Flow<Long> = sbPrefs.totalSkippedMs

    // ── Downloads ────────────────────────────────────────────────────────
    val smartDownloads: Flow<Boolean> = store.smartDownloads
    val manualRetentionDays: Flow<Int> = store.manualDownloadRetentionDays
    val smartRetentionDays: Flow<Int> = store.smartDownloadRetentionDays
    val smartSeries: Flow<Boolean> = store.smartSeries
    val smartMusic: Flow<Boolean> = store.smartMusic
    val smartYoutube: Flow<Boolean> = store.smartYoutube
    val smartBudgetGb: Flow<Int> = store.smartBudgetGb

    /** Von Smart-Downloads geholte Songs — zählen mit ins Budget. */
    val smartSongBytes: StateFlow<Long> = localMusicDao.observeAll()
        .map { rows -> rows.filter { it.source == LocalDownloadSource.SMART.name }.sumOf { it.byteSize } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    val deviceDownloads: StateFlow<DeviceDownloads> = localDownloadDao.observeAll()
        .map { rows ->
            val (smart, manual) = rows.partition { it.source == LocalDownloadSource.SMART.name }
            DeviceDownloads(manual = manual, smart = smart)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, DeviceDownloads())

    // ── App ──────────────────────────────────────────────────────────────
    val updateState: StateFlow<UpdateState> = updates.state

    init {
        refreshServer()
    }

    /** Fragt den Server einmal an — die Antwort dient zugleich als Speicherstand. */
    fun refreshServer() = viewModelScope.launch {
        _server.value = ServerStatus.Checking
        _server.value = runCatching { feedRepo.getDownloads() }
            .fold({ ServerStatus.Connected(it) }, { ServerStatus.Unreachable })
    }

    fun setBackendUrl(url: String) = viewModelScope.launch {
        store.setBackendUrl(url)
        // Der Netzwerk-Client übernimmt die neue Adresse über einen Flow — kurz warten.
        delay(300)
        refreshServer()
    }

    fun setAuthToken(token: String) = viewModelScope.launch {
        store.setAuthToken(token)
        delay(300)
        refreshServer()
    }

    fun triggerMangaSync() = viewModelScope.launch {
        _mangaSyncStatus.value = null
        runCatching { mangaRepo.startSync() }
            .onSuccess { _mangaSyncStatus.value = "Abgleich gestartet" }
            .onFailure { e ->
                _mangaSyncStatus.value =
                    if ("409" in e.message.orEmpty()) "Abgleich läuft bereits" else "Server nicht erreichbar"
            }
        delay(5_000)
        _mangaSyncStatus.value = null
    }

    fun setSbBehavior(apiKey: String, behavior: SegmentBehavior) = viewModelScope.launch {
        sbPrefs.setBehavior(apiKey, behavior)
    }

    fun setSmartDownloads(enabled: Boolean) = viewModelScope.launch {
        store.setSmartDownloads(enabled)
        // Gleich einmal loslaufen lassen (WLAN vorausgesetzt), statt bis zu 6 h zu warten.
        if (enabled) smartScheduler.runOnceNow()
    }

    fun setSmartSeries(enabled: Boolean) = viewModelScope.launch { store.setSmartSeries(enabled) }

    fun setSmartMusic(enabled: Boolean) = viewModelScope.launch { store.setSmartMusic(enabled) }

    fun setSmartYoutube(enabled: Boolean) = viewModelScope.launch { store.setSmartYoutube(enabled) }

    fun setSmartBudgetGb(gb: Int) = viewModelScope.launch { store.setSmartBudgetGb(gb) }

    /** Räumt alle Smart-Downloads vom Gerät — sie kommen beim nächsten Lauf nach Bedarf wieder. */
    fun clearSmartDownloads() = viewModelScope.launch { runCatching { smartStorage.clearAll() } }

    fun setManualRetentionDays(days: Int) = viewModelScope.launch {
        store.setManualDownloadRetentionDays(days)
    }

    fun setSmartRetentionDays(days: Int) = viewModelScope.launch {
        store.setSmartDownloadRetentionDays(days)
    }

    fun checkForUpdate() = updates.check()

    /** Lädt das gefundene Update und startet die Installation. */
    fun startUpdate() = updates.startUpdate()
}
