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
    @SerialName("jobId") val jobId: String? = null,
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

@Serializable
data class LanguagesResponse(
    val dub: List<String> = emptyList(),
    val sub: List<String> = emptyList(),
)

/**
 * Ein im In-App-Browser mitgelesener Stream.
 *
 * [pageUrl] ist die Identität des Videos, nicht [mediaUrl]: Die Medien-URL
 * trägt bei praktisch jedem Hoster ein ablaufendes Token und sähe bei jedem
 * Aufruf anders aus — die Duplikatserkennung im Backend würde nie greifen.
 * Referer, Cookie und User-Agent stammen aus den echten Request-Headern der
 * Seite; ohne sie verweigert der Hoster den späteren Serverdownload.
 */
@Serializable
data class SniffedImportItem(
    @SerialName("pageUrl")   val pageUrl: String,
    @SerialName("mediaUrl")  val mediaUrl: String,
    val referer: String? = null,
    val cookie: String? = null,
    @SerialName("userAgent") val userAgent: String? = null,
    val title: String? = null,
    val metadata: ImportItemMetadata? = null,
)

@Serializable
data class SniffedImportRequest(
    val items: List<SniffedImportItem>,
)

@Serializable
data class ImportResultDto(
    val url: String,
    val status: String,
    @SerialName("videoId") val videoId: String? = null,
    val title: String? = null,
    val error: String? = null,
)

/** Fortschritt eines laufenden oder abgeschlossenen Bulk-/Sniff-Imports. */
@Serializable
data class BulkJobStatusDto(
    val id: String,
    val total: Int,
    val ok: Int = 0,
    val duplicate: Int = 0,
    val failed: Int = 0,
    val results: List<ImportResultDto> = emptyList(),
    @SerialName("finishedAt") val finishedAt: Long? = null,
)
