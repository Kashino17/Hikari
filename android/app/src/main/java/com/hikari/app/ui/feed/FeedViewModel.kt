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

enum class FeedMode { NEW, OLD }

@HiltViewModel
class FeedViewModel @Inject constructor(
    private val repo: FeedRepository,
    private val settings: SettingsStore,
) : ViewModel() {

    val backendUrl: StateFlow<String> = settings.backendUrl
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    // Feed mode: NEW = unseen queue, OLD = history browse
    private val _mode = MutableStateFlow(FeedMode.NEW)
    val mode: StateFlow<FeedMode> = _mode.asStateFlow()

    private val _oldItems = MutableStateFlow<List<FeedItem>>(emptyList())
    val oldItems: StateFlow<List<FeedItem>> = _oldItems.asStateFlow()

    fun setMode(newMode: FeedMode) {
        _mode.value = newMode
        if (newMode == FeedMode.OLD) loadOld()
    }

    private fun loadOld() = viewModelScope.launch {
        _refreshing.value = true
        runCatching { repo.fetchOld() }.onSuccess { _oldItems.value = it }
        _refreshing.value = false
    }

    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    val items: StateFlow<List<FeedItem>> =
        combine(repo.unseenItems(), settings.dailyBudget, _selectedCategory) { list, budget, cat ->
            list.filter { cat == null || it.category == cat }.take(budget)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val categories: StateFlow<List<String>> =
        repo.unseenItems()
            .map { list -> list.map { it.category }.distinct().sorted() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _today = MutableStateFlow<TodayCountResponse?>(null)
    val today: StateFlow<TodayCountResponse?> = _today.asStateFlow()

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        _refreshing.value = true
        if (_mode.value == FeedMode.OLD) {
            runCatching { repo.fetchOld() }.onSuccess { _oldItems.value = it }
        } else {
            runCatching { repo.refresh() }
            runCatching { _today.value = repo.todayCount() }
        }
        _refreshing.value = false
    }

    fun selectCategory(cat: String?) { _selectedCategory.value = cat }

    fun onSeen(id: String) = viewModelScope.launch {
        // Skip marking seen in OLD mode — video is already in history
        if (_mode.value == FeedMode.NEW) repo.markSeen(id)
    }
    fun onToggleSave(id: String, currentlySaved: Boolean) = viewModelScope.launch {
        repo.toggleSave(id, currentlySaved)
    }
    fun onUnplayable(id: String) = viewModelScope.launch { repo.markUnplayable(id) }
    fun onLessLikeThis(id: String) = viewModelScope.launch { repo.lessLikeThis(id) }
}
