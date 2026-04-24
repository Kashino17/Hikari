package com.hikari.app.data.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class ChannelStatsDto(
    val totalVideos: Int,
    val approved: Int,
    val rejected: Int,
    val latestAdded: Long? = null,
    val diskBytes: Long,
)
