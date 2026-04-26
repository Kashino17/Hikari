package com.hikari.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hikari.app.data.api.dto.DownloadsResponse
import com.hikari.app.data.prefs.SettingsStore
import com.hikari.app.domain.repo.FeedRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface DownloadsUiState {
    object Loading : DownloadsUiState
    data class Success(val data: DownloadsResponse) : DownloadsUiState
    data class Error(val message: String) : DownloadsUiState
}

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val repo: FeedRepository,
    private val settings: SettingsStore,
) : ViewModel() {

    private val _state = MutableStateFlow<DownloadsUiState>(DownloadsUiState.Loading)
    val state: StateFlow<DownloadsUiState> = _state.asStateFlow()

    val smartDownloads: StateFlow<Boolean> = settings.smartDownloads
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = DownloadsUiState.Loading
            runCatching { repo.getDownloads() }
                .onSuccess { _state.value = DownloadsUiState.Success(it) }
                .onFailure {
                    _state.value = DownloadsUiState.Error(it.message ?: "Konnte Downloads nicht laden")
                }
        }
    }

    fun setSmartDownloads(enabled: Boolean) = viewModelScope.launch {
        settings.setSmartDownloads(enabled)
    }
}
