package com.hikari.app.data.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class ChannelDto(
    val id: String,
    val url: String,
    val title: String,
    val added_at: Long,
    val is_active: Int,
)

@Serializable
data class AddChannelRequest(val channelUrl: String)

@Serializable
data class AddChannelResponse(
    val id: String,
    val title: String,
    val url: String,
)
