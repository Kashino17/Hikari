package com.hikari.app.data.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class VideoFullDto(
    val title: String,
    val durationSec: Double,
    val thumbnailUrl: String?,
    val channelTitle: String,
    val fileUrl: String,
)
