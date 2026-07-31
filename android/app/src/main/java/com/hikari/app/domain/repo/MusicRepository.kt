package com.hikari.app.domain.repo

import com.hikari.app.data.api.MusicApi
import com.hikari.app.data.api.dto.PipedSearchResult
import com.hikari.app.data.db.*
import com.hikari.app.domain.model.MusicPlaylist
import com.hikari.app.domain.model.MusicSong
import com.hikari.app.domain.model.PlaylistSong

class MusicRepository(
    private val songDao: MusicSongDao,
    private val playlistDao: MusicPlaylistDao,
    private val playlistSongDao: MusicPlaylistSongDao,
    private val api: MusicApi,
) {
    suspend fun searchMusic(query: String): List<MusicSong> {
        return try {
            val results = api.search(query)
            results.results.mapIndexed { index, r -> r.toSong(index) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getSuggestions(query: String): List<MusicSong> {
        // Piped suggestions returns plain strings, resolve via search
        val suggestions = api.getSuggestions(query)
        return suggestions.map { suggestion ->
            // Use the suggestion text as a title with a placeholder uploader
            val videoId = "suggestion_${suggestion.hashCode()}"
            MusicSong(
                videoId = videoId,
                title = suggestion,
                uploader = "Hikari Suggestions",
                uploaderUrl = "",
                thumbnailUrl = "",
                duration = 0,
                views = 0,
                addedAt = System.currentTimeMillis(),
            )
        }
    }

    suspend fun insertSong(song: MusicSong) = songDao.insert(song.toEntity())

    suspend fun insertSongs(songs: List<MusicSong>) = songDao.insertAll(songs.map { it.toEntity() })

    suspend fun getAllSongs(): List<MusicSong> = songDao.getAll().map { it.toSong() }

    suspend fun getSong(videoId: String): MusicSong? = songDao.getByName(videoId)?.toSong()

    suspend fun getFavorites(): List<MusicSong> = songDao.getFavorites().map { it.toSong() }

    suspend fun toggleFavorite(videoId: String) {
        val current = songDao.getByName(videoId)
        songDao.setFavorite(videoId, !(current?.isFavorite == true))
    }

    suspend fun isFavorite(videoId: String): Boolean = songDao.getByName(videoId)?.isFavorite == true

    suspend fun removeSong(song: MusicSong) = songDao.delete(song.toEntity())

    suspend fun createPlaylist(name: String, description: String = ""): MusicPlaylist {
        val id = playlistDao.insert(MusicPlaylistEntity(name = name, description = description))
        return MusicPlaylist(id = id.toInt(), name = name, description = description)
    }

    suspend fun getPlaylists(): List<MusicPlaylist> = playlistDao.getAll().map { it.toModel() }

    suspend fun getPlaylist(id: Int): MusicPlaylist? = playlistDao.getById(id)?.toModel()

    suspend fun deletePlaylist(playlist: MusicPlaylist) = playlistDao.delete(playlist.toEntity())

    suspend fun getPlaylistSongs(playlistId: Int): List<PlaylistSong> {
        val entities = playlistSongDao.getByPlaylist(playlistId)
        val songs = songDao.getAll()
        val songMap = songs.associateBy { it.videoId }
        return entities.mapNotNull { e ->
            songMap[e.songVideoId]?.toSong()?.let { PlaylistSong(playlistId, it, e.addedAt) }
        }
    }

    suspend fun addSongToPlaylist(playlistId: Int, song: MusicSong): Long {
        songDao.insert(song.toEntity())
        return playlistSongDao.insert(MusicPlaylistSongEntity(playlistId, song.videoId))
    }

    suspend fun removeSongFromPlaylist(playlistId: Int, song: MusicSong) {
        playlistSongDao.delete(MusicPlaylistSongEntity(playlistId, song.videoId))
    }

    suspend fun clearPlaylist(playlistId: Int) = playlistSongDao.clearPlaylist(playlistId)

    suspend fun getAudioStream(videoId: String): String? {
        return try {
            val streams = api.getStreams(videoId)
            streams.streams.firstOrNull { it.mimeType.contains("audio/mp4") }?.url
        } catch (_: Exception) {
            null
        }
    }

    suspend fun getMusicSuggestions(): List<MusicSong> {
        val keywords = listOf("lofi", "pop", "jazz", "chill")
        val results = mutableListOf<MusicSong>()
        keywords.take(3).forEach { keyword ->
            try {
                val suggestions = api.getSuggestions(keyword)
                results.addAll(suggestions.take(4).map { suggestion ->
                    val videoId = "suggestion_${keyword}_${suggestion.hashCode()}"
                    MusicSong(
                        videoId = videoId,
                        title = suggestion,
                        uploader = keyword,
                        uploaderUrl = "",
                        thumbnailUrl = "",
                        duration = 0,
                        views = 0,
                        addedAt = System.currentTimeMillis(),
                    )
                })
            } catch (_: Exception) {}
        }
        return results
    }

    private fun PipedSearchResult.toSong(index: Int) = MusicSong(
        videoId = url.substringAfterLast("/"),
        title = title, uploader = uploader, uploaderUrl = uploaderUrl,
        thumbnailUrl = thumbnail?.let { if (it.startsWith("//")) "https:$it" else it } ?: "",
        duration = duration, views = views,
        addedAt = System.currentTimeMillis() + index,
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

    private fun MusicPlaylist.toEntity() = MusicPlaylistEntity(
        id = id, name = name, description = description,
        thumbnailUrl = thumbnailUrl, createdAt = createdAt,
    )

    private fun MusicPlaylistEntity.toModel() = MusicPlaylist(
        id = id, name = name, description = description,
        thumbnailUrl = thumbnailUrl, createdAt = createdAt,
    )
}
