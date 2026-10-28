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

    @Query("SELECT * FROM whatsapp_messages ORDER BY timestamp DESC")
    fun observeAllMessages(): Flow<List<WhatsAppMessageEntity>>

    @Query("SELECT * FROM whatsapp_messages WHERE isDeleted = 1 ORDER BY COALESCE(deletedAt, timestamp) DESC")
    fun observeDeletedMessages(): Flow<List<WhatsAppMessageEntity>>

    @Query("SELECT * FROM whatsapp_messages WHERE isEdited = 1 ORDER BY COALESCE(editedAt, timestamp) DESC")
    fun observeEditedMessages(): Flow<List<WhatsAppMessageEntity>>

    @Query("SELECT * FROM whatsapp_messages WHERE chatKey = :chatKey ORDER BY timestamp ASC")
    fun observeChatMessages(chatKey: String): Flow<List<WhatsAppMessageEntity>>

    @Query(
        """
        SELECT * FROM whatsapp_messages
        WHERE chatKey = :chatKey
        ORDER BY timestamp DESC
        LIMIT :limit
        """,
    )
    suspend fun getRecentMessagesForChat(chatKey: String, limit: Int = 25): List<WhatsAppMessageEntity>

    @Query(
        """
        SELECT * FROM whatsapp_messages
        WHERE notificationKey = :notificationKey
        ORDER BY timestamp DESC
        LIMIT :limit
        """,
    )
    suspend fun getRecentMessagesByNotificationKey(
        notificationKey: String,
        limit: Int = 15,
    ): List<WhatsAppMessageEntity>

    @Query("SELECT * FROM whatsapp_messages WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): WhatsAppMessageEntity?

    @Query("UPDATE whatsapp_messages SET isReadInHikari = 1 WHERE chatKey = :chatKey AND isReadInHikari = 0")
    suspend fun markChatRead(chatKey: String)

    @Query("UPDATE whatsapp_messages SET isReadInHikari = 1 WHERE isReadInHikari = 0")
    suspend fun markAllRead()

    @Query("DELETE FROM whatsapp_messages WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM whatsapp_messages WHERE chatKey = :chatKey")
    suspend fun deleteChat(chatKey: String)

    @Query("DELETE FROM whatsapp_messages")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM whatsapp_messages WHERE isDeleted = 1")
    fun observeDeletedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM whatsapp_messages WHERE isEdited = 1")
    fun observeEditedCount(): Flow<Int>
}
