package com.hikari.app.domain.repo

import com.hikari.app.data.api.HikariApi
import com.hikari.app.data.api.dto.MusicTrackDto
import com.hikari.app.data.api.dto.PipedSearchPageDto
import com.hikari.app.data.api.dto.PipedStreamsDto
import com.hikari.app.data.db.LocalMusicDownloadDao
import com.hikari.app.data.db.MusicPlaylistDao
import com.hikari.app.data.db.MusicPlaylistEntity
import com.hikari.app.data.db.MusicPlaylistSongDao
import com.hikari.app.data.db.MusicPlaylistSongEntity
import com.hikari.app.data.db.MusicSongDao
import com.hikari.app.data.db.MusicSongEntity
import com.hikari.app.domain.model.MusicPlaylist
import com.hikari.app.domain.model.MusicSong
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

data class DiscoverSection(val title: String, val songs: List<MusicSong>)

/** Playlist samt Songs und wie viele davon offline verfügbar sind. */
data class PlaylistWithSongs(
    val playlist: MusicPlaylist,
    val songs: List<MusicSong>,
    val downloadedCount: Int,
)

/**
 * Search + streaming go through the Hikari backend (yt-dlp — the same
 * extraction the clipper uses, so it works even when public Piped instances
 * are blocked). If the backend is unreachable the repo falls back to querying
 * Piped instances directly from the device.
 */
class MusicRepository(
    private val songDao: MusicSongDao,
    private val playlistDao: MusicPlaylistDao,
    private val playlistSongDao: MusicPlaylistSongDao,
    private val downloadDao: LocalMusicDownloadDao,
    private val api: HikariApi,
    private val fallbackClient: OkHttpClient,
    private val json: Json,
) {
    companion object {
        private val PIPED_INSTANCES = listOf(
            "https://api.piped.private.coffee",
            "https://pipedapi.kavin.rocks",
            "https://pipedapi.reallyaweso.me",
        )
        private val DISCOVER_SECTIONS = listOf(
            "Top Hits" to "top hits 2026",
            "Lofi & Study" to "lofi hip hop beats",
            "Chill Pop" to "chill pop playlist",
            "Hip-Hop" to "hip hop hits",
            "Anime & Gaming" to "anime opening songs",
        )
    }

    suspend fun searchMusic(query: String): List<MusicSong> {
        val tracks = try {
            api.searchMusic(query).map { it.toSong() }
        } catch (_: Exception) {
            pipedSearchFallback(query)
        }
        return withFavoriteState(tracks)
    }

    suspend fun getDiscoverSections(): List<DiscoverSection> = coroutineScope {
        DISCOVER_SECTIONS.map { (title, query) ->
            async {
                val songs = try {
                    searchMusic(query).take(10)
                } catch (_: Exception) {
                    emptyList()
                }
                DiscoverSection(title, songs)
            }
        }.map { it.await() }.filter { it.songs.isNotEmpty() }
    }

    suspend fun getAudioStream(videoId: String): String? {
        try {
            api.getMusicStream(videoId).url?.let { return it }
        } catch (_: Exception) {
            // backend down or extraction failed — try Piped directly
        }
        return pipedStreamFallback(videoId)
    }

    // --- Library (= play history) & favorites ---

    suspend fun getHistory(): List<MusicSong> = songDao.getAll().map { it.toSong() }

    suspend fun getFavorites(): List<MusicSong> = songDao.getFavorites().map { it.toSong() }

    /** Alle offline verfügbaren Songs — funktioniert ohne jedes Netz. */
    suspend fun getDownloadedSongs(): List<MusicSong> {
        val favorites = getFavoriteIds()
        return downloadDao.getAll().map { row ->
            MusicSong(
                videoId = row.videoId,
                title = row.title,
                uploader = row.uploader,
                uploaderUrl = "",
                thumbnailUrl = row.thumbnailUrl,
                duration = row.durationSeconds,
                views = 0,
                addedAt = row.downloadedAt,
                isFavorite = row.videoId in favorites,
            )
        }
    }

    fun observeDownloadedIds(): Flow<List<String>> = downloadDao.observeIds()

    /**
     * Merkt einen Song in der Bibliothek. [touchRecency] steuert, ob er im
     * Verlauf nach oben rutscht — beim Download soll er das nicht.
     */
    suspend fun recordPlayed(song: MusicSong, touchRecency: Boolean = true) {
        val existing = songDao.getByName(song.videoId)
        songDao.insert(
            song.toEntity().copy(
                isFavorite = existing?.isFavorite ?: song.isFavorite,
                addedAt = if (touchRecency) {
                    System.currentTimeMillis()
                } else {
                    existing?.addedAt ?: song.addedAt
                },
            ),
        )
    }

    /** Returns the new favorite state. Unknown songs get saved first. */
    suspend fun toggleFavorite(song: MusicSong): Boolean {
        val existing = songDao.getByName(song.videoId)
        return if (existing == null) {
            songDao.insert(song.toEntity().copy(isFavorite = true))
            true
        } else {
            val next = !existing.isFavorite
            songDao.setFavorite(song.videoId, next)
            next
        }
    }

    suspend fun removeSong(song: MusicSong) = songDao.delete(song.toEntity())

    suspend fun getFavoriteIds(): Set<String> = songDao.getFavorites().map { it.videoId }.toSet()

    // --- Playlists ---

    suspend fun getPlaylists(): List<PlaylistWithSongs> {
        val downloadedIds = downloadDao.getAll().map { it.videoId }.toSet()
        val favorites = getFavoriteIds()
        return playlistDao.getAll().map { entity ->
            val songs = songsOf(entity.id, favorites)
            PlaylistWithSongs(
                playlist = entity.toModel(),
                songs = songs,
                downloadedCount = songs.count { it.videoId in downloadedIds },
            )
        }
    }

    suspend fun getPlaylist(id: Int): PlaylistWithSongs? {
        val entity = playlistDao.getById(id) ?: return null
        val downloadedIds = downloadDao.getAll().map { it.videoId }.toSet()
        val songs = songsOf(id, getFavoriteIds())
        return PlaylistWithSongs(
            playlist = entity.toModel(),
            songs = songs,
            downloadedCount = songs.count { it.videoId in downloadedIds },
        )
    }

    private suspend fun songsOf(playlistId: Int, favorites: Set<String>): List<MusicSong> {
        val links = playlistSongDao.getByPlaylist(playlistId)
        if (links.isEmpty()) return emptyList()
        val byId = songDao.getAll().associateBy { it.videoId }
        return links.mapNotNull { link ->
            byId[link.songVideoId]?.toSong()?.copy(isFavorite = link.songVideoId in favorites)
        }
    }

    suspend fun createPlaylist(name: String): Int {
        val id = playlistDao.insert(MusicPlaylistEntity(name = name))
        return id.toInt()
    }

    suspend fun renamePlaylist(playlist: MusicPlaylist, newName: String) {
        playlistDao.getById(playlist.id)?.let { playlistDao.update(it.copy(name = newName)) }
    }

    suspend fun deletePlaylist(playlist: MusicPlaylist) {
        playlistDao.getById(playlist.id)?.let { playlistDao.delete(it) }
    }

    /** Song muss in `music_songs` existieren — der Fremdschlüssel verlangt das. */
    suspend fun addToPlaylist(playlistId: Int, song: MusicSong) {
        recordPlayed(song, touchRecency = false)
        playlistSongDao.insert(MusicPlaylistSongEntity(playlistId, song.videoId))
    }

    suspend fun removeFromPlaylist(playlistId: Int, song: MusicSong) {
        playlistSongDao.delete(MusicPlaylistSongEntity(playlistId, song.videoId))
    }

    // --- Fallback path (direct Piped) ---

    private suspend fun pipedSearchFallback(query: String): List<MusicSong> =
        withContext(Dispatchers.IO) {
            val q = URLEncoder.encode(query, "UTF-8")
            for (base in PIPED_INSTANCES) {
                try {
                    val body = httpGet("$base/search?q=$q&filter=music_songs") ?: continue
                    val page = json.decodeFromString<PipedSearchPageDto>(body)
                    val songs = page.items.mapNotNull { item ->
                        val videoId = item.url?.substringAfter("v=", "")?.substringBefore("&")
                        if (videoId.isNullOrBlank()) return@mapNotNull null
                        MusicSong(
                            videoId = videoId,
                            title = item.title.orEmpty(),
                            uploader = item.uploaderName.orEmpty(),
                            uploaderUrl = "",
                            thumbnailUrl = "https://i.ytimg.com/vi/$videoId/mqdefault.jpg",
                            duration = item.duration ?: 0,
                            views = 0,
                        )
                    }
                    if (songs.isNotEmpty()) return@withContext songs
                } catch (_: Exception) {
                    // dead instance — try the next one
                }
            }
            emptyList()
        }

    private suspend fun pipedStreamFallback(videoId: String): String? =
        withContext(Dispatchers.IO) {
            for (base in PIPED_INSTANCES) {
                try {
                    val body = httpGet("$base/streams/$videoId") ?: continue
                    val streams = json.decodeFromString<PipedStreamsDto>(body)
                    val best = streams.audioStreams
                        .filter { it.url != null }
                        .maxByOrNull { it.bitrate ?: 0L }
                    if (best?.url != null) return@withContext best.url
                } catch (_: Exception) {
                    // dead instance — try the next one
                }
            }
            null
        }

    private fun httpGet(url: String): String? {
        val request = Request.Builder().url(url).build()
        fallbackClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body?.string()
        }
    }

    private suspend fun withFavoriteState(songs: List<MusicSong>): List<MusicSong> {
        if (songs.isEmpty()) return songs
        val favorites = getFavoriteIds()
        return songs.map { it.copy(isFavorite = it.videoId in favorites) }
    }

    private fun MusicTrackDto.toSong() = MusicSong(
        videoId = videoId,
        title = title,
        uploader = uploader,
        uploaderUrl = "",
        thumbnailUrl = thumbnailUrl,
        duration = durationSeconds,
        views = 0,
    )

    private fun MusicSong.toEntity() = MusicSongEntity(
        videoId = videoId, title = title, uploader = uploader, uploaderUrl = uploaderUrl,
        thumbnailUrl = thumbnailUrl, duration = duration, views = views,
        addedAt = addedAt, isFavorite = isFavorite,
    )

    private fun MusicSongEntity.toSong() = MusicSong(
        videoId = videoId, title = title, uploader = uploader, uploaderUrl = uploaderUrl,
        thumbnailUrl = thumbnailUrl, duration = duration, views = views,
        addedAt = addedAt, isFavorite = isFavorite,
    )

    private fun MusicPlaylistEntity.toModel() = MusicPlaylist(
        id = id, name = name, description = description,
        thumbnailUrl = thumbnailUrl, createdAt = createdAt,
    )
}
