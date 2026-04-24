package com.hikari.app.di

import android.content.Context
import androidx.room.Room
import com.hikari.app.data.db.FeedDao
import com.hikari.app.data.db.HikariDatabase
import com.hikari.app.data.db.PlaybackPositionDao
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
            .fallbackToDestructiveMigration()
            .build()

    @Provides @Singleton
    fun provideFeedDao(db: HikariDatabase): FeedDao = db.feedDao()

    @Provides @Singleton
    fun providePlaybackPositionDao(db: HikariDatabase): PlaybackPositionDao =
        db.playbackPositionDao()
}
