package com.hikari.app.data.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class ChannelVideoDto(
    val videoId: String,
    val title: String,
    val thumbnailUrl: String? = null,
    val durationSeconds: Int,
    val publishedAt: Long? = null,
    val discoveredAt: Long? = null,
    val score: Int? = null,
    val category: String? = null,
    val reasoning: String? = null,
    val decision: String? = null,        // "approved" | "rejected" | null (still processing)
    val downloadedBytes: Long? = null,
    val addedToFeedAt: Long? = null,
    val seenAt: Long? = null,
    val saved: Int? = null,
)
