package com.hikari.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Ein vollständig heruntergeladener Song. Die Zeile existiert nur, wenn die
 * Datei komplett auf der Platte liegt — Fortschritt lebt im Manager, nicht hier.
 *
 * Metadaten sind bewusst denormalisiert mitgeschrieben (wie bei
 * [LocalDownloadEntity]), damit die Offline-Ansicht ohne Server und ohne
 * Join auf `music_songs` rendern kann.
 */
@Entity(tableName = "local_music_downloads")
data class LocalMusicDownloadEntity(
    @PrimaryKey @ColumnInfo(name = "video_id") val videoId: String,
    @ColumnInfo(name = "local_file_path") val localFilePath: String,
    @ColumnInfo(name = "byte_size") val byteSize: Long,
    @ColumnInfo(name = "downloaded_at") val downloadedAt: Long,
    @ColumnInfo(name = "title") val title: String = "",
    @ColumnInfo(name = "uploader") val uploader: String = "",
    @ColumnInfo(name = "thumbnail_url") val thumbnailUrl: String = "",
    @ColumnInfo(name = "duration_seconds") val durationSeconds: Int = 0,
)
