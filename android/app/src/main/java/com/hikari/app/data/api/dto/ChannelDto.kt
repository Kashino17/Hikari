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
    val thumbnail_url: String? = null,
    val banner_url: String? = null,
    val autoApprove: Int = 0,
)

@Serializable
data class SetAutoApproveRequest(val autoApprove: Boolean)

@Serializable
data class SetAutoApproveResponse(val id: String, val autoApprove: Boolean)

@Serializable
data class AddChannelRequest(val channelUrl: String)

@Serializable
data class AddChannelResponse(
    val id: String,
    val title: String,
    val url: String,
    val handle: String? = null,
    val thumbnail_url: String? = null,
    val banner_url: String? = null,
    val subscribers: Long? = null,
)
