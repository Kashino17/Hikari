package com.hikari.app.domain.music

import com.hikari.app.domain.model.MusicPlaylist
import com.hikari.app.domain.model.MusicSong
import com.hikari.app.domain.repo.MusicSearchMode
import com.hikari.app.domain.repo.PlaylistWithSongs
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OfflineMusicBuilderTest {

    private fun song(id: String, artist: String = "Band", at: Long = 0L, mode: String? = null) = MusicSong(
        videoId = id,
        title = "Titel $id",
        uploader = artist,
        uploaderUrl = "",
        thumbnailUrl = "https://img/$id.jpg",
        duration = 200,
        views = 0,
        addedAt = at,
        listenMode = mode,
    )

    private fun playlist(id: Int, name: String, vararg songs: MusicSong) =
        PlaylistWithSongs(MusicPlaylist(id = id, name = name), songs.toList(), downloadedCount = 0)

    @Test fun zeigt_nur_was_auf_dem_geraet_liegt() {
        val music = OfflineMusicBuilder.build(
            downloaded = listOf(song("a", at = 1), song("b", at = 2)),
            history = listOf(song("x"), song("a"), song("b")),
            favorites = listOf(song("b"), song("x")),
            playlists = listOf(
                playlist(1, "Gym", song("x"), song("a")),
                playlist(2, "Nur online", song("x"), song("y")),
            ),
        )
        // Zuletzt geladene zuerst.
        assertEquals(listOf("b", "a"), music.songs.map { it.videoId })
        assertEquals(listOf("a", "b"), music.recent.map { it.videoId })
        assertEquals(listOf("b"), music.favorites?.songs?.map { it.videoId })
        // Die Playlist ohne einen geladenen Song fehlt ganz.
        assertEquals(listOf("Gym"), music.playlists.map { it.title })
        assertEquals(listOf("a"), music.playlists.single().songs.map { it.videoId })
        assertEquals(listOf("Favoriten", "Gym"), music.shelves.map { it.title })
    }

    @Test fun ohne_geladene_favoriten_gibt_es_keine_favoriten_sammlung() {
        val music = OfflineMusicBuilder.build(downloaded = listOf(song("a")), favorites = listOf(song("x")))
        assertNull(music.favorites)
    }

    @Test fun interpreten_mit_den_meisten_songs_stehen_vorn() {
        val music = OfflineMusicBuilder.build(
            downloaded = listOf(song("a", "Zeta"), song("b", "Alpha"), song("c", "Zeta"), song("d", "")),
        )
        assertEquals(listOf("Zeta", "Alpha"), music.artists.map { it.title })
        assertEquals(2, music.artists.first().songs.size)
    }

    @Test fun bereiche_kommen_aus_dem_verlauf_und_filtern_alles() {
        val podcast = MusicSearchMode.PODCAST
        val downloaded = listOf(song("lied", at = 1), song("folge", "Show", at = 2))
        val history = listOf(song("folge", mode = podcast.apiValue), song("lied", mode = MusicSearchMode.MUSIC.apiValue))

        val all = OfflineMusicBuilder.build(downloaded, history)
        assertEquals(listOf(MusicSearchMode.MUSIC, podcast), all.modes)
        assertEquals(2, all.songs.size)
        assertEquals(podcast.apiValue, all.songs.first { it.videoId == "folge" }.listenMode)

        val onlyPodcast = OfflineMusicBuilder.build(
            downloaded, history,
            favorites = listOf(song("lied")),
            playlists = listOf(playlist(1, "Mix", song("lied"), song("folge"))),
            mode = podcast,
        )
        assertEquals(listOf("folge"), onlyPodcast.songs.map { it.videoId })
        assertEquals(listOf("folge"), onlyPodcast.recent.map { it.videoId })
        assertNull(onlyPodcast.favorites)
        assertEquals(listOf("folge"), onlyPodcast.playlists.single().songs.map { it.videoId })
        // Die Auswahl der Bereiche bleibt vollständig, sonst käme man nicht zurück.
        assertEquals(listOf(MusicSearchMode.MUSIC, podcast), onlyPodcast.modes)
    }

    @Test fun ein_bereich_ohne_downloads_zaehlt_wie_alles() {
        val music = OfflineMusicBuilder.build(listOf(song("a")), mode = MusicSearchMode.AUDIOBOOK)
        assertEquals(listOf("a"), music.songs.map { it.videoId })
    }

    @Test fun suche_findet_titel_interpreten_und_sammlungen() {
        val music = OfflineMusicBuilder.build(
            downloaded = listOf(song("a", "Marino"), song("b", "Lil Peep")),
            playlists = listOf(playlist(1, "Marino Mix", song("b"))),
        )
        val hit = music.search(" marino ")
        assertEquals(listOf("a"), hit.songs.map { it.videoId })
        assertEquals(setOf("Marino Mix", "Marino"), hit.collections.map { it.title }.toSet())
        assertTrue(music.search("gibtsnicht").isEmpty)
        assertTrue(music.search("  ").isEmpty)
    }

    @Test fun sammlungen_lassen_sich_ueber_ihren_schluessel_finden() {
        val music = OfflineMusicBuilder.build(
            downloaded = listOf(song("a", "Marino")),
            favorites = listOf(song("a")),
            playlists = listOf(playlist(7, "Gym", song("a"))),
        )
        assertEquals("Deine Downloads", music.collection(OfflineMusic.ALL_KEY)?.title)
        assertEquals("Favoriten", music.collection(OfflineMusic.FAVORITES_KEY)?.title)
        assertEquals("Gym", music.collection("playlist:7")?.title)
        assertEquals("Marino", music.collection("artist:Marino")?.title)
        assertNull(music.collection("playlist:99"))
        assertNull(music.collection(null))
    }

    @Test fun leer_ist_leer() {
        assertTrue(OfflineMusicBuilder.build(emptyList()).isEmpty)
    }
}
