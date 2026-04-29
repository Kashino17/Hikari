package com.hikari.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Eine einzelne heruntergeladene Manga-Seite. CASCADE-Delete vom Arc:
 * wenn der User einen Arc löscht, fliegen alle dazugehörigen Page-Rows
 * automatisch raus.
 *
 * Index auf arc_id ermöglicht günstiges COUNT für "85/120 Pages bereit".
 */
@Entity(
    tableName = "local_manga_pages",
    foreignKeys = [
        ForeignKey(
            entity = LocalMangaArcEntity::class,
            parentColumns = ["arc_id"],
            childColumns = ["arc_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["arc_id"])],
)
data class LocalMangaPageEntity(
    @PrimaryKey @ColumnInfo(name = "page_id") val pageId: String,
    @ColumnInfo(name = "arc_id") val arcId: String,
    @ColumnInfo(name = "chapter_id") val chapterId: String,
    @ColumnInfo(name = "chapter_number") val chapterNumber: Double,
    @ColumnInfo(name = "page_number") val pageNumber: Int,
    @ColumnInfo(name = "local_file_path") val localFilePath: String,
    @ColumnInfo(name = "byte_size") val byteSize: Long,
    @ColumnInfo(name = "downloaded_at") val downloadedAt: Long,
)
