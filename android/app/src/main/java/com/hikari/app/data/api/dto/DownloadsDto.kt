package com.hikari.app.data.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class DownloadsResponse(
    val total_bytes: Long = 0L,
    val limit_bytes: Long = 0L,
    val series: List<SeriesGroupDto> = emptyList(),
    val channels: List<ChannelGroupDto> = emptyList(),
    val movies: List<MovieEntryDto> = emptyList(),
)

@Serializable
data class SeriesGroupDto(
    val id: String,
    val title: String,
    val thumbnail_url: String? = null,
    val episode_count: Int = 0,
    val total_bytes: Long = 0L,
    val episodes: List<SeriesEpisodeDto> = emptyList(),
)

@Serializable
data class SeriesEpisodeDto(
    val id: String,
    val title: String,
    val season: Int? = null,
    val episode: Int? = null,
    val duration_seconds: Int = 0,
    val thumbnail_url: String? = null,
    val size_bytes: Long = 0L,
    val downloaded_at: Long = 0L,
)

@Serializable
data class ChannelGroupDto(
    val id: String,
    val title: String,
    val thumbnail_url: String? = null,
    val banner_url: String? = null,
    val video_count: Int = 0,
    val total_bytes: Long = 0L,
    val videos: List<ChannelVideoEntryDto> = emptyList(),
)

@Serializable
data class ChannelVideoEntryDto(
    val id: String,
    val title: String,
    val duration_seconds: Int = 0,
    val thumbnail_url: String? = null,
    val size_bytes: Long = 0L,
    val downloaded_at: Long = 0L,
)

@Serializable
data class MovieEntryDto(
    val id: String,
    val title: String,
    val thumbnail_url: String? = null,
    val duration_seconds: Int = 0,
    val size_bytes: Long = 0L,
    val downloaded_at: Long = 0L,
)
