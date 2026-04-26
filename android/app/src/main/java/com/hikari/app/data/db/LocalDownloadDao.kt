package com.hikari.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalDownloadDao {
    @Query("SELECT * FROM local_downloads WHERE video_id = :id LIMIT 1")
    suspend fun get(id: String): LocalDownloadEntity?

    @Query("SELECT * FROM local_downloads ORDER BY downloaded_at DESC")
    fun observeAll(): Flow<List<LocalDownloadEntity>>

    @Query("SELECT video_id FROM local_downloads")
    fun observeIds(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LocalDownloadEntity)

    @Query("DELETE FROM local_downloads WHERE video_id = :id")
    suspend fun delete(id: String)
}
