package com.hikari.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WhatsAppDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: WhatsAppMessageEntity): Long

    @Update
    suspend fun update(message: WhatsAppMessageEntity)

    @Query("SELECT * FROM whatsapp_messages WHERE isDeleted = 1 OR isEdited = 1 ORDER BY COALESCE(deletedAt, editedAt, timestamp) DESC")
    fun observeAllMessages(): Flow<List<WhatsAppMessageEntity>>

    @Query("SELECT * FROM whatsapp_messages WHERE isDeleted = 1 ORDER BY COALESCE(deletedAt, timestamp) DESC")
    fun observeDeletedMessages(): Flow<List<WhatsAppMessageEntity>>

    @Query("SELECT * FROM whatsapp_messages WHERE isEdited = 1 ORDER BY COALESCE(editedAt, timestamp) DESC")
    fun observeEditedMessages(): Flow<List<WhatsAppMessageEntity>>

    @Query(
        """
        SELECT * FROM whatsapp_messages
        WHERE chatKey = :chatKey
        ORDER BY timestamp DESC, id DESC
        LIMIT :limit
        """,
    )
    suspend fun getRecentMessagesForChat(chatKey: String, limit: Int = 40): List<WhatsAppMessageEntity>

    @Query(
        """
        SELECT * FROM whatsapp_messages
        WHERE packageName = :packageName AND isDeleted = 0 AND timestamp >= :sinceTimestamp
        ORDER BY timestamp DESC, id DESC
        LIMIT :limit
        """,
    )
    suspend fun getRecentUnmarkedMessages(
        packageName: String,
        sinceTimestamp: Long,
        limit: Int = 40,
    ): List<WhatsAppMessageEntity>

    @Query("SELECT * FROM whatsapp_messages WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): WhatsAppMessageEntity?

    @Query("DELETE FROM whatsapp_messages WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM whatsapp_messages WHERE chatKey = :chatKey")
    suspend fun deleteChat(chatKey: String)

    @Query("DELETE FROM whatsapp_messages")
    suspend fun deleteAll()

    /**
     * Entfernt reguläre Zwischenspeicher-Nachrichten (nicht gelöscht & nicht bearbeitet),
     * die älter als [stagingCutoffTimestamp] (24 Stunden) sind.
     */
    @Query("DELETE FROM whatsapp_messages WHERE isDeleted = 0 AND isEdited = 0 AND timestamp < :stagingCutoffTimestamp")
    suspend fun deleteExpiredNormalMessages(stagingCutoffTimestamp: Long): Int

    /**
     * Liefert alle Einträge, die älter als [cutoffTimestamp] (7 Tage) sind,
     * damit vor dem DB-Löschen auch deren Mediendateien bereinigt werden können.
     */
    @Query("SELECT * FROM whatsapp_messages WHERE COALESCE(deletedAt, editedAt, timestamp) < :cutoffTimestamp")
    suspend fun getExpiredMessages(cutoffTimestamp: Long): List<WhatsAppMessageEntity>

    /**
     * Automatische Bereinigung: Löscht alle Einträge, die älter als [cutoffTimestamp] (7 Tage) sind.
     */
    @Query("DELETE FROM whatsapp_messages WHERE COALESCE(deletedAt, editedAt, timestamp) < :cutoffTimestamp")
    suspend fun deleteExpiredMessages(cutoffTimestamp: Long): Int

    @Query("SELECT COUNT(*) FROM whatsapp_messages WHERE isDeleted = 1")
    fun observeDeletedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM whatsapp_messages WHERE isEdited = 1")
    fun observeEditedCount(): Flow<Int>

    @Query("SELECT mediaPreviewPath FROM whatsapp_messages WHERE mediaPreviewPath IS NOT NULL")
    suspend fun getActiveMediaPaths(): List<String>
}
