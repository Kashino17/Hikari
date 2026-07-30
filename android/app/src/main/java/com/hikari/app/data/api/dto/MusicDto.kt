package com.hikari.app.data.api.dto

import com.google.gson.annotations.SerializedName

data class PipedSearchResult(
    val url: String,
    val title: String,
    val uploader: String,
    @SerializedName("uploader_url") val uploaderUrl: String,
    val duration: Int,
    @SerializedName("thumbnail") val thumbnail: String,
    @SerializedName("views") val views: Int,
)

data class PipedSearchResponse(
    val results: List<PipedSearchResult>,
)

data class PipedStream(
    val url: String,
    @SerializedName("quality") val quality: String,
    @SerializedName("quality_description") val qualityDesc: String,
    val mimeType: String,
    val bitrate: Long,
    val fileSize: Long,
)

data class PipedStreamResponse(
    val streams: List<PipedStream>,
)

data class PipedSuggestion(
    val title: String,
    val url: String,
    val uploader: String,
    @SerializedName("uploader_url") val uploaderUrl: String,
    val duration: Int,
    @SerializedName("thumbnail") val thumbnail: String,
    @SerializedName("views") val views: Int,
)

data class PipedSuggestionResponse(
    val suggestions: List<PipedSuggestion>,
)
