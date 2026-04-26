package com.hikari.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [FeedItemEntity::class, PlaybackPositionEntity::class, LocalDownloadEntity::class],
    version = 6,
    exportSchema = false,
)
abstract class HikariDatabase : RoomDatabase() {
    abstract fun feedDao(): FeedDao
    abstract fun playbackPositionDao(): PlaybackPositionDao
    abstract fun localDownloadDao(): LocalDownloadDao
}
