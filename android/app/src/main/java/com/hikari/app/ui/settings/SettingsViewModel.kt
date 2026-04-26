package com.hikari.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hikari.app.data.api.dto.DownloadsResponse
import com.hikari.app.data.prefs.SettingsStore
import com.hikari.app.domain.repo.FeedRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val store: SettingsStore,
    private val feedRepo: FeedRepository,
) : ViewModel() {

    val backendUrl: Flow<String> = store.backendUrl
    val dailyBudget: Flow<Int> = store.dailyBudget
    val smartDownloads: Flow<Boolean> = store.smartDownloads

    private val _diskUsage = MutableStateFlow<DownloadsResponse?>(null)
    val diskUsage: StateFlow<DownloadsResponse?> = _diskUsage.asStateFlow()

    init {
        refreshDiskUsage()
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

    fun refreshDiskUsage() = viewModelScope.launch {
        runCatching { feedRepo.getDownloads() }
            .onSuccess { _diskUsage.value = it }
    }
}
