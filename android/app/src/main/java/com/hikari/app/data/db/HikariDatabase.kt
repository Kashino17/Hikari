package com.hikari.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        FeedItemEntity::class,
        PlaybackPositionEntity::class,
        LocalDownloadEntity::class,
        LocalMangaArcEntity::class,
        LocalMangaPageEntity::class,
    ],
    version = 9,
    exportSchema = false,
)
abstract class HikariDatabase : RoomDatabase() {
    abstract fun feedDao(): FeedDao
    abstract fun playbackPositionDao(): PlaybackPositionDao
    abstract fun localDownloadDao(): LocalDownloadDao
    abstract fun localMangaDao(): LocalMangaDao
}
