package com.hikari.app.domain.library

import com.hikari.app.data.api.dto.isFilm
import com.hikari.app.data.db.LocalDownloadEntity
import com.hikari.app.data.db.LocalDownloadKind
import com.hikari.app.data.db.PlaybackPositionEntity
import com.hikari.app.domain.model.MusicPlaylist
import com.hikari.app.domain.model.MusicSong
import com.hikari.app.domain.repo.PlaylistWithSongs
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OfflineLibraryBuilderTest {

    private fun episode(
        id: String,
        season: Int,
        episode: Int,
        series: String = "dark",
        downloadedAt: Long = 100L,
        poster: String? = null,
    ) = LocalDownloadEntity(
        videoId = id,
        localFilePath = "/x/$id.mp4",
        byteSize = 1_000,
        downloadedAt = downloadedAt,
        durationSeconds = 3_000,
        kind = LocalDownloadKind.SERIES.name,
        title = "Folge $episode",
        seriesId = series,
        seriesTitle = series.replaceFirstChar { it.uppercase() },
        season = season,
        episode = episode,
        seriesPosterUrl = poster,
    )

    private fun film(id: String, downloadedAt: Long = 100L) = LocalDownloadEntity(
        videoId = id,
        localFilePath = "/x/$id.mp4",
        byteSize = 5_000,
        downloadedAt = downloadedAt,
        durationSeconds = 6_000,
        kind = LocalDownloadKind.MOVIE.name,
        title = id,
    )

    private fun clip(id: String) = LocalDownloadEntity(
        videoId = id,
        localFilePath = "/x/$id.mp4",
        byteSize = 500,
        downloadedAt = 50L,
        durationSeconds = 600,
        kind = LocalDownloadKind.CHANNEL.name,
        title = id,
        channelId = "UC1",
        channelTitle = "Kanal",
    )

    private fun position(id: String, seconds: Int, at: Long) =
        PlaybackPositionEntity(videoId = id, positionMs = seconds * 1000L, updatedAt = at)

    private fun song(id: String, addedAt: Long = 0L, favorite: Boolean = false) = MusicSong(
        videoId = id,
        title = id,
        uploader = "Band",
        uploaderUrl = "",
        thumbnailUrl = "https://img/$id.jpg",
        duration = 200,
        views = 0,
        addedAt = addedAt,
        isFavorite = favorite,
    )

    @Test fun folgen_werden_zu_serien_gebuendelt_und_sortiert() {
        val library = OfflineLibraryBuilder.build(
            downloads = listOf(episode("e3", 1, 3), episode("e1", 1, 1), episode("s2e1", 2, 1), film("matrix"), clip("yt1")),
            positions = emptyList(),
        )
        val dark = library.series.single()
        assertEquals("Dark", dark.title)
        assertEquals(3, dark.episode_count)
        assertEquals(2, dark.season_count)
        assertEquals(listOf("e1", "e3", "s2e1"), library.episodes.getValue("dark").map { it.id })
        assertEquals(listOf("matrix"), library.films.map { it.id })
        assertTrue(library.films.single().isFilm())
        assertEquals(listOf("yt1"), library.channelVideos.map { it.id })
        assertFalse(library.channelVideos.single().isFilm())
        assertEquals(5, library.videoCount)
    }

    @Test fun serienplakat_kommt_von_irgendeiner_folge_die_es_kennt() {
        val library = OfflineLibraryBuilder.build(
            downloads = listOf(episode("e1", 1, 1, downloadedAt = 10, poster = "/covers/poster_dark.jpg"), episode("e2", 1, 2, downloadedAt = 20)),
            positions = emptyList(),
        )
        assertEquals("/covers/poster_dark.jpg", library.series.single().poster_url)
    }

    @Test fun weiterschauen_zeigt_je_serie_eine_folge_und_laufende_filme() {
        val library = OfflineLibraryBuilder.build(
            downloads = listOf(episode("e1", 1, 1), episode("e2", 1, 2), episode("e3", 1, 3), film("matrix"), film("heat")),
            positions = listOf(
                position("e1", 3_000, at = 10), // zu Ende geschaut
                position("e2", 400, at = 20), // mittendrin
                position("matrix", 1_200, at = 30),
                position("heat", 5, at = 40), // nur angetippt — zählt nicht
            ),
        )
        assertEquals(listOf("matrix", "e2"), library.continueWatching.map { it.id })
        assertEquals("matrix", library.hero?.id)
    }

    @Test fun nach_einer_fertigen_folge_geht_es_mit_der_naechsten_weiter() {
        val library = OfflineLibraryBuilder.build(
            downloads = listOf(episode("e1", 1, 1), episode("e2", 1, 2)),
            positions = listOf(position("e1", 2_990, at = 10)),
        )
        assertEquals(listOf("e2"), library.continueWatching.map { it.id })
        assertEquals("e2", library.nextEpisode("dark")?.id)
    }

    @Test fun eine_durchgeschaute_serie_steht_nicht_mehr_unter_weiterschauen() {
        val library = OfflineLibraryBuilder.build(
            downloads = listOf(episode("e1", 1, 1)),
            positions = listOf(position("e1", 3_000, at = 10)),
        )
        assertTrue(library.continueWatching.isEmpty())
    }

    @Test fun ohne_fortschritt_zeigt_der_kopf_den_neuesten_download() {
        val library = OfflineLibraryBuilder.build(
            downloads = listOf(film("alt", downloadedAt = 10), episode("e2", 1, 2, downloadedAt = 99), episode("e1", 1, 1, downloadedAt = 98)),
            positions = emptyList(),
        )
        // Neuester Download ist Folge 2 — angeboten wird aber der Anfang der Serie.
        assertEquals("e1", library.hero?.id)
    }

    @Test fun serienseite_enthaelt_nur_die_folgen_auf_dem_geraet() {
        val library = OfflineLibraryBuilder.build(listOf(episode("e1", 1, 1), episode("e2", 1, 2)), emptyList())
        assertEquals(listOf("e1", "e2"), library.seriesDetail("dark")?.videos?.map { it.id })
        assertNull(library.seriesDetail("gibt-es-nicht"))
    }

    @Test fun playlists_enthalten_nur_geladene_songs() {
        val onDevice = listOf(song("a", addedAt = 1), song("b", addedAt = 2, favorite = true))
        val library = OfflineLibraryBuilder.build(
            downloads = emptyList(),
            positions = emptyList(),
            downloadedSongs = onDevice,
            favorites = listOf(song("b", favorite = true), song("c", favorite = true)),
            playlists = listOf(
                PlaylistWithSongs(MusicPlaylist(id = 7, name = "Sport"), listOf(song("c"), song("a")), downloadedCount = 1),
                PlaylistWithSongs(MusicPlaylist(id = 8, name = "Nur online"), listOf(song("c")), downloadedCount = 0),
                PlaylistWithSongs(MusicPlaylist(id = 9, name = "Leer"), emptyList(), downloadedCount = 0),
            ),
        )
        // Playlists ohne einen geladenen Song fehlen ganz.
        assertEquals(listOf("Favoriten", "Alle Downloads", "Sport"), library.playlists.map { it.name })
        val favorites = library.playlists[0]
        assertEquals(OfflinePlaylistKind.FAVORITES, favorites.kind)
        assertEquals(listOf("b"), favorites.songs.map { it.videoId })
        assertEquals(listOf("a"), library.playlists[2].songs.map { it.videoId })
        // Zuletzt geladene Songs zuerst.
        assertEquals(listOf("b", "a"), library.songs.map { it.videoId })
        assertEquals(listOf("b", "a"), library.playlists[1].songs.map { it.videoId })
    }

    @Test fun ohne_geladene_favoriten_gibt_es_keine_favoriten_playlist() {
        val library = OfflineLibraryBuilder.build(
            downloads = emptyList(),
            positions = emptyList(),
            downloadedSongs = listOf(song("a")),
            favorites = listOf(song("x", favorite = true)),
        )
        assertEquals(listOf("Alle Downloads"), library.playlists.map { it.name })
    }

    @Test fun leer_ist_leer() {
        assertTrue(OfflineLibraryBuilder.build(emptyList(), emptyList()).isEmpty)
        assertNull(OfflineLibraryBuilder.build(emptyList(), emptyList()).hero)
    }
}
