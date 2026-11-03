package com.hikari.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaybackPositionDao {
    @Query("SELECT * FROM playback_positions WHERE videoId = :videoId")
    suspend fun get(videoId: String): PlaybackPositionEntity?

    @Query("SELECT * FROM playback_positions WHERE updatedAt >= :cutoffMs ORDER BY updatedAt DESC")
    suspend fun getRecentSince(cutoffMs: Long): List<PlaybackPositionEntity>

    @Query("SELECT * FROM playback_positions ORDER BY updatedAt DESC")
    suspend fun getAll(): List<PlaybackPositionEntity>

    /** Live-Ansicht für Downloads/Bibliothek — lokaler Fortschritt ist dort maßgeblich. */
    @Query("SELECT * FROM playback_positions ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<PlaybackPositionEntity>>

    /** Noch nicht (oder nicht mehr) ans Backend übertragene Positionen. */
    @Query("SELECT * FROM playback_positions WHERE syncedPositionMs != positionMs ORDER BY updatedAt ASC")
    suspend fun getPending(): List<PlaybackPositionEntity>

    /**
     * Markiert genau die übertragene Position als synchronisiert. Der
     * `positionMs`-Guard verhindert, dass ein während des Uploads
     * weitergelaufener Fortschritt fälschlich als gesynct gilt.
     */
    @Query(
        "UPDATE playback_positions SET syncedPositionMs = :positionMs, syncedAt = :syncedAt " +
            "WHERE videoId = :videoId AND positionMs = :positionMs",
    )
    suspend fun markSynced(videoId: String, positionMs: Long, syncedAt: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: PlaybackPositionEntity)
}
