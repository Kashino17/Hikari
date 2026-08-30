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

/**
 * Ein Import, der gerade läuft.
 *
 * Lebt im Backend bewusst getrennt von den fertigen Videos: Erst wenn die
 * Datei vollständig auf der Platte liegt, wandert der Eintrag in die
 * Bibliothek. Bis dahin sind Titel, Serie und Sprache hier frei änderbar —
 * die Werte gewinnen beim Abschluss.
 */
@Serializable
data class PendingImportDto(
    val id: String,
    @SerialName("pageUrl") val pageUrl: String,
    val title: String? = null,
    @SerialName("seriesId") val seriesId: String? = null,
    @SerialName("seriesTitle") val seriesTitle: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    @SerialName("dubLanguage") val dubLanguage: String? = null,
    @SerialName("subLanguage") val subLanguage: String? = null,
    @SerialName("isMovie") val isMovie: Boolean = false,
    /** "queued" | "downloading" | "failed" */
    val status: String,
    // Bewusst Double statt Long/Int: yt-dlp liefert Groesse, Tempo und Restzeit
    // als Fliesskommazahlen. Der Server rundet sie inzwischen, aber ein
    // ganzzahliges Feld liesse die gesamte Antwort scheitern, sobald doch
    // einmal ein Komma durchkommt — und mit ihr die ganze Downloadliste.
    @SerialName("downloadedBytes") val downloadedBytes: Double = 0.0,
    @SerialName("totalBytes") val totalBytes: Double? = null,
    @SerialName("speedBps") val speedBps: Double? = null,
    @SerialName("etaSeconds") val etaSeconds: Double? = null,
    @SerialName("fragmentIndex") val fragmentIndex: Int? = null,
    @SerialName("fragmentCount") val fragmentCount: Int? = null,
    /** 0…1, oder null solange sich der Anteil nicht bestimmen lässt. */
    val progress: Float? = null,
    val error: String? = null,
    /** Vorschaubild, oft als relativer Pfad ("/covers/vid_…jpg") — auflösen übernimmt der ImageLoader. */
    @SerialName("thumbnailUrl") val thumbnailUrl: String? = null,
    @SerialName("startedAt") val startedAt: Long = 0,
)

@Serializable
data class PendingImportsResponse(
    val items: List<PendingImportDto> = emptyList(),
)

/** Teil-Update: Nicht gesetzte Felder bleiben unverändert. */
@Serializable
data class PendingImportPatch(
    val title: String? = null,
    @SerialName("seriesTitle") val seriesTitle: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    @SerialName("dubLanguage") val dubLanguage: String? = null,
    @SerialName("subLanguage") val subLanguage: String? = null,
    @SerialName("isMovie") val isMovie: Boolean? = null,
)
