package com.hikari.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "local_downloads")
data class LocalDownloadEntity(
    @PrimaryKey @ColumnInfo(name = "video_id") val videoId: String,
    @ColumnInfo(name = "local_file_path") val localFilePath: String,
    @ColumnInfo(name = "byte_size") val byteSize: Long,
    @ColumnInfo(name = "downloaded_at") val downloadedAt: Long,
    /** Cached so playback can show duration before any network round-trip. */
    @ColumnInfo(name = "duration_seconds") val durationSeconds: Int = 0,
)
