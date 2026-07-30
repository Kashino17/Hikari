package com.hikari.app.data.db

import androidx.room.*

@Dao
interface MusicSongDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(song: MusicSongEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(songs: List<MusicSongEntity>)

    @Update
    suspend fun update(song: MusicSongEntity)

    @Delete
    suspend fun delete(song: MusicSongEntity)

    @Query("SELECT * FROM music_songs ORDER BY addedAt DESC")
    suspend fun getAll(): List<MusicSongEntity>

    @Query("SELECT * FROM music_songs WHERE videoId = :videoId")
    suspend fun getByName(videoId: String): MusicSongEntity?

    @Query("SELECT * FROM music_songs WHERE isFavorite = 1 ORDER BY addedAt DESC")
    suspend fun getFavorites(): List<MusicSongEntity>

    @Query("UPDATE music_songs SET isFavorite = :favorite WHERE videoId = :videoId")
    suspend fun setFavorite(videoId: String, favorite: Boolean)

    @Query("SELECT COUNT(*) FROM music_songs")
    suspend fun getCount(): Int
}
