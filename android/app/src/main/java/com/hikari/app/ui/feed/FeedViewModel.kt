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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

    private val _oldItems = MutableStateFlow<List<FeedItem>>(emptyList())

    fun setMode(newMode: FeedMode) {
        _mode.value = newMode
        if (newMode == FeedMode.OLD) loadOld()
    }

    private fun loadOld() = viewModelScope.launch {
        _refreshing.value = true
        runCatching { repo.fetchOld() }
            .onSuccess { list -> _oldItems.value = list.distinctBy { it.videoId } }
        _refreshing.value = false
    }

    private val newItems: StateFlow<List<FeedItem>> =
        combine(repo.unseenItems(), settings.dailyBudget) { list, budget ->
            list.distinctBy { it.videoId }.take(budget)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val savedItems: StateFlow<List<FeedItem>> =
        repo.savedItems()
            .map { it.distinctBy { item -> item.videoId } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val items: StateFlow<List<FeedItem>> =
        combine(_mode, newItems, savedItems, _oldItems) { mode, newL, savedL, oldL ->
            when (mode) {
                FeedMode.NEW -> newL
                FeedMode.SAVED -> savedL
                FeedMode.OLD -> oldL
            }
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
                runCatching { repo.refresh() }
                runCatching { _today.value = repo.todayCount() }
            }
            FeedMode.SAVED -> { /* savedItems is a Room Flow — already live */ }
            FeedMode.OLD -> {
                runCatching { repo.fetchOld() }.onSuccess { _oldItems.value = it }
            }
        }
        _refreshing.value = false
    }

    fun onSeen(id: String) = viewModelScope.launch {
        if (_mode.value == FeedMode.NEW) repo.markSeen(id)
    }
    fun onToggleSave(id: String, currentlySaved: Boolean) = viewModelScope.launch {
        repo.toggleSave(id, currentlySaved)
    }
    fun onUnplayable(id: String) = viewModelScope.launch { repo.markUnplayable(id) }
    fun onLessLikeThis(id: String) = viewModelScope.launch { repo.lessLikeThis(id) }
    fun onDelete(videoId: String) = viewModelScope.launch {
        repo.delete(videoId)
        if (_mode.value == FeedMode.OLD) loadOld()
    }
}
