package com.hikari.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
        LocalMusicDownloadEntity::class,
    ],
    version = 13,
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
    abstract fun localMusicDownloadDao(): LocalMusicDownloadDao
}

/**
 * Erste echte Migration des Projekts: rein additiv. Ohne sie würde
 * `fallbackToDestructiveMigration` beim Update auf v0.41 sämtliche
 * Download- und Playlist-Einträge des Nutzers löschen.
 */
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `local_music_downloads` (
                `video_id` TEXT NOT NULL,
                `local_file_path` TEXT NOT NULL,
                `byte_size` INTEGER NOT NULL,
                `downloaded_at` INTEGER NOT NULL,
                `title` TEXT NOT NULL,
                `uploader` TEXT NOT NULL,
                `thumbnail_url` TEXT NOT NULL,
                `duration_seconds` INTEGER NOT NULL,
                PRIMARY KEY(`video_id`)
            )
            """.trimIndent(),
        )
    }
}
