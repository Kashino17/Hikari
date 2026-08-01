package com.hikari.app.data.api.dto

import kotlinx.serialization.Serializable

/** Playlist eines Künstlers (/music/artist/{channelId}/playlists). */
@Serializable
data class ArtistPlaylistDto(
    val playlistId: String,
    val name: String = "",
    val thumbnailUrl: String = "",
    val videoCount: Int = 0,
    val uploaderName: String = "",
)
