package com.hikari.app.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MangaSeriesDto(
    val id: String,
    val source: String,
    val title: String,
    val author: String? = null,
    val description: String? = null,
    @SerialName("coverPath") val coverPath: String? = null,
    @SerialName("totalChapters") val totalChapters: Int = 0,
    @SerialName("lastSyncedAt") val lastSyncedAt: Long? = null,
)

@Serializable
data class MangaArcDto(
    val id: String,
    val title: String,
    @SerialName("arcOrder") val arcOrder: Int,
    @SerialName("chapterStart") val chapterStart: Int? = null,
    @SerialName("chapterEnd") val chapterEnd: Int? = null,
)

@Serializable
data class MangaChapterDto(
    val id: String,
    val number: Double,
    val title: String? = null,
    @SerialName("arcId") val arcId: String? = null,
    @SerialName("pageCount") val pageCount: Int = 0,
    @SerialName("isAvailable") val isAvailable: Int = 1,
    @SerialName("isRead") val isRead: Int = 0,
    /** Sprache des Kapitels: de oder en. */
    val lang: String? = null,
    /** Wer übersetzt hat (MangaDex: Fan-Gruppe). */
    @SerialName("scanlationGroup") val scanlationGroup: String? = null,
)

@Serializable
data class MangaSeriesDetailDto(
    val id: String,
    val title: String,
    val author: String? = null,
    val description: String? = null,
    @SerialName("coverPath") val coverPath: String? = null,
    @SerialName("totalChapters") val totalChapters: Int = 0,
    val arcs: List<MangaArcDto> = emptyList(),
    val chapters: List<MangaChapterDto> = emptyList(),
    /** Sprachen mit Kapiteln (de, en). */
    val langs: List<String> = emptyList(),
)

@Serializable
data class MangaPageDto(
    val id: String,
    @SerialName("pageNumber") val pageNumber: Int,
    val ready: Boolean,
)

@Serializable
data class MangaContinueDto(
    @SerialName("seriesId") val seriesId: String,
    val title: String,
    @SerialName("coverPath") val coverPath: String? = null,
    @SerialName("chapterId") val chapterId: String,
    @SerialName("pageNumber") val pageNumber: Int,
    @SerialName("updatedAt") val updatedAt: Long,
)

@Serializable
data class MangaSyncJobDto(
    val id: String,
    val source: String,
    val status: String,
    @SerialName("total_chapters") val totalChapters: Int = 0,
    @SerialName("done_chapters") val doneChapters: Int = 0,
    @SerialName("total_pages") val totalPages: Int = 0,
    @SerialName("done_pages") val donePages: Int = 0,
    @SerialName("error_message") val errorMessage: String? = null,
    @SerialName("started_at") val startedAt: Long,
    @SerialName("finished_at") val finishedAt: Long? = null,
)

@Serializable
data class MangaProgressRequest(
    @SerialName("chapterId") val chapterId: String,
    @SerialName("pageNumber") val pageNumber: Int,
)


/** Eine Reihe im Manga-Katalog (MangaDex), noch nicht geöffnet. */
@Serializable
data class MangaCatalogCardDto(
    val id: String,
    val title: String,
    @SerialName("titleDe") val titleDe: String? = null,
    val authors: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val status: String? = null,
    val kind: String = "Manga",
    val langs: List<String> = emptyList(),
    @SerialName("lastChapter") val lastChapter: String? = null,
    @SerialName("coverUrl") val coverUrl: String? = null,
    val source: String = "mangadex",
    /** Lesbare Kapitel je Sprache (vom Mac gezählt). */
    val chapters: MangaChapterCountsDto = MangaChapterCountsDto(),
) {
    /** Deutscher Titel, wenn es einen gibt — ohne langen Zusatz nach „ - “. */
    val displayTitle: String get() = titleDe?.substringBefore(" - ")?.trim()?.takeIf { it.isNotBlank() } ?: title
}

@Serializable
data class MangaCatalogShelfDto(val key: String, val title: String, val subtitle: String = "", val items: List<MangaCatalogCardDto> = emptyList())

@Serializable
data class MangaGenreDto(val name: String, val count: Int = 0)

@Serializable
data class MangaCatalogHomeDto(
    val shelves: List<MangaCatalogShelfDto> = emptyList(),
    val genres: List<MangaGenreDto> = emptyList(),
    val syncing: Boolean = false,
)

@Serializable
data class MangaCatalogListDto(val total: Int = 0, val items: List<MangaCatalogCardDto> = emptyList())

@Serializable
data class MangaOpenDto(@SerialName("seriesId") val seriesId: String)

@Serializable
data class MangaChapterCountsDto(val de: Int = 0, val en: Int = 0)
