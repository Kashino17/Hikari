package com.hikari.app.data.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class RecommendationDto(
    val channelId: String,
    val channelUrl: String,
    val title: String,
    val handle: String? = null,
    val description: String? = null,
    val subscribers: Long? = null,
    val thumbnail: String? = null,
    val banner: String? = null,
    val verified: Boolean = false,
    val subscribed: Boolean = false,
    val matchingTags: List<String> = emptyList(),
)
