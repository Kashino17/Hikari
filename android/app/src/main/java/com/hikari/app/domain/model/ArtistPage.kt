package com.hikari.app.domain.model

/**
 * Album oder Single eines Künstlers. [playlistId] kann eine MPREb-Browse-Id
 * sein — der Playlist-Endpunkt des Backends löst beide Formen auf.
 */
data class ArtistAlbum(
    val playlistId: String,
    val name: String,
    val artistName: String,
    val thumbnailUrl: String,
    val videoCount: Int,
    val year: Int?,
)

/** Komplette Artist-Seite: Profil plus alle Inhalts-Sektionen in einem Stück. */
data class ArtistPage(
    val artist: Artist,
    val topSongs: List<MusicSong>,
    /** Neueste Uploads — bei normalen Kanälen gefüllt, bei Music-Artists leer. */
    val latest: List<MusicSong> = emptyList(),
    val albums: List<ArtistAlbum>,
    val singles: List<ArtistAlbum>,
    val playlists: List<ArtistPlaylist>,
    val related: List<SearchArtist>,
)

/** Ein Eintrag einer Home-Feed-Sektion — genau eine der vier Ausprägungen. */
sealed interface HomeItem {
    data class SongItem(val song: MusicSong) : HomeItem
    data class PlaylistItem(val playlist: RemotePlaylist) : HomeItem
    data class AlbumItem(val album: MusicAlbum) : HomeItem
    data class ArtistItem(val artist: SearchArtist) : HomeItem
}

/** Woraus eine Home-Sektion entstand — steuert Darstellung und Aktionen. */
enum class HomeSectionKind {
    /** Radio-Mix aus den Hör-Seeds des Nutzers. */
    MIX,

    /** "Ähnlich wie X" — Related-Songs eines einzelnen Seeds. */
    SIMILAR,

    /** Sektion aus dem YouTube-Music-Home-Feed (gemischte Item-Typen). */
    BACKEND,

    /** Zufällige Auswahl aus den lokalen Favoriten. */
    FAVORITES,

    /** Kuratierter Fallback-Mix mit Suchbegriff (offline/Fehler/Instrumental). */
    CURATED,
}

/**
 * Sektion des personalisierten Home-Feeds. Song-Sektionen füllen [songs]
 * (abspielbar als Queue), Backend-Sektionen füllen [items]. [query] trägt bei
 * kuratierten Sektionen den Suchbegriff für die Detail-Seite.
 */
data class HomeSection(
    val title: String,
    val kind: HomeSectionKind,
    val songs: List<MusicSong> = emptyList(),
    val items: List<HomeItem> = emptyList(),
    val query: String = "",
)
