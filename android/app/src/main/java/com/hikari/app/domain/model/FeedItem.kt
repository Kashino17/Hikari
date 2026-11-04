package com.hikari.app.domain.model

data class Caption(val startMs: Long, val endMs: Long, val text: String)

data class FeedItem(
    val videoId: String,
    val title: String,
    val durationSeconds: Int,
    val aspectRatio: String?,
    val thumbnailUrl: String?,
    val channelTitle: String,
    val category: String,
    val reasoning: String,
    val saved: Boolean,
    val overallScore: Int? = null,
    val educationalValue: Int? = null,
    val captions: List<Caption>? = null,
    val context: String? = null,
    val summary: String? = null,
    val channelId: String = "",
    val publishedAt: Long? = null,
)
