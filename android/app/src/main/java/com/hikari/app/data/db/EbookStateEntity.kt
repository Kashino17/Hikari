package com.hikari.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Lesestand eines Buchs als JSON (BookState); dirty = noch nicht beim Mac. */
@Entity(tableName = "ebook_state")
data class EbookStateEntity(
    @PrimaryKey @ColumnInfo(name = "book_id") val bookId: String,
    val json: String,
    val dirty: Boolean,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Dao
interface EbookStateDao {
    @Query("SELECT * FROM ebook_state WHERE book_id = :id LIMIT 1")
    fun observe(id: String): Flow<EbookStateEntity?>

    @Query("SELECT * FROM ebook_state WHERE book_id = :id LIMIT 1")
    suspend fun get(id: String): EbookStateEntity?

    @Query("SELECT * FROM ebook_state")
    suspend fun all(): List<EbookStateEntity>

    @Query("SELECT * FROM ebook_state")
    fun observeAll(): Flow<List<EbookStateEntity>>

    @Query("SELECT * FROM ebook_state WHERE dirty = 1")
    suspend fun dirty(): List<EbookStateEntity>

    @Upsert
    suspend fun upsert(e: EbookStateEntity)
}
