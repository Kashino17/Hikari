package com.hikari.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalMusicDownloadDao {
    @Query("SELECT * FROM local_music_downloads WHERE video_id = :id LIMIT 1")
    suspend fun get(id: String): LocalMusicDownloadEntity?

    @Query("SELECT * FROM local_music_downloads ORDER BY downloaded_at DESC")
    fun observeAll(): Flow<List<LocalMusicDownloadEntity>>

    @Query("SELECT * FROM local_music_downloads ORDER BY downloaded_at DESC")
    suspend fun getAll(): List<LocalMusicDownloadEntity>

    @Query("SELECT video_id FROM local_music_downloads")
    fun observeIds(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LocalMusicDownloadEntity)

    @Query("DELETE FROM local_music_downloads WHERE video_id = :id")
    suspend fun delete(id: String)
}
