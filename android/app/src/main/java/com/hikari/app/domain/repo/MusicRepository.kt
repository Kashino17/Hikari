package com.hikari.app.domain.repo

import com.hikari.app.data.api.HikariApi
import com.hikari.app.data.api.dto.MusicTrackDto
import com.hikari.app.data.api.dto.PipedSearchPageDto
import com.hikari.app.data.api.dto.PipedStreamsDto
import com.hikari.app.data.db.MusicSongDao
import com.hikari.app.data.db.MusicSongEntity
import com.hikari.app.domain.model.MusicSong
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

data class DiscoverSection(val title: String, val songs: List<MusicSong>)

/**
 * Search + streaming go through the Hikari backend (yt-dlp — the same
 * extraction the clipper uses, so it works even when public Piped instances
 * are blocked). If the backend is unreachable the repo falls back to querying
 * Piped instances directly from the device.
 */
class MusicRepository(
    private val songDao: MusicSongDao,
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

    /** Called on every playback start; keeps favorite state, bumps recency. */
    suspend fun recordPlayed(song: MusicSong) {
        val existing = songDao.getByName(song.videoId)
        songDao.insert(
            song.toEntity().copy(
                isFavorite = existing?.isFavorite ?: song.isFavorite,
                addedAt = System.currentTimeMillis(),
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
                            thumbnailUrl = item.thumbnail.orEmpty()
                                .let { if (it.startsWith("//")) "https:$it" else it },
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
}
