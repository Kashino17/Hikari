package com.hikari.app.ui.profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hikari.app.data.api.dto.DownloadsResponse
import com.hikari.app.domain.repo.FeedRepository
import com.hikari.app.ui.profile.tabs.DownloadCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DownloadCategoryUiState(
    val category: DownloadCategory,
    val loading: Boolean = true,
    val error: String? = null,
    val data: DownloadsResponse? = null,
    val expandedGroupId: String? = null,
    val editMode: Boolean = false,
    val selectedVideoIds: Set<String> = emptySet(),
)

@HiltViewModel
class DownloadCategoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repo: FeedRepository,
) : ViewModel() {

    private val initialCategory = (savedStateHandle.get<String>("category") ?: "SERIES")
        .let { name -> runCatching { DownloadCategory.valueOf(name) }.getOrDefault(DownloadCategory.SERIES) }

    private val _state = MutableStateFlow(DownloadCategoryUiState(category = initialCategory))
    val state: StateFlow<DownloadCategoryUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { repo.getDownloads() }
                .onSuccess { data ->
                    _state.update { it.copy(loading = false, data = data) }
                }
                .onFailure { e ->
                    _state.update { it.copy(loading = false, error = e.message ?: "Konnte Downloads nicht laden") }
                }
        }
    }

    fun toggleExpand(groupId: String) {
        _state.update {
            it.copy(expandedGroupId = if (it.expandedGroupId == groupId) null else groupId)
        }
    }

    fun setEditMode(enabled: Boolean) {
        _state.update {
            it.copy(editMode = enabled, selectedVideoIds = if (!enabled) emptySet() else it.selectedVideoIds)
        }
    }

    fun toggleSelection(videoId: String) {
        _state.update {
            val s = it.selectedVideoIds.toMutableSet()
            if (s.contains(videoId)) s.remove(videoId) else s.add(videoId)
            it.copy(selectedVideoIds = s, editMode = true)
        }
    }

    fun clearSelection() {
        _state.update { it.copy(selectedVideoIds = emptySet()) }
    }

    fun deleteSelected() {
        val ids = _state.value.selectedVideoIds.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val failures = mutableListOf<String>()
            ids.forEach { videoId ->
                runCatching { repo.delete(videoId) }.onFailure { failures.add(videoId) }
            }
            // Refresh inline so we can keep failed IDs selected for retry
            runCatching { repo.getDownloads() }
                .onSuccess { data ->
                    _state.update {
                        it.copy(
                            loading = false,
                            data = data,
                            selectedVideoIds = failures.toSet(),
                            editMode = failures.isNotEmpty(),
                            error = if (failures.isEmpty()) null
                                else "${failures.size} von ${ids.size} konnten nicht gelöscht werden",
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(loading = false, error = e.message ?: "Reload nach Löschen fehlgeschlagen")
                    }
                }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
