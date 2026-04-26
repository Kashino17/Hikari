package com.hikari.app.ui.channels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hikari.app.data.api.dto.SeriesItemDto
import com.hikari.app.data.api.dto.UpdateVideoRequest
import com.hikari.app.data.api.dto.VideoDetailDto
import com.hikari.app.domain.repo.ChannelsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VideoEditFormState(
    val title: String = "",
    val description: String = "",
    val thumbnailUrl: String = "",
    val seriesId: String? = null,
    val seriesTitle: String = "",
    val season: Int? = null,
    val episode: Int? = null,
    val dubLanguage: String = "",
    val subLanguage: String = "",
    val isMovie: Boolean = false,
)

data class VideoEditUiState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
    val form: VideoEditFormState = VideoEditFormState(),
    val allSeries: List<SeriesItemDto> = emptyList(),
    val allDubLanguages: List<String> = emptyList(),
    val allSubLanguages: List<String> = emptyList(),
    val originalVideoId: String? = null,
)

@HiltViewModel
class VideoEditViewModel @Inject constructor(
    private val repo: ChannelsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VideoEditUiState())
    val uiState: StateFlow<VideoEditUiState> = _uiState.asStateFlow()

    fun load(videoId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null, saved = false) }
            runCatching {
                kotlinx.coroutines.coroutineScope {
                    val video = async { repo.getVideo(videoId) }
                    val series = async { runCatching { repo.listSeries() }.getOrDefault(emptyList()) }
                    val langs = async {
                        runCatching { repo.listLanguages() }.getOrNull()
                    }
                    Triple(video.await(), series.await(), langs.await())
                }
            }.onSuccess { (video, series, langs) ->
                _uiState.update {
                    it.copy(
                        loading = false,
                        originalVideoId = video.id,
                        form = video.toForm(),
                        allSeries = series,
                        allDubLanguages = langs?.dub.orEmpty(),
                        allSubLanguages = langs?.sub.orEmpty(),
                    )
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(loading = false, error = e.message ?: "Laden fehlgeschlagen")
                }
            }
        }
    }

    fun updateForm(transform: VideoEditFormState.() -> VideoEditFormState) {
        _uiState.update { it.copy(form = it.form.transform()) }
    }

    fun save() {
        val state = _uiState.value
        val id = state.originalVideoId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, error = null) }
            val req = state.form.toRequest()
            runCatching { repo.updateVideo(id, req) }
                .onSuccess {
                    _uiState.update { s ->
                        s.copy(saving = false, saved = true, form = it.toForm())
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(saving = false, error = e.message ?: "Speichern fehlgeschlagen") }
                }
        }
    }

    fun uploadThumbnail(bytes: ByteArray, mime: String) {
        val id = _uiState.value.originalVideoId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, error = null) }
            runCatching { repo.uploadVideoThumbnail(id, bytes, mime) }
                .onSuccess { v ->
                    _uiState.update {
                        it.copy(
                            saving = false,
                            form = it.form.copy(thumbnailUrl = v.thumbnail_url.orEmpty()),
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(saving = false, error = e.message ?: "Upload fehlgeschlagen") }
                }
        }
    }
}

private fun VideoDetailDto.toForm(): VideoEditFormState = VideoEditFormState(
    title = title,
    description = description.orEmpty(),
    thumbnailUrl = thumbnail_url.orEmpty(),
    seriesId = series_id,
    seriesTitle = series_title.orEmpty(),
    season = season,
    episode = episode,
    dubLanguage = dub_language.orEmpty(),
    subLanguage = sub_language.orEmpty(),
    isMovie = is_movie == 1,
)

private fun VideoEditFormState.toRequest(): UpdateVideoRequest = UpdateVideoRequest(
    title = title,
    description = description.ifBlank { null },
    thumbnail_url = thumbnailUrl.ifBlank { null },
    season = season,
    episode = episode,
    dub_language = dubLanguage.ifBlank { null },
    sub_language = subLanguage.ifBlank { null },
    is_movie = isMovie,
    series_id = seriesId,
    series_title = if (seriesId == null) seriesTitle.ifBlank { null } else null,
)
