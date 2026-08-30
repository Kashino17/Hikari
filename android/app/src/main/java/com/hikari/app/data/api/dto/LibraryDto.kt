package com.hikari.app.data.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class SeriesDto(
    val id: String,
    val title: String,
    val description: String? = null,
    val thumbnail_url: String? = null,
    val added_at: Long
)

@Serializable
data class LibraryVideoDto(
    val id: String,
    val channel_id: String,
    val series_id: String? = null,
    val title: String,
    val description: String? = null,
    val published_at: Long,
    val duration_seconds: Int,
    val aspect_ratio: String? = null,
    val thumbnail_url: String? = null,
    val discovered_at: Long,
    val season: Int? = null,
    val episode: Int? = null,
    val channelTitle: String? = null,
    val progress_seconds: Float? = null,
    val overall_score: Int? = null,
    /**
     * 1, wenn die Datei tatsächlich auf dem Server liegt. Eine videos-Zeile
     * allein heißt das nicht: Ein abgebrochener Import oder eine weggeräumte
     * Datei hinterlässt eine Folge, die sich nicht abspielen lässt. Default 1,
     * damit ältere Server (die das Feld nicht senden) unverändert wirken.
     */
    val downloaded: Int = 1,
)

@Serializable
data class LibraryResponse(
    val series: List<SeriesDto>,
    val recentlyAdded: List<LibraryVideoDto>,
    val channels: List<ChannelDto>,
    // Etappe 5: die Sammlung — optional, damit alte Server weiter parsen.
    val watchLater: List<FeedItemDto> = emptyList(),
    val history: List<FeedItemDto> = emptyList(),
    val suggestions: List<FeedItemDto> = emptyList(),
)

@Serializable
data class SeriesDetailResponse(
    val id: String,
    val title: String,
    val description: String? = null,
    val thumbnail_url: String? = null,
    val added_at: Long,
    val videos: List<LibraryVideoDto>
)

@Serializable
data class UpdateSeriesRequest(
    val thumbnail_url: String? = null,
    val description: String? = null,
)

@Serializable
data class MergeSeriesRequest(
    val sourceId: String,
    val targetId: String,
)

/** Antwort von GET /videos/{id}/next — die nächste Folge derselben Serie. */
@Serializable
data class NextVideoDto(
    val id: String,
    val title: String,
    val season: Int? = null,
    val episode: Int? = null,
    val thumbnailUrl: String? = null,
)

@Serializable
data class VideoDetailDto(
    val id: String,
    val channel_id: String,
    val series_id: String? = null,
    val series_title: String? = null,
    val title: String,
    val description: String? = null,
    val published_at: Long = 0L,
    val duration_seconds: Int = 0,
    val thumbnail_url: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val dub_language: String? = null,
    val sub_language: String? = null,
    val is_movie: Int = 0,
)

@Serializable
data class UpdateVideoRequest(
    val title: String? = null,
    val description: String? = null,
    val thumbnail_url: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val dub_language: String? = null,
    val sub_language: String? = null,
    val is_movie: Boolean? = null,
    val series_id: String? = null,
    val series_title: String? = null,
)
