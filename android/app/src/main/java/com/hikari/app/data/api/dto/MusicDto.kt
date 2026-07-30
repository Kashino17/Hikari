package com.hikari.app.data.api.dto

import com.google.gson.annotations.SerializedName

data class PipedSearchResult(
    val url: String,
    val title: String,
    val uploader: String,
    @SerializedName("uploader_url") val uploaderUrl: String,
    val duration: Int,
    val thumbnail: String?,
    val views: Long,
)

data class PipedSearchResponse(
    @SerializedName("items") val results: List<PipedSearchResult>,
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

// Piped suggestions endpoint returns a plain string array: ["lofi hip hop", ...]
data class PipedSuggestionsRawResponse(
    val suggestions: List<String>,
)

data class PipedSuggestion(
    val title: String,
    val url: String,
    val uploader: String,
    @SerializedName("uploader_url") val uploaderUrl: String,
    val duration: Int,
    val thumbnail: String?,
    val views: Long,
)
