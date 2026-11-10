package com.hikari.app.domain.music

import com.hikari.app.domain.model.MusicSong
import com.hikari.app.domain.repo.MusicSearchMode
import com.hikari.app.domain.repo.PlaylistWithSongs

enum class OfflineCollectionKind { DOWNLOADS, FAVORITES, PLAYLIST, ARTIST }

/** Eine Auswahl von Songs, die komplett ohne Netz spielbar ist. */
data class OfflineCollection(
    val key: String,
    val kind: OfflineCollectionKind,
    val title: String,
    val songs: List<MusicSong>,
) {
    /** Bis zu vier verschiedene Cover fürs Titelbild. */
    val covers: List<String>
        get() = songs.map { it.thumbnailUrl }.filter { it.isNotBlank() }.distinct().take(4)

    val totalSeconds: Int get() = songs.sumOf { it.duration }
}

/**
 * Die Musikseite ohne Netz. Enthält ausschließlich, was als Datei auf dem
 * Gerät liegt: Eine Playlist erscheint nur mit ihren geladenen Songs — und
 * gar nicht, wenn keiner davon geladen ist. So führt kein Tipp ins Leere.
 */
data class OfflineMusic(
    /** Bereiche (Musik, Hörbücher …), aus denen etwas geladen ist — in fester Reihenfolge. */
    val modes: List<MusicSearchMode> = emptyList(),
    /** Alle geladenen Songs des gewählten Bereichs, zuletzt geladene zuerst. */
    val songs: List<MusicSong> = emptyList(),
    /** Zuletzt gehörte, soweit geladen. */
    val recent: List<MusicSong> = emptyList(),
    val favorites: OfflineCollection? = null,
    val playlists: List<OfflineCollection> = emptyList(),
    /** Interpreten mit ihren geladenen Songs, die mit den meisten zuerst. */
    val artists: List<OfflineCollection> = emptyList(),
) {
    val isEmpty: Boolean get() = songs.isEmpty()

    /** Alles Geladene als eine Sammlung — für „Alle abspielen". */
    val all: OfflineCollection
        get() = OfflineCollection(ALL_KEY, OfflineCollectionKind.DOWNLOADS, "Deine Downloads", songs)

    /** Favoriten und Playlists in der Reihenfolge, in der die Seite sie zeigt. */
    val shelves: List<OfflineCollection> get() = listOfNotNull(favorites) + playlists

    fun collection(key: String?): OfflineCollection? = when (key) {
        null -> null
        ALL_KEY -> all
        else -> (shelves + artists).firstOrNull { it.key == key }
    }

    /** Suche in den Downloads: Songs nach Titel oder Interpret, Sammlungen nach Namen. */
    fun search(query: String): OfflineSearch {
        val q = query.trim()
        if (q.isEmpty()) return OfflineSearch()
        return OfflineSearch(
            songs = songs.filter { it.title.contains(q, ignoreCase = true) || it.uploader.contains(q, ignoreCase = true) },
            collections = (shelves + artists).filter { it.title.contains(q, ignoreCase = true) },
        )
    }

    companion object {
        const val ALL_KEY = "downloads"
        const val FAVORITES_KEY = "favorites"
    }
}

data class OfflineSearch(
    val songs: List<MusicSong> = emptyList(),
    val collections: List<OfflineCollection> = emptyList(),
) {
    val isEmpty: Boolean get() = songs.isEmpty() && collections.isEmpty()
}

object OfflineMusicBuilder {

    private const val RECENT_LIMIT = 10

    /**
     * @param downloaded alle Songs auf dem Gerät ([MusicSong.addedAt] = Zeitpunkt des Downloads)
     * @param history der Hörverlauf, zuletzt Gehörtes zuerst — er kennt auch den Bereich jedes Songs
     * @param mode nur diesen Bereich zeigen; `null` = alles
     */
    fun build(
        downloaded: List<MusicSong>,
        history: List<MusicSong> = emptyList(),
        favorites: List<MusicSong> = emptyList(),
        playlists: List<PlaylistWithSongs> = emptyList(),
        mode: MusicSearchMode? = null,
    ): OfflineMusic {
        // Der Download selbst merkt sich seinen Bereich nicht — der Verlauf schon.
        val modeById = history.associate { it.videoId to MusicSearchMode.fromApiValue(it.listenMode) }
        fun modeOf(song: MusicSong) = modeById[song.videoId] ?: MusicSearchMode.MUSIC

        val allOnDevice = downloaded.map { it.copy(listenMode = modeOf(it).apiValue) }
        val modes = MusicSearchMode.entries.filter { m -> allOnDevice.any { modeOf(it) == m } }
        // Ein Bereich, aus dem nichts (mehr) geladen ist, zählt wie „alles".
        val active = mode?.takeIf { it in modes }

        val songs = allOnDevice
            .filter { active == null || modeOf(it) == active }
            .sortedByDescending { it.addedAt }
        val byId = songs.associateBy { it.videoId }
        // Aus einer fremden Liste nur das übernehmen, was geladen ist — in deren Reihenfolge.
        fun List<MusicSong>.onDevice(): List<MusicSong> = mapNotNull { byId[it.videoId] }

        val favoriteSongs = favorites.onDevice()
        val artists = songs
            .filter { it.uploader.isNotBlank() }
            .groupBy { it.uploader.trim() }
            .map { (name, list) -> OfflineCollection("artist:$name", OfflineCollectionKind.ARTIST, name, list) }
            .sortedWith(compareByDescending<OfflineCollection> { it.songs.size }.thenBy { it.title.lowercase() })

        return OfflineMusic(
            modes = modes,
            songs = songs,
            recent = history.onDevice().take(RECENT_LIMIT),
            favorites = favoriteSongs.takeIf { it.isNotEmpty() }?.let {
                OfflineCollection(OfflineMusic.FAVORITES_KEY, OfflineCollectionKind.FAVORITES, "Favoriten", it)
            },
            playlists = playlists.mapNotNull { entry ->
                entry.songs.onDevice().takeIf { it.isNotEmpty() }?.let {
                    OfflineCollection("playlist:${entry.playlist.id}", OfflineCollectionKind.PLAYLIST, entry.playlist.name, it)
                }
            },
            artists = artists,
        )
    }
}
