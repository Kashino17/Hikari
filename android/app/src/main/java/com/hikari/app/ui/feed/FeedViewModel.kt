package com.hikari.app.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hikari.app.data.api.dto.TodayCountResponse
import com.hikari.app.data.prefs.SettingsStore
import com.hikari.app.domain.model.FeedItem
import com.hikari.app.domain.repo.FeedRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

enum class FeedMode { NEW, SAVED, OLD }

@HiltViewModel
class FeedViewModel @Inject constructor(
    private val repo: FeedRepository,
    private val settings: SettingsStore,
) : ViewModel() {

    val backendUrl: StateFlow<String> = settings.backendUrl
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    private val _mode = MutableStateFlow(FeedMode.NEW)
    val mode: StateFlow<FeedMode> = _mode.asStateFlow()

    private val _savedItems = MutableStateFlow<List<FeedItem>>(emptyList())
    private val _oldItems = MutableStateFlow<List<FeedItem>>(emptyList())
    private val _saveOverrides = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun setMode(newMode: FeedMode) {
        _mode.value = newMode
        when (newMode) {
            FeedMode.NEW -> refresh()
            FeedMode.SAVED -> loadSaved()
            FeedMode.OLD -> loadOld()
        }
    }

    private fun loadOld() = viewModelScope.launch {
        _refreshing.value = true
        runCatching { repo.fetchOld() }
            .onSuccess {
                _oldItems.value = it.distinctBy { item -> item.videoId }
                _error.value = null
            }
            .onFailure { _error.value = it.message ?: "Archiv konnte nicht geladen werden" }
        _refreshing.value = false
    }

    private fun loadSaved() = viewModelScope.launch {
        _refreshing.value = true
        runCatching { repo.fetchSaved() }
            .onSuccess {
                _savedItems.value = it.distinctBy { item -> item.videoId }
                _error.value = null
            }
            .onFailure { _error.value = it.message ?: "Gespeicherte Videos konnten nicht geladen werden" }
        _refreshing.value = false
    }

    private val newItems: StateFlow<List<FeedItem>> =
        repo.newItems()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val items: StateFlow<List<FeedItem>> =
        combine(_mode, newItems, _savedItems, _oldItems, _saveOverrides) { mode, newL, savedL, oldL, overrides ->
            val base = when (mode) {
                FeedMode.NEW -> newL
                FeedMode.SAVED -> savedL
                FeedMode.OLD -> oldL
            }
            val patched = base
                .distinctBy { it.videoId }
                .withSaveOverrides(overrides)
            if (mode == FeedMode.SAVED) patched.filter { it.saved } else patched
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _today = MutableStateFlow<TodayCountResponse?>(null)
    val today: StateFlow<TodayCountResponse?> = _today.asStateFlow()

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        _refreshing.value = true
        when (_mode.value) {
            FeedMode.NEW -> {
                runCatching {
                    repo.refresh()
                    _today.value = repo.todayCount()
                }.onSuccess {
                    _error.value = null
                }.onFailure {
                    _error.value = it.message ?: "Feed konnte nicht geladen werden"
                }
            }
            FeedMode.SAVED -> {
                runCatching { repo.fetchSaved() }
                    .onSuccess {
                        _savedItems.value = it.distinctBy { item -> item.videoId }
                        _error.value = null
                    }
                    .onFailure { _error.value = it.message ?: "Gespeicherte Videos konnten nicht geladen werden" }
            }
            FeedMode.OLD -> {
                runCatching { repo.fetchOld() }
                    .onSuccess {
                        _oldItems.value = it.distinctBy { item -> item.videoId }
                        _error.value = null
                    }
                    .onFailure { _error.value = it.message ?: "Archiv konnte nicht geladen werden" }
            }
        }
        _refreshing.value = false
    }

    fun onSeen(id: String) = viewModelScope.launch {
        if (_mode.value == FeedMode.NEW) repo.markSeen(id)
    }
    fun onToggleSave(id: String, currentlySaved: Boolean) = viewModelScope.launch {
        val newSaved = !currentlySaved
        _saveOverrides.update { it + (id to newSaved) }
        yield()

        runCatching { repo.toggleSave(id, currentlySaved) }
            .onSuccess {
                _savedItems.value = _savedItems.value
                    .map { if (it.videoId == id) it.copy(saved = newSaved) else it }
                    .filter { it.saved }
                _oldItems.value = _oldItems.value.map {
                    if (it.videoId == id) it.copy(saved = newSaved) else it
                }
                _saveOverrides.update { it - id }
                _error.value = null
            }
            .onFailure {
                _saveOverrides.update { it - id }
                _error.value = it.message ?: "Speicherstatus konnte nicht aktualisiert werden"
            }
    }
    fun onUnplayable(id: String) = viewModelScope.launch { repo.markUnplayable(id) }
    fun onLessLikeThis(id: String) = viewModelScope.launch { repo.lessLikeThis(id) }
    fun onDelete(videoId: String) = viewModelScope.launch {
        repo.delete(videoId)
        if (_mode.value == FeedMode.OLD) loadOld()
    }
}

private fun List<FeedItem>.withSaveOverrides(overrides: Map<String, Boolean>): List<FeedItem> =
    map { item ->
        val saved = overrides[item.videoId] ?: return@map item
        item.copy(saved = saved)
    }
