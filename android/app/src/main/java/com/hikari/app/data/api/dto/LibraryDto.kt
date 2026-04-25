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
    val progress_seconds: Float? = null
)

@Serializable
data class LibraryResponse(
    val series: List<SeriesDto>,
    val recentlyAdded: List<LibraryVideoDto>,
    val channels: List<ChannelDto>
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
