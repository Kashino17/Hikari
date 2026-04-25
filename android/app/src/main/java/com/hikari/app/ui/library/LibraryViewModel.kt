package com.hikari.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hikari.app.data.api.dto.LibraryResponse
import com.hikari.app.data.api.dto.SeriesDetailResponse
import com.hikari.app.domain.repo.FeedRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface LibraryUiState {
    object Loading : LibraryUiState
    data class Success(val data: LibraryResponse) : LibraryUiState
    data class Error(val message: String) : LibraryUiState
}

sealed interface SeriesUiState {
    object Loading : SeriesUiState
    data class Success(val data: SeriesDetailResponse) : SeriesUiState
    data class Error(val message: String) : SeriesUiState
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repo: FeedRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<LibraryUiState>(LibraryUiState.Loading)
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private val _seriesState = MutableStateFlow<SeriesUiState>(SeriesUiState.Loading)
    val seriesState: StateFlow<SeriesUiState> = _seriesState.asStateFlow()

    init {
        loadLibrary()
    }

    fun loadLibrary() {
        viewModelScope.launch {
            _uiState.value = LibraryUiState.Loading
            runCatching {
                repo.getLibrary()
            }.onSuccess {
                _uiState.value = LibraryUiState.Success(it)
            }.onFailure {
                _uiState.value = LibraryUiState.Error(it.message ?: "Unbekannter Fehler")
            }
        }
    }

    fun loadSeries(id: String) {
        viewModelScope.launch {
            _seriesState.value = SeriesUiState.Loading
            runCatching {
                repo.getSeries(id)
            }.onSuccess {
                _seriesState.value = SeriesUiState.Success(it)
            }.onFailure {
                _seriesState.value = SeriesUiState.Error(it.message ?: "Unbekannter Fehler")
            }
        }
    }
}
