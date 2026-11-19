package com.hikari.app.data.api.dto

import kotlinx.serialization.Serializable

/** Ein Buch in Reihen und Listen (Server: backend/src/api/ebooks.ts, BookCard). */
@Serializable
data class EbookCardDto(
    val id: String,
    val title: String,
    val authors: List<String> = emptyList(),
    val year: Int? = null,
    val format: String = "epub",
    val coverUrl: String? = null,
    val categories: List<String> = emptyList(),
    val source: String = "",
    val wordCount: Int? = null,
    val minutes: Int? = null,
) {
    val isPdf: Boolean get() = format == "pdf"
}

@Serializable
data class EbookRowDto(val category: String, val label: String, val books: List<EbookCardDto> = emptyList())

@Serializable
data class EbookCategoryDto(val id: String, val label: String, val group: String, val count: Int = 0)

@Serializable
data class EbookHomeDto(
    val newest: List<EbookCardDto> = emptyList(),
    val rows: List<EbookRowDto> = emptyList(),
    val categories: List<EbookCategoryDto> = emptyList(),
    val lastSync: Long? = null,
    val syncing: Boolean = false,
)

/** Stand von „Buch holen“ auf dem Mac: loading | ready | hidden. */
@Serializable
data class EbookPrepareDto(val status: String, val error: String? = null, val lastError: String? = null)

@Serializable
data class EbookListDto(val total: Int = 0, val books: List<EbookCardDto> = emptyList())

@Serializable
data class EbookDetailDto(
    val id: String,
    val title: String,
    val authors: List<String> = emptyList(),
    val year: Int? = null,
    val format: String = "epub",
    val coverUrl: String? = null,
    val categories: List<String> = emptyList(),
    val source: String = "",
    val wordCount: Int? = null,
    val minutes: Int? = null,
    val description: String? = null,
    val sourceUrl: String = "",
    val sourceName: String = "",
    val downloaded: Boolean = false,
    val fileBytes: Long? = null,
) {
    val isPdf: Boolean get() = format == "pdf"
    fun toCard() = EbookCardDto(id, title, authors, year, format, coverUrl, categories, source, wordCount, minutes)
}
