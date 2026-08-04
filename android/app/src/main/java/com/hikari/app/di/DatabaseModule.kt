package com.hikari.app.di

import android.content.Context
import androidx.room.Room
import com.hikari.app.data.db.FeedDao
import com.hikari.app.data.db.HikariDatabase
import com.hikari.app.data.db.LocalDownloadDao
import com.hikari.app.data.db.LocalMangaDao
import com.hikari.app.data.db.LocalMusicDownloadDao
import com.hikari.app.data.db.MIGRATION_12_13
import com.hikari.app.data.db.MIGRATION_13_14
import com.hikari.app.data.db.MusicPlaylistDao
import com.hikari.app.data.db.MusicPlaylistSongDao
import com.hikari.app.data.db.PlaybackPositionDao
import com.hikari.app.data.db.SearchHistoryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): HikariDatabase =
        Room.databaseBuilder(ctx, HikariDatabase::class.java, "hikari.db")
            .addMigrations(MIGRATION_12_13, MIGRATION_13_14)
            .fallbackToDestructiveMigration()
            .build()

    @Provides @Singleton
    fun provideFeedDao(db: HikariDatabase): FeedDao = db.feedDao()

    @Provides @Singleton
    fun providePlaybackPositionDao(db: HikariDatabase): PlaybackPositionDao =
        db.playbackPositionDao()

    @Provides @Singleton
    fun provideLocalDownloadDao(db: HikariDatabase): LocalDownloadDao =
        db.localDownloadDao()

    @Provides @Singleton
    fun provideLocalMangaDao(db: HikariDatabase): LocalMangaDao =
        db.localMangaDao()

    @Provides @Singleton
    fun provideLocalMusicDownloadDao(db: HikariDatabase): LocalMusicDownloadDao =
        db.localMusicDownloadDao()

    @Provides @Singleton
    fun provideMusicPlaylistDao(db: HikariDatabase): MusicPlaylistDao =
        db.musicPlaylistDao()

    @Provides @Singleton
    fun provideMusicPlaylistSongDao(db: HikariDatabase): MusicPlaylistSongDao =
        db.musicPlaylistSongDao()

    @Provides @Singleton
    fun provideSearchHistoryDao(db: HikariDatabase): SearchHistoryDao =
        db.searchHistoryDao()
}
