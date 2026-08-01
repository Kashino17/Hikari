package com.hikari.app.domain.model

/** Ein Beitrag des täglichen News-Tagesberichts. */
data class NewsItem(
    val id: String,
    val title: String,
    val summary: String,
    val source: String,
    val url: String,
    val imageUrls: List<String>,
    val videoUrl: String?,
    val topic: String,
    /** ISO-8601-Zeitstempel, wie ihn das Backend liefert. */
    val publishedAt: String,
)
