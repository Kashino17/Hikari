package com.hikari.app.ui.profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hikari.app.data.api.dto.LibraryVideoDto
import com.hikari.app.data.api.dto.SeriesGroupDto
import com.hikari.app.data.net.ConnectivityObserver
import com.hikari.app.data.db.PlaybackPositionEntity
import com.hikari.app.domain.download.LocalDownloadManager
import com.hikari.app.domain.download.LocalDownloadMetadata
import com.hikari.app.domain.repo.ChannelsRepository
import com.hikari.app.domain.repo.DownloadsRepository
import com.hikari.app.domain.repo.FeedRepository
import com.hikari.app.domain.repo.PlaybackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Calendar
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Eine Folge auf der Download-Detailseite — Server- und Lokalstand vereint. */
data class DownloadDetailEpisode(
    val id: String,
    val title: String,
    val season: Int?,
    val episode: Int?,
    val durationSeconds: Int,
    val thumbnailUrl: String?,
    val description: String?,
    /** Fortschritt laut Backend; lokale Room-Position hat in der UI Vorrang. */
    val serverProgressSeconds: Float?,
    /** Dateigröße auf dem Server, 0 wenn unbekannt (nicht heruntergeladen). */
    val sizeBytes: Long,
    /** true, wenn die Datei auf dem Server liegt und geladen werden kann. */
    val available: Boolean = true,
)

data class DownloadSeriesDetail(
    val id: String,
    val title: String,
    val description: String?,
    val thumbnailUrl: String?,
    val year: Int?,
    val episodes: List<DownloadDetailEpisode>,
    /** true = kein Serverkontakt, Liste enthält nur bereits geladene Folgen. */
    val offlineOnly: Boolean,
)

data class DownloadMovieDetail(
    val id: String,
    val title: String,
    val description: String?,
    val thumbnailUrl: String?,
    val year: Int?,
    val durationSeconds: Int,
    val sizeBytes: Long,
    val serverProgressSeconds: Float?,
)

sealed interface DownloadDetailUiState {
    data object Loading : DownloadDetailUiState
    data class Series(val data: DownloadSeriesDetail) : DownloadDetailUiState
    data class Movie(val data: DownloadMovieDetail) : DownloadDetailUiState
    data class Error(val message: String) : DownloadDetailUiState
}

/**
 * Lädt die Detailansicht einer heruntergeladenen Serie oder eines Films.
 *
 * Online kommt die volle Folgenliste samt Beschreibung aus `/series/:id` bzw.
 * `/videos/:id` — so sieht die Seite aus wie in der Bibliothek, inklusive
 * Folgen, die noch nicht auf dem Gerät liegen. Offline fällt sie auf die
 * lokal vorhandenen Downloads zurück; nur die lassen sich dann ohnehin
 * abspielen.
 */
@HiltViewModel
class DownloadDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val feedRepo: FeedRepository,
    private val downloadsRepo: DownloadsRepository,
    private val channelsRepo: ChannelsRepository,
    private val localDownloads: LocalDownloadManager,
    private val playbackRepo: PlaybackRepository,
    private val connectivity: ConnectivityObserver,
) : ViewModel() {

    private val seriesId: String? = savedStateHandle.get<String>("seriesId")
    private val videoId: String? = savedStateHandle.get<String>("videoId")

    private val _state = MutableStateFlow<DownloadDetailUiState>(DownloadDetailUiState.Loading)
    val state: StateFlow<DownloadDetailUiState> = _state.asStateFlow()

    private val _bulkDownloading = MutableStateFlow(false)
    val bulkDownloading: StateFlow<Boolean> = _bulkDownloading.asStateFlow()

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    /** videoIds, die tatsächlich auf dem Gerät liegen. */
    val localIds: StateFlow<Set<String>> = localDownloads.downloadedIds
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** Laufender Download pro videoId (0f..1f), absent = kein Download. */
    val downloadProgress: StateFlow<Map<String, Float>> = localDownloads.progress

    /** Lokale Wiedergabeposition — frischer als der Serverstand. */
    val playbackPositions: StateFlow<Map<String, PlaybackPositionEntity>> = playbackRepo.positions
        .map { list -> list.associateBy { it.videoId } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = DownloadDetailUiState.Loading
            _actionError.value = null
            val downloads = runCatching { downloadsRepo.load() }.getOrNull()
            val online = connectivity.currentlyOnline()
            when {
                seriesId != null -> _state.value = loadSeries(seriesId, downloads, online)
                videoId != null -> _state.value = loadMovie(videoId, downloads, online)
                else -> _state.value = DownloadDetailUiState.Error("Unbekannter Download")
            }
        }
    }

    private suspend fun loadSeries(
        id: String,
        downloads: com.hikari.app.data.api.dto.DownloadsResponse?,
        online: Boolean,
    ): DownloadDetailUiState {
        val group = downloads?.series?.firstOrNull { it.id == id }
        val server = if (online) runCatching { feedRepo.getSeries(id) }.getOrNull() else null

        return when {
            server != null -> {
                val episodes = server.videos.map { video ->
                    video.toDetailEpisode(
                        sizeBytes = group?.episodes?.firstOrNull { it.id == video.id }?.size_bytes ?: 0L,
                        fallbackProgress = group?.episodes?.firstOrNull { it.id == video.id }?.progress_seconds,
                    )
                }
                DownloadDetailUiState.Series(
                    DownloadSeriesDetail(
                        id = server.id,
                        title = server.title,
                        description = server.description,
                        thumbnailUrl = server.thumbnail_url ?: group?.thumbnail_url,
                        year = server.videos.firstOrNull()?.published_at.toYear(),
                        episodes = episodes,
                        offlineOnly = false,
                    ),
                )
            }
            group != null -> DownloadDetailUiState.Series(group.toDetail(offlineOnly = true))
            else -> DownloadDetailUiState.Error(
                if (online) "Serie nicht gefunden" else "Offline — diese Serie ist nicht auf dem Gerät",
            )
        }
    }

    private suspend fun loadMovie(
        id: String,
        downloads: com.hikari.app.data.api.dto.DownloadsResponse?,
        online: Boolean,
    ): DownloadDetailUiState {
        val entry = downloads?.movies?.firstOrNull { it.id == id }
        val server = if (online) runCatching { channelsRepo.getVideo(id) }.getOrNull() else null

        if (entry == null && server == null) {
            return DownloadDetailUiState.Error(
                if (online) "Film nicht gefunden" else "Offline — dieser Film ist nicht auf dem Gerät",
            )
        }
        return DownloadDetailUiState.Movie(
            DownloadMovieDetail(
                id = id,
                title = server?.title ?: entry?.title.orEmpty(),
                description = server?.description,
                thumbnailUrl = server?.thumbnail_url ?: entry?.thumbnail_url,
                year = server?.published_at.toYear(),
                durationSeconds = server?.duration_seconds ?: entry?.duration_seconds ?: 0,
                sizeBytes = entry?.size_bytes ?: 0L,
                serverProgressSeconds = entry?.progress_seconds,
            ),
        )
    }

    private fun LibraryVideoDto.toDetailEpisode(
        sizeBytes: Long,
        fallbackProgress: Float?,
    ) = DownloadDetailEpisode(
        id = id,
        title = title,
        season = season,
        episode = episode,
        durationSeconds = duration_seconds,
        thumbnailUrl = thumbnail_url,
        description = description,
        serverProgressSeconds = progress_seconds ?: fallbackProgress,
        sizeBytes = sizeBytes,
        available = downloaded != 0,
    )

    private fun SeriesGroupDto.toDetail(offlineOnly: Boolean) = DownloadSeriesDetail(
        id = id,
        title = title,
        description = null,
        thumbnailUrl = thumbnail_url,
        year = null,
        episodes = episodes
            .sortedWith(compareBy({ it.season ?: 1 }, { it.episode ?: Int.MAX_VALUE }))
            .map { ep ->
                DownloadDetailEpisode(
                    id = ep.id,
                    title = ep.title,
                    season = ep.season,
                    episode = ep.episode,
                    durationSeconds = ep.duration_seconds,
                    thumbnailUrl = ep.thumbnail_url,
                    description = null,
                    serverProgressSeconds = ep.progress_seconds,
                    sizeBytes = ep.size_bytes,
                )
            },
        offlineOnly = offlineOnly,
    )

    fun downloadEpisode(episode: DownloadDetailEpisode) {
        val detail = (_state.value as? DownloadDetailUiState.Series)?.data ?: return
        launchDownload(
            LocalDownloadMetadata(
                videoId = episode.id,
                kind = com.hikari.app.data.db.LocalDownloadKind.SERIES,
                title = episode.title,
                durationSeconds = episode.durationSeconds,
                thumbnailUrl = episode.thumbnailUrl,
                seriesId = detail.id,
                seriesTitle = detail.title,
                seriesThumbnailUrl = detail.thumbnailUrl,
                season = episode.season,
                episode = episode.episode,
            ),
        )
    }

    fun downloadMovie(movie: DownloadMovieDetail) {
        launchDownload(
            LocalDownloadMetadata(
                videoId = movie.id,
                kind = com.hikari.app.data.db.LocalDownloadKind.MOVIE,
                title = movie.title,
                durationSeconds = movie.durationSeconds,
                thumbnailUrl = movie.thumbnailUrl,
            ),
        )
    }

    /** Lädt alle noch fehlenden Folgen der übergebenen Staffel nacheinander. */
    fun downloadEpisodes(episodes: List<DownloadDetailEpisode>) {
        if (_bulkDownloading.value) return
        val detail = (_state.value as? DownloadDetailUiState.Series)?.data ?: return
        viewModelScope.launch {
            _bulkDownloading.value = true
            _actionError.value = null
            for (episode in episodes) {
                if (localDownloads.isDownloaded(episode.id)) continue
                val result = localDownloads.download(
                    LocalDownloadMetadata(
                        videoId = episode.id,
                        kind = com.hikari.app.data.db.LocalDownloadKind.SERIES,
                        title = episode.title,
                        durationSeconds = episode.durationSeconds,
                        thumbnailUrl = episode.thumbnailUrl,
                        seriesId = detail.id,
                        seriesTitle = detail.title,
                        seriesThumbnailUrl = detail.thumbnailUrl,
                        season = episode.season,
                        episode = episode.episode,
                    ),
                )
                if (result.isFailure) {
                    _actionError.value = "Download fehlgeschlagen — ist das Gerät online?"
                    break
                }
            }
            _bulkDownloading.value = false
        }
    }

    private fun launchDownload(meta: LocalDownloadMetadata) {
        viewModelScope.launch {
            _actionError.value = null
            localDownloads.download(meta).onFailure {
                _actionError.value = "Download fehlgeschlagen — ist das Gerät online?"
            }
        }
    }

    fun removeLocal(videoId: String) {
        viewModelScope.launch { localDownloads.delete(videoId) }
    }

    fun clearActionError() {
        _actionError.value = null
    }

    private fun Long?.toYear(): Int? {
        if (this == null || this <= 0L) return null
        return Calendar.getInstance().apply { timeInMillis = this@toYear }.get(Calendar.YEAR)
    }
}
