package com.hikari.app.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hikari.app.data.api.dto.DownloadsResponse
import com.hikari.app.data.prefs.SettingsStore
import com.hikari.app.data.prefs.SponsorBlockPrefs
import com.hikari.app.data.sponsor.SegmentBehavior
import com.hikari.app.domain.repo.FeedRepository
import com.hikari.app.domain.repo.MangaRepository
import com.hikari.app.domain.update.UpdateCheckResult
import com.hikari.app.domain.update.UpdateManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** UI-State des In-App-Updaters in den Einstellungen. */
sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data object UpToDate : UpdateUiState
    data class Available(val version: String, val downloadUrl: String) : UpdateUiState
    data class Downloading(val version: String, val percent: Int) : UpdateUiState
    data class Downloaded(val version: String, val apk: File) : UpdateUiState
    data class Error(val message: String) : UpdateUiState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val store: SettingsStore,
    private val feedRepo: FeedRepository,
    private val mangaRepo: MangaRepository,
    private val sbPrefs: SponsorBlockPrefs,
    private val updateManager: UpdateManager,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    val backendUrl: Flow<String> = store.backendUrl
    val dailyBudget: Flow<Int> = store.dailyBudget
    val smartDownloads: Flow<Boolean> = store.smartDownloads
    val authToken: Flow<String> = store.authToken
    val sbBehaviors: Flow<Map<String, SegmentBehavior>> = sbPrefs.behaviors

    // Zeitbudget des Tagesmixes (Minuten) — lebt im Backend; null = noch nicht geladen.
    private val _budgetMinutes = MutableStateFlow<Int?>(null)
    val budgetMinutes: StateFlow<Int?> = _budgetMinutes.asStateFlow()

    private val _mangaSyncStatus = MutableStateFlow<String?>(null)
    val mangaSyncStatus: StateFlow<String?> = _mangaSyncStatus.asStateFlow()

    private val _diskUsage = MutableStateFlow<DownloadsResponse?>(null)
    val diskUsage: StateFlow<DownloadsResponse?> = _diskUsage.asStateFlow()

    private val _updateState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val updateState: StateFlow<UpdateUiState> = _updateState.asStateFlow()

    init {
        refreshDiskUsage()
        loadBudgetMinutes()
    }

    fun setBackendUrl(url: String) = viewModelScope.launch {
        store.setBackendUrl(url)
    }

    fun setDailyBudget(value: Int) = viewModelScope.launch {
        store.setDailyBudget(value)
    }

    fun setSmartDownloads(enabled: Boolean) = viewModelScope.launch {
        store.setSmartDownloads(enabled)
    }

    fun setAuthToken(token: String) = viewModelScope.launch {
        store.setAuthToken(token)
    }

    private fun loadBudgetMinutes() = viewModelScope.launch {
        runCatching { feedRepo.getBudgetMinutes() }.onSuccess { _budgetMinutes.value = it }
    }

    fun setBudgetMinutes(minutes: Int) = viewModelScope.launch {
        runCatching { feedRepo.setBudgetMinutes(minutes) }.onSuccess { _budgetMinutes.value = it }
    }

    fun setSbBehavior(apiKey: String, behavior: SegmentBehavior) = viewModelScope.launch {
        sbPrefs.setBehavior(apiKey, behavior)
    }

    fun triggerMangaSync() = viewModelScope.launch {
        _mangaSyncStatus.value = null
        runCatching { mangaRepo.startSync() }
            .onSuccess { _mangaSyncStatus.value = "Sync gestartet" }
            .onFailure { e ->
                _mangaSyncStatus.value =
                    if ("409" in e.message.orEmpty()) "Sync läuft bereits" else "Backend nicht erreichbar"
            }
        delay(5_000)
        _mangaSyncStatus.value = null
    }

    fun refreshDiskUsage() = viewModelScope.launch {
        runCatching { feedRepo.getDownloads() }
            .onSuccess { _diskUsage.value = it }
    }

    /** Prüft über die GitHub-API, ob ein neueres Release verfügbar ist. */
    fun checkForUpdate() {
        if (_updateState.value is UpdateUiState.Checking ||
            _updateState.value is UpdateUiState.Downloading
        ) return
        _updateState.value = UpdateUiState.Checking
        viewModelScope.launch {
            _updateState.value = when (val result = updateManager.checkForUpdate()) {
                is UpdateCheckResult.Available ->
                    UpdateUiState.Available(result.version, result.downloadUrl)
                UpdateCheckResult.UpToDate -> UpdateUiState.UpToDate
                is UpdateCheckResult.Error -> UpdateUiState.Error(result.message)
            }
        }
    }

    /** Lädt das APK in den App-Cache; Fortschritt landet im State. */
    fun downloadUpdate(version: String, url: String) {
        _updateState.value = UpdateUiState.Downloading(version, percent = 0)
        viewModelScope.launch {
            val target = File(appContext.cacheDir, "update.apk")
            updateManager.downloadApk(url, target) { percent ->
                _updateState.value = UpdateUiState.Downloading(version, percent)
            }
                .onSuccess { apk ->
                    _updateState.value = UpdateUiState.Downloaded(version, apk)
                }
                .onFailure { e ->
                    _updateState.value = UpdateUiState.Error(
                        e.message ?: "Download fehlgeschlagen",
                    )
                }
        }
    }

    /** Zurück auf Idle — z. B. wenn der Update-Dialog geschlossen wird. */
    fun resetUpdateState() {
        _updateState.value = UpdateUiState.Idle
    }
}
