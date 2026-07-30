package com.hikari.app.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PipedSearchResult(
    val url: String,
    val title: String,
    val uploader: String,
    @SerialName("uploader_url") val uploaderUrl: String,
    val duration: Int,
    @SerialName("thumbnail") val thumbnail: String,
    @SerialName("views") val views: Int,
)

@Serializable
data class PipedSearchResponse(
    val results: List<PipedSearchResult>,
)

@Serializable
data class PipedStream(
    val url: String,
    @SerialName("quality") val quality: String,
    @SerialName("quality_description") val qualityDesc: String,
    val mimeType: String,
    val bitrate: Long,
    val fileSize: Long,
)

@Serializable
data class PipedStreamResponse(
    val streams: List<PipedStream>,
)

@Serializable
data class PipedSuggestion(
    val title: String,
    val url: String,
    val uploader: String,
    @SerialName("uploader_url") val uploaderUrl: String,
    val duration: Int,
    @SerialName("thumbnail") val thumbnail: String,
    @SerialName("views") val views: Int,
)

@Serializable
data class PipedSuggestionResponse(
    val suggestions: List<PipedSuggestion>,
)
