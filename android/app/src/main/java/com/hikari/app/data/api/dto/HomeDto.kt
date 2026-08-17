package com.hikari.app.data.api.dto

import kotlinx.serialization.Serializable

// --- YouTube-Music-Feed & Artist-Seite (/music/home, /music/artist/{id}/page) ---

/**
 * Ein Eintrag im Home-Feed: [kind] sagt, welches der vier optionalen Felder
 * belegt ist ("song", "playlist", "album", "artist") — gleiche Union-Technik
 * wie [TopResultDto].
 */
@Serializable
data class HomeItemDto(
    val kind: String,
    val song: MusicTrackDto? = null,
    val playlist: SearchPlaylistDto? = null,
    val album: SearchAlbumDto? = null,
    val artist: SearchArtistDto? = null,
)

@Serializable
data class HomeSectionDto(
    val title: String = "",
    val items: List<HomeItemDto> = emptyList(),
)

@Serializable
data class HomeFeedDto(
    val sections: List<HomeSectionDto> = emptyList(),
)

/**
 * Album/Single auf der Artist-Seite. [playlistId] kann eine MPREb-Browse-Id
 * sein — /music/playlist/{id} löst beide Formen auf.
 */
@Serializable
data class ArtistAlbumDto(
    val playlistId: String,
    val name: String = "",
    val artistName: String = "",
    val thumbnailUrl: String = "",
    val videoCount: Int = 0,
    val year: Int? = null,
    val browseId: String? = null,
)

/** Komplette Artist-Seite in einem Call (/music/artist/{channelId}/page). */
@Serializable
data class ArtistPageDto(
    val artist: ArtistDto,
    val topSongs: List<MusicTrackDto> = emptyList(),
    /** Neueste Uploads — nur bei normalen Kanälen gefüllt, Music-Artists leer. */
    val latest: List<MusicTrackDto> = emptyList(),
    val albums: List<ArtistAlbumDto> = emptyList(),
    val singles: List<ArtistAlbumDto> = emptyList(),
    val playlists: List<ArtistPlaylistDto> = emptyList(),
    val related: List<SearchArtistDto> = emptyList(),
)
