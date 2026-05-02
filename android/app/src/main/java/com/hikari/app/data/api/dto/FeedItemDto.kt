package com.hikari.app.data.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class CaptionDto(
    val start: Double,
    val end: Double,
    val text: String,
)

@Serializable
data class FeedItemDto(
    val videoId: String,
    val kind: String = "legacy",
    val parentVideoId: String = "",
    val startSec: Double? = null,
    val endSec: Double? = null,
    val title: String,
    val durationSeconds: Int,
    val aspectRatio: String? = null,
    val thumbnailUrl: String? = null,
    val channelId: String,
    val channelTitle: String,
    val category: String,
    val reasoning: String,
    val overallScore: Int? = null,
    val educationalValue: Int? = null,
    val addedAt: Long,
    val saved: Int,
    val seenAt: Long? = null,  // only present in mode=old responses
    val captions: List<CaptionDto>? = null,
)

@Serializable
data class ProgressBody(val seconds: Float)
