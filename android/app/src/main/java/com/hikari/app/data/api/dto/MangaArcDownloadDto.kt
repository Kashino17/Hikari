package com.hikari.app.data.api.dto

import kotlinx.serialization.Serializable

/**
 * Manifest fürs Phone — kommt vom Backend bei GET /api/manga/arcs/{arcId}/manifest.
 * Gibt die komplette Page-Liste eines Arcs zurück, damit der Client iterativ
 * jede Page über /api/manga/page/{pageId} runterladen kann.
 */
@Serializable
data class MangaArcManifestDto(
    val arcId: String,
    val arcOrder: Int,
    val arcTitle: String,
    val seriesId: String,
    val seriesSlug: String,
    val seriesTitle: String,
    val chapters: Int,
    val totalBytes: Long,
    val readyPages: Int,
    val pages: List<MangaArcManifestPageDto>,
)

@Serializable
data class MangaArcManifestPageDto(
    val pageId: String,
    val chapterId: String,
    val chapterNumber: Double,
    val pageNumber: Int,
    val bytes: Long? = null,
    val ready: Boolean,
)
