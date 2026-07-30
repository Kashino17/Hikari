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
        MusicSongEntity::class,
        MusicPlaylistEntity::class,
        MusicPlaylistSongEntity::class,
    ],
    version = 12,
    exportSchema = false,
)
abstract class HikariDatabase : RoomDatabase() {
    abstract fun feedDao(): FeedDao
    abstract fun playbackPositionDao(): PlaybackPositionDao
    abstract fun localDownloadDao(): LocalDownloadDao
    abstract fun localMangaDao(): LocalMangaDao
    abstract fun musicSongDao(): MusicSongDao
    abstract fun musicPlaylistDao(): MusicPlaylistDao
    abstract fun musicPlaylistSongDao(): MusicPlaylistSongDao
}
