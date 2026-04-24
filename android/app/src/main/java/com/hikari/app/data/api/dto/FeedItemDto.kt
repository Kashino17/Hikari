package com.hikari.app.data.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class FeedItemDto(
    val videoId: String,
    val title: String,
    val durationSeconds: Int,
    val aspectRatio: String,
    val thumbnailUrl: String,
    val channelId: String,
    val channelTitle: String,
    val category: String,
    val reasoning: String,
    val addedAt: Long,
    val saved: Int,
)
