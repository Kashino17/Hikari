package com.hikari.app.data.api.dto

import kotlinx.serialization.Serializable

/**
 * Ein Beitrag des täglichen News-Briefings (GET news/briefing).
 * Felder exakt nach Backend-Vertrag — publishedAt ist ISO-8601.
 */
@Serializable
data class NewsItemDto(
    val id: String,
    val title: String,
    val summary: String,
    val source: String,
    val url: String,
    val imageUrls: List<String> = emptyList(),
    val videoUrl: String? = null,
    val topic: String,
    val publishedAt: String,
)
