package com.hikari.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PlaybackPositionDao {
    @Query("SELECT * FROM playback_positions WHERE videoId = :videoId")
    suspend fun get(videoId: String): PlaybackPositionEntity?

    @Query("SELECT * FROM playback_positions WHERE updatedAt >= :cutoffMs ORDER BY updatedAt DESC")
    suspend fun getRecentSince(cutoffMs: Long): List<PlaybackPositionEntity>

    @Query("SELECT * FROM playback_positions ORDER BY updatedAt DESC")
    suspend fun getAll(): List<PlaybackPositionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: PlaybackPositionEntity)
}
