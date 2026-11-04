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
        SearchHistoryEntity::class,
        MusicPlayEventEntity::class,
        WhatsAppMessageEntity::class,
    ],
    version = 21,
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
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun musicPlayEventDao(): MusicPlayEventDao
    abstract fun whatsAppDao(): WhatsAppDao
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

/**
 * Rein additive Migration: Suchverlauf der Musik-Suche.
 */
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `search_history` (
                `query` TEXT NOT NULL,
                `searchedAt` INTEGER NOT NULL,
                PRIMARY KEY(`query`)
            )
            """.trimIndent(),
        )
    }
}

/**
 * Rein additive Migration: Abspiel-Ereignisse für "Meistgehört der Woche".
 */
val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `music_play_events` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `videoId` TEXT NOT NULL,
                `playedAt` INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_music_play_events_playedAt` ON `music_play_events` (`playedAt`)",
        )
        // Manuelle Playlist-Reihenfolge (Bearbeiten-Modus).
        db.execSQL(
            "ALTER TABLE `music_playlist_songs` ADD COLUMN `position` INTEGER NOT NULL DEFAULT 0",
        )
    }
}

// Etappe 2 (Feed-Streaming-Umbau): KI-Kurzbeschreibung fuer Langvideo-Karten.
val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE feed_items ADD COLUMN summary TEXT DEFAULT NULL")
    }
}

// Etappe 3 (Discovery): Herkunft des Feed-Items fuer das "Neu fuer dich"-Badge.
val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE feed_items ADD COLUMN source TEXT DEFAULT NULL")
    }
}

// Veroeffentlichungsdatum der Feed-Items fuer die Datumszeile auf den Karten.
val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE feed_items ADD COLUMN publishedAt INTEGER DEFAULT NULL")
    }
}

// WhatsApp-Ecke: Nachrichten-Tresor fuer geloeschte & bearbeitete WhatsApp-Nachrichten.
val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `whatsapp_messages` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `chatKey` TEXT NOT NULL,
                `chatTitle` TEXT NOT NULL,
                `senderName` TEXT NOT NULL,
                `isGroup` INTEGER NOT NULL DEFAULT 0,
                `packageName` TEXT NOT NULL DEFAULT 'com.whatsapp',
                `text` TEXT NOT NULL,
                `originalText` TEXT DEFAULT NULL,
                `editHistoryJson` TEXT DEFAULT NULL,
                `isDeleted` INTEGER NOT NULL DEFAULT 0,
                `deletedAt` INTEGER DEFAULT NULL,
                `isEdited` INTEGER NOT NULL DEFAULT 0,
                `editedAt` INTEGER DEFAULT NULL,
                `mediaType` TEXT DEFAULT NULL,
                `mediaPreviewPath` TEXT DEFAULT NULL,
                `avatarPath` TEXT DEFAULT NULL,
                `timestamp` INTEGER NOT NULL,
                `notificationKey` TEXT DEFAULT NULL,
                `isReadInHikari` INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_whatsapp_messages_chatKey` ON `whatsapp_messages` (`chatKey`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_whatsapp_messages_timestamp` ON `whatsapp_messages` (`timestamp`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_whatsapp_messages_isDeleted` ON `whatsapp_messages` (`isDeleted`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_whatsapp_messages_isEdited` ON `whatsapp_messages` (`isEdited`)")
    }
}

/**
 * Wiedergabefortschritt wird offline geschrieben und muss später nachgezogen
 * werden. `syncedPositionMs` merkt sich, was das Backend zuletzt bestätigt hat;
 * -1 bedeutet "noch nie übertragen".
 */
val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE playback_positions ADD COLUMN syncedPositionMs INTEGER NOT NULL DEFAULT -1",
        )
        db.execSQL(
            "ALTER TABLE playback_positions ADD COLUMN syncedAt INTEGER NOT NULL DEFAULT 0",
        )
    }
}

/**
 * Clipper und Discovery sind weg: `kind`/`parentVideoId` (Clip-Items) und
 * `source` (Herkunft fuer das "Neu fuer dich"-Badge) fliegen aus `feed_items`.
 * SQLite kann DROP COLUMN erst ab 3.35 (Android 14) — daher Tabelle neu
 * anlegen, Bestand uebernehmen, umbenennen. Clip-Items werden nicht
 * uebernommen: ihre Dateien liefert der Server nicht mehr aus.
 */
val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `feed_items_new` (
                `videoId` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `durationSeconds` INTEGER NOT NULL,
                `aspectRatio` TEXT,
                `thumbnailUrl` TEXT,
                `channelId` TEXT NOT NULL,
                `channelTitle` TEXT NOT NULL,
                `category` TEXT NOT NULL,
                `reasoning` TEXT NOT NULL,
                `overallScore` INTEGER,
                `educationalValue` INTEGER,
                `addedAt` INTEGER NOT NULL,
                `saved` INTEGER NOT NULL,
                `seen` INTEGER NOT NULL,
                `captionsJson` TEXT DEFAULT NULL,
                `context` TEXT DEFAULT NULL,
                `summary` TEXT DEFAULT NULL,
                `position` INTEGER NOT NULL DEFAULT 0,
                `publishedAt` INTEGER DEFAULT NULL,
                PRIMARY KEY(`videoId`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO `feed_items_new` (
                `videoId`, `title`, `durationSeconds`, `aspectRatio`, `thumbnailUrl`,
                `channelId`, `channelTitle`, `category`, `reasoning`, `overallScore`,
                `educationalValue`, `addedAt`, `saved`, `seen`, `captionsJson`,
                `context`, `summary`, `position`, `publishedAt`
            )
            SELECT
                `videoId`, `title`, `durationSeconds`, `aspectRatio`, `thumbnailUrl`,
                `channelId`, `channelTitle`, `category`, `reasoning`, `overallScore`,
                `educationalValue`, `addedAt`, `saved`, `seen`, `captionsJson`,
                `context`, `summary`, `position`, `publishedAt`
            FROM `feed_items`
            WHERE `kind` != 'clip'
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE `feed_items`")
        db.execSQL("ALTER TABLE `feed_items_new` RENAME TO `feed_items`")
    }
}
