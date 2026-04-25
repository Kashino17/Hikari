package com.hikari.app.data.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class FeedItemDto(
    val videoId: String,
    val title: String,
    val durationSeconds: Int,
    val aspectRatio: String? = null,
    val thumbnailUrl: String? = null,
    val channelId: String,
    val channelTitle: String,
    val category: String,
    val reasoning: String,
    val addedAt: Long,
    val saved: Int,
    val seenAt: Long? = null,  // only present in mode=old responses
)
