package com.hikari.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Ein aufs Handy geladenes Buch — lesbar ohne Netz und ohne Mac. */
@Entity(tableName = "local_ebooks")
data class LocalEbookEntity(
    @PrimaryKey val id: String,
    val title: String,
    /** Autoren, mit „, “ verbunden. */
    val authors: String,
    val format: String,
    @ColumnInfo(name = "cover_path") val coverPath: String?,
    @ColumnInfo(name = "file_path") val filePath: String,
    val bytes: Long,
    @ColumnInfo(name = "added_at") val addedAt: Long,
) {
    val isPdf: Boolean get() = format == "pdf"
}

@Dao
interface LocalEbookDao {
    @Query("SELECT * FROM local_ebooks ORDER BY added_at DESC")
    fun observeAll(): Flow<List<LocalEbookEntity>>

    @Query("SELECT * FROM local_ebooks WHERE id = :id LIMIT 1")
    suspend fun get(id: String): LocalEbookEntity?

    @Upsert
    suspend fun upsert(e: LocalEbookEntity)

    @Query("DELETE FROM local_ebooks WHERE id = :id")
    suspend fun delete(id: String)
}
