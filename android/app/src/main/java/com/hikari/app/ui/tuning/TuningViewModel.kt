package com.hikari.app.ui.tuning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hikari.app.data.prefs.SettingsStore
import com.hikari.app.data.prefs.SponsorBlockPrefs
import com.hikari.app.data.sponsor.SegmentBehavior
import com.hikari.app.data.sponsor.SegmentCategories
import com.hikari.app.domain.model.FilterConfig
import com.hikari.app.domain.model.FilterState
import com.hikari.app.domain.repo.FilterRepository
import com.hikari.app.domain.repo.MangaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class TuningViewModel @Inject constructor(
    private val filterRepo: FilterRepository,
    private val settings: SettingsStore,
    private val sbPrefs: SponsorBlockPrefs,
    private val mangaRepo: MangaRepository,
) : ViewModel() {

    // ── Filter / Prompt state (loaded from server) ───────────────────────────
    private val _state = MutableStateFlow<FilterState?>(null)
    val state: StateFlow<FilterState?> = _state.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // ── Settings (mirrored from DataStore) ───────────────────────────────────
    val backendUrl: StateFlow<String> = settings.backendUrl
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val dailyBudget: StateFlow<Int> = settings.dailyBudget
        .stateIn(viewModelScope, SharingStarted.Eagerly, 15)

    val sbBehaviors: StateFlow<Map<String, SegmentBehavior>> = sbPrefs.behaviors
        .stateIn(
            viewModelScope, SharingStarted.Eagerly,
            SegmentCategories.all.associate { it.apiKey to it.defaultBehavior },
        )

    init { load() }

    fun load() = viewModelScope.launch {
        runCatching { filterRepo.fetch() }
            .onSuccess { _state.value = it; _error.value = null }
            .onFailure { _error.value = it.message ?: "Konnte Filter nicht laden" }
    }

    /**
     * Optimistically updates local state, then fires a server PUT in the
     * background. If the server rejects, we surface an error and refetch.
     * No spinner during normal edits — only the explicit "save override" path
     * shows _saving = true.
     */
    fun updateFilter(transform: (FilterConfig) -> FilterConfig) {
        val cur = _state.value ?: return
        val next = transform(cur.filter)
        // Optimistic update — keep assembled prompt as-is until server returns
        _state.value = cur.copy(filter = next)
        viewModelScope.launch {
            runCatching { filterRepo.updateFilter(next) }
                .onSuccess { _state.value = it; _error.value = null }
                .onFailure {
                    _error.value = it.message ?: "Speichern fehlgeschlagen"
                    runCatching { filterRepo.fetch() }.onSuccess { _state.value = it }
                }
        }
    }

    fun setOverride(prompt: String) = viewModelScope.launch {
        _saving.value = true
        runCatching { filterRepo.setOverride(prompt) }
            .onSuccess { _state.value = it; _error.value = null }
            .onFailure { _error.value = it.message ?: "Override speichern fehlgeschlagen" }
        _saving.value = false
    }

    fun clearOverride() = viewModelScope.launch {
        _saving.value = true
        runCatching { filterRepo.clearOverride() }
            .onSuccess { _state.value = it; _error.value = null }
            .onFailure { _error.value = it.message ?: "Override löschen fehlgeschlagen" }
        _saving.value = false
    }

    // ── Manga sync ───────────────────────────────────────────────────────────
    private val _mangaSyncStatus = MutableStateFlow<String?>(null)
    val mangaSyncStatus: StateFlow<String?> = _mangaSyncStatus.asStateFlow()

    fun triggerMangaSync() {
        viewModelScope.launch {
            _mangaSyncStatus.value = null
            runCatching { mangaRepo.startSync() }
                .onSuccess { _mangaSyncStatus.value = "Sync gestartet" }
                .onFailure { e ->
                    val msg = e.message.orEmpty()
                    _mangaSyncStatus.value = when {
                        "409" in msg -> "Sync läuft bereits"
                        else -> "Backend nicht erreichbar"
                    }
                }
            delay(5_000)
            _mangaSyncStatus.value = null
        }
    }

    // ── Settings ─────────────────────────────────────────────────────────────
    fun setBackendUrl(url: String) = viewModelScope.launch { settings.setBackendUrl(url) }
    fun setDailyBudget(value: Int) = viewModelScope.launch { settings.setDailyBudget(value) }

    fun setSbBehavior(apiKey: String, behavior: SegmentBehavior) = viewModelScope.launch {
        sbPrefs.setBehavior(apiKey, behavior)
    }
}
