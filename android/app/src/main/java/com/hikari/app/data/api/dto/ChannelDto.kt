package com.hikari.app.data.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class ChannelDto(
    val id: String,
    val url: String,
    val title: String,
    val added_at: Long,
    val is_active: Int,
    val last_polled_at: Long? = null,
    val handle: String? = null,
    val description: String? = null,
    val subscribers: Long? = null,
    val thumbnail: String? = null,
)

@Serializable
data class AddChannelRequest(val channelUrl: String)

@Serializable
data class AddChannelResponse(
    val id: String,
    val title: String,
    val url: String,
    val handle: String? = null,
    val thumbnail: String? = null,
    val subscribers: Long? = null,
)
