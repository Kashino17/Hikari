package com.hikari.app.data.api.dto

import kotlinx.serialization.Serializable

/** Künstler-Profil vom Hikari-Backend (/music/artist/{channelId}). */
@Serializable
data class ArtistDto(
    val channelId: String,
    val name: String = "",
    val avatarUrl: String? = null,
    val bannerUrl: String? = null,
    val subscriberCount: Long = 0,
    val description: String = "",
    val verified: Boolean = false,
)
