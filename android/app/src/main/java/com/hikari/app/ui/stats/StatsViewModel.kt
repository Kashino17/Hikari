package com.hikari.app.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hikari.app.data.api.HikariApi
import com.hikari.app.data.api.dto.WeeklyStatsDto
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class StatsViewModel @Inject constructor(private val api: HikariApi) : ViewModel() {
    private val _stats = MutableStateFlow<WeeklyStatsDto?>(null)
    val stats: StateFlow<WeeklyStatsDto?> = _stats.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init { load() }

    fun load() = viewModelScope.launch {
        _loading.value = true
        _error.value = null
        runCatching { api.getWeeklyStats() }
            .onSuccess { _stats.value = it }
            .onFailure { _error.value = it.message ?: "Failed to load stats" }
        _loading.value = false
    }
}
