package com.hikari.app.ui.profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hikari.app.data.api.dto.DownloadsResponse
import com.hikari.app.domain.download.LocalDownloadManager
import com.hikari.app.domain.download.LocalDownloadMetadata
import com.hikari.app.domain.repo.DownloadsRepository
import com.hikari.app.domain.repo.FeedRepository
import com.hikari.app.ui.profile.tabs.DownloadCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
    private val downloadsRepo: DownloadsRepository,
    private val localDownloads: LocalDownloadManager,
) : ViewModel() {

    private val initialCategory = (savedStateHandle.get<String>("category") ?: "SERIES")
        .let { name -> runCatching { DownloadCategory.valueOf(name) }.getOrDefault(DownloadCategory.SERIES) }

    private val _state = MutableStateFlow(DownloadCategoryUiState(category = initialCategory))
    val state: StateFlow<DownloadCategoryUiState> = _state.asStateFlow()

    /** videoIds that are present on this device's local storage */
    val localIds: StateFlow<Set<String>> = localDownloads.downloadedIds
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** Per-videoId download progress 0f..1f. Absent = not downloading. */
    val downloadProgress: StateFlow<Map<String, Float>> = localDownloads.progress

    init {
        load()
    }

    fun downloadLocally(meta: LocalDownloadMetadata) {
        viewModelScope.launch {
            localDownloads.download(meta)
                .onFailure { e ->
                    _state.update { it.copy(error = "Lokal-Download: ${e.message}") }
                }
        }
    }

    fun removeLocal(videoId: String) {
        viewModelScope.launch { localDownloads.delete(videoId) }
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val data = downloadsRepo.load()
            _state.update { it.copy(loading = false, data = data) }
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
            val serverFailures = mutableListOf<String>()
            ids.forEach { videoId ->
                // Server-Delete (best effort — schlägt offline fehl, ist ok)
                runCatching { repo.delete(videoId) }.onFailure { serverFailures.add(videoId) }
                // Lokal immer entfernen, damit Storage tatsächlich frei wird
                runCatching { localDownloads.delete(videoId) }
            }
            // Reload nutzt offline-Fallback automatisch
            val data = downloadsRepo.load()
            _state.update {
                it.copy(
                    loading = false,
                    data = data,
                    selectedVideoIds = emptySet(),
                    editMode = false,
                    error = if (serverFailures.isEmpty()) null
                        else "${serverFailures.size} Server-Löschungen fehlgeschlagen (lokal entfernt)",
                )
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
