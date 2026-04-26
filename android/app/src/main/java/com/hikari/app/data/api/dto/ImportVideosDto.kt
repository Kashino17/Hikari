package com.hikari.app.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ImportItemMetadata(
    val title: String? = null,
    @SerialName("seriesId")     val seriesId: String? = null,
    @SerialName("seriesTitle")  val seriesTitle: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    @SerialName("dubLanguage")  val dubLanguage: String? = null,
    @SerialName("subLanguage")  val subLanguage: String? = null,
    @SerialName("isMovie")      val isMovie: Boolean? = null,
)

@Serializable
data class BulkImportItem(
    val url: String,
    val metadata: ImportItemMetadata? = null,
)

@Serializable
data class BulkImportRequest(
    val items: List<BulkImportItem>,
)

@Serializable
data class BulkImportResponse(
    val queued: Int,
)

@Serializable
data class AnalyzeRequest(val url: String)

@Serializable
data class AiMeta(
    @SerialName("seriesTitle") val seriesTitle: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    @SerialName("dubLanguage") val dubLanguage: String? = null,
    @SerialName("subLanguage") val subLanguage: String? = null,
    @SerialName("isMovie")     val isMovie: Boolean? = null,
)

@Serializable
data class AnalyzeResponse(
    val url: String,
    val title: String? = null,
    val description: String? = null,
    @SerialName("thumbnailUrl") val thumbnailUrl: String? = null,
    @SerialName("aiMeta")       val aiMeta: AiMeta? = null,
)

@Serializable
data class SeriesItemDto(
    val id: String,
    val title: String,
)
