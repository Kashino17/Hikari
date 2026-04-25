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
    @SerialName("isRead") val isRead: Int = 0,
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
