package com.hikari.app.ui.tuning

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hikari.app.data.api.HikariApi
import com.hikari.app.data.api.dto.ClipperStatusDto
import com.hikari.app.data.api.dto.LlmHealthDto
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
    private val api: HikariApi,
    savedState: SavedStateHandle,
) : ViewModel() {

    /**
     * When non-null, this screen edits ONE channel's filter (per-channel mode);
     * when null, it edits the global filter. Sourced from the `channelId` nav arg.
     */
    val channelId: String? = savedState.get<String>("channelId")?.takeIf { it.isNotBlank() }

    val isChannelScoped: Boolean get() = channelId != null

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
        val cid = channelId
        runCatching { if (cid != null) filterRepo.fetchForChannel(cid) else filterRepo.fetch() }
            .onSuccess { _state.value = it; _error.value = null }
            .onFailure { _error.value = it.message ?: "Konnte Filter nicht laden" }
    }

    /** Per-channel only: drop the channel's own filter so it inherits the global one. */
    fun resetToGlobal() {
        val cid = channelId ?: return
        viewModelScope.launch {
            _saving.value = true
            runCatching {
                filterRepo.resetChannelToGlobal(cid)
                filterRepo.fetchForChannel(cid)
            }
                .onSuccess { _state.value = it; _error.value = null }
                .onFailure { _error.value = it.message ?: "Zurücksetzen fehlgeschlagen" }
            _saving.value = false
        }
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
        val cid = channelId
        // Optimistic update. In channel mode the first edit promotes an
        // inherited filter into the channel's own copy, so inherited→false.
        _state.value = cur.copy(filter = next, inherited = if (cid != null) false else cur.inherited)
        viewModelScope.launch {
            runCatching {
                if (cid != null) filterRepo.updateFilterForChannel(cid, next)
                else filterRepo.updateFilter(next)
            }
                .onSuccess { _state.value = it; _error.value = null }
                .onFailure {
                    _error.value = it.message ?: "Speichern fehlgeschlagen"
                    runCatching {
                        if (cid != null) filterRepo.fetchForChannel(cid) else filterRepo.fetch()
                    }.onSuccess { _state.value = it }
                }
        }
    }

    fun setOverride(prompt: String) = viewModelScope.launch {
        val cid = channelId
        _saving.value = true
        runCatching {
            if (cid != null) filterRepo.setOverrideForChannel(cid, prompt)
            else filterRepo.setOverride(prompt)
        }
            .onSuccess { _state.value = it; _error.value = null }
            .onFailure { _error.value = it.message ?: "Override speichern fehlgeschlagen" }
        _saving.value = false
    }

    fun clearOverride() = viewModelScope.launch {
        val cid = channelId
        _saving.value = true
        // In channel mode "clear override" reverts the whole channel to global.
        runCatching {
            if (cid != null) {
                filterRepo.resetChannelToGlobal(cid)
                filterRepo.fetchForChannel(cid)
            } else {
                filterRepo.clearOverride()
            }
        }
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

    // ── Clipper status ───────────────────────────────────────────────────────
    private val _clipperStatus = MutableStateFlow<ClipperStatusDto?>(null)
    val clipperStatus: StateFlow<ClipperStatusDto?> = _clipperStatus.asStateFlow()

    private val _clipperRetrying = MutableStateFlow(false)
    val clipperRetrying: StateFlow<Boolean> = _clipperRetrying.asStateFlow()

    private val _llmHealth = MutableStateFlow<LlmHealthDto?>(null)
    val llmHealth: StateFlow<LlmHealthDto?> = _llmHealth.asStateFlow()

    fun loadClipperStatus() = viewModelScope.launch {
        _clipperStatus.value = runCatching { api.getClipperStatus() }.getOrNull()
        _llmHealth.value = runCatching { api.getLlmHealth() }.getOrNull()
    }

    fun retryFailedClips() = viewModelScope.launch {
        _clipperRetrying.value = true
        runCatching { api.retryFailed() }
        _clipperStatus.value = runCatching { api.getClipperStatus() }.getOrNull()
        _clipperRetrying.value = false
    }

    // ── Settings ─────────────────────────────────────────────────────────────
    fun setBackendUrl(url: String) = viewModelScope.launch { settings.setBackendUrl(url) }
    fun setDailyBudget(value: Int) = viewModelScope.launch { settings.setDailyBudget(value) }

    fun setSbBehavior(apiKey: String, behavior: SegmentBehavior) = viewModelScope.launch {
        sbPrefs.setBehavior(apiKey, behavior)
    }
}
