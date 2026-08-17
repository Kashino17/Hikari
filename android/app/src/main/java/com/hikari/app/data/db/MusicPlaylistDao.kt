package com.hikari.app.data.db

import androidx.room.*

@Dao
interface MusicPlaylistDao {
    @Insert
    suspend fun insert(playlist: MusicPlaylistEntity): Long

    @Update
    suspend fun update(playlist: MusicPlaylistEntity)

    @Delete
    suspend fun delete(playlist: MusicPlaylistEntity)

    @Query("SELECT * FROM music_playlists ORDER BY createdAt DESC")
    suspend fun getAll(): List<MusicPlaylistEntity>

    @Query("SELECT * FROM music_playlists WHERE id = :id")
    suspend fun getById(id: Int): MusicPlaylistEntity?
}

@Dao
interface MusicPlaylistSongDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(song: MusicPlaylistSongEntity): Long

    @Delete
    suspend fun delete(song: MusicPlaylistSongEntity)

    @Query(
        "SELECT * FROM music_playlist_songs WHERE playlistId = :playlistId ORDER BY position ASC, addedAt ASC",
    )
    suspend fun getByPlaylist(playlistId: Int): List<MusicPlaylistSongEntity>

    @Query("SELECT COALESCE(MAX(position), 0) FROM music_playlist_songs WHERE playlistId = :playlistId")
    suspend fun maxPosition(playlistId: Int): Int

    @Query(
        "UPDATE music_playlist_songs SET position = :position WHERE playlistId = :playlistId AND songVideoId = :videoId",
    )
    suspend fun setPosition(playlistId: Int, videoId: String, position: Int)

    @Query("SELECT COUNT(*) FROM music_playlist_songs WHERE playlistId = :playlistId")
    suspend fun getCount(playlistId: Int): Int

    @Query("DELETE FROM music_playlist_songs WHERE playlistId = :playlistId")
    suspend fun clearPlaylist(playlistId: Int)
}
