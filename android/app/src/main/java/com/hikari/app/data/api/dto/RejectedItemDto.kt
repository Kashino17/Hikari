package com.hikari.app.data.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class RejectedItemDto(
    val videoId: String,
    val title: String,
    val channelId: String,
    val channelTitle: String? = null,
    val durationSeconds: Int,
    val thumbnailUrl: String,
    val overallScore: Int,
    val category: String,
    val reasoning: String,
    val clickbaitRisk: Int,
    val emotionalManipulation: Int,
)
