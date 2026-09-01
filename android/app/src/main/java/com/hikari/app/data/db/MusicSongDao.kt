package com.hikari.app.data.db

import androidx.room.*

@Dao
interface MusicSongDao {
    // Upsert statt REPLACE-Insert: REPLACE ist intern DELETE+INSERT und würde
    // über den CASCADE-Fremdschlüssel die Zeilen in music_playlist_songs
    // mitlöschen. Upsert macht ein echtes UPDATE ohne Delete.
    @Upsert
    suspend fun insert(song: MusicSongEntity)

    @Upsert
    suspend fun insertAll(songs: List<MusicSongEntity>)

    @Update
    suspend fun update(song: MusicSongEntity)

    @Delete
    suspend fun delete(song: MusicSongEntity)

    @Query("SELECT * FROM music_songs ORDER BY addedAt DESC")
    suspend fun getAll(): List<MusicSongEntity>

    @Query("SELECT * FROM music_songs WHERE videoId = :videoId")
    suspend fun getByName(videoId: String): MusicSongEntity?

    @Query("SELECT * FROM music_songs WHERE videoId IN (:videoIds)")
    suspend fun getByIds(videoIds: List<String>): List<MusicSongEntity>

    @Query("SELECT * FROM music_songs WHERE isFavorite = 1 ORDER BY addedAt DESC")
    suspend fun getFavorites(): List<MusicSongEntity>

    @Query("UPDATE music_songs SET isFavorite = :favorite WHERE videoId = :videoId")
    suspend fun setFavorite(videoId: String, favorite: Boolean)

    @Query("SELECT COUNT(*) FROM music_songs")
    suspend fun getCount(): Int
}
