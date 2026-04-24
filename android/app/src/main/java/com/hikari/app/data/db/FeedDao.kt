package com.hikari.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FeedDao {
    @Query("SELECT * FROM feed_items WHERE seen = 0 ORDER BY addedAt DESC")
    fun unseenItems(): Flow<List<FeedItemEntity>>

    @Query("SELECT * FROM feed_items WHERE saved = 1 ORDER BY addedAt DESC")
    fun savedItems(): Flow<List<FeedItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<FeedItemEntity>)

    @Query("UPDATE feed_items SET seen = 1 WHERE videoId = :videoId")
    suspend fun markSeen(videoId: String)

    @Query("UPDATE feed_items SET saved = :saved WHERE videoId = :videoId")
    suspend fun setSaved(videoId: String, saved: Boolean)

    @Query("DELETE FROM feed_items WHERE videoId NOT IN (:keepIds)")
    suspend fun pruneNotIn(keepIds: List<String>)
}
