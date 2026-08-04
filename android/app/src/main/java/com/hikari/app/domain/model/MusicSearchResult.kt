package com.hikari.app.domain.model

/** Künstler-Treffer der Musik-Suche. */
data class SearchArtist(
    val channelId: String,
    val name: String,
    val thumbnailUrl: String,
    val subscribers: Long,
)

/** Album-Treffer der Musik-Suche — technisch eine Remote-Playlist. */
data class MusicAlbum(
    val playlistId: String,
    val name: String,
    val artistName: String,
    val thumbnailUrl: String,
    val videoCount: Int,
)

/** Playlist-Treffer der Musik-Suche (nicht lokal, daher ohne DB-Id). */
data class RemotePlaylist(
    val playlistId: String,
    val name: String,
    val uploaderName: String,
    val thumbnailUrl: String,
    val videoCount: Int,
)

/** Top-Ergebnis der Vollsuche — genau einer der vier Treffertypen. */
sealed interface MusicSearchResult {
    data class Song(val song: MusicSong) : MusicSearchResult
    data class Artist(val artist: SearchArtist) : MusicSearchResult
    data class Album(val album: MusicAlbum) : MusicSearchResult
    data class Playlist(val playlist: RemotePlaylist) : MusicSearchResult
}

/** Ergebnis der Vollsuche (`/music/search/full`). */
data class FullSearchResults(
    val topResult: MusicSearchResult?,
    val songs: List<MusicSong>,
    val artists: List<SearchArtist>,
    val albums: List<MusicAlbum>,
    val playlists: List<RemotePlaylist>,
)
