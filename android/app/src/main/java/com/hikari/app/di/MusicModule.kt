package com.hikari.app.di

import com.hikari.app.data.api.HikariApi
import com.hikari.app.data.db.HikariDatabase
import com.hikari.app.data.db.MusicSongDao
import com.hikari.app.domain.repo.MusicRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

/** Plain client for direct Piped fallback calls — must NOT go through the
 *  backend-URL-rewriting interceptors of the main OkHttpClient. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MusicFallbackClient

@Module
@InstallIn(SingletonComponent::class)
object MusicModule {

    @Provides
    @Singleton
    @MusicFallbackClient
    fun providesFallbackClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun providesMusicRepository(
        songDao: MusicSongDao,
        api: HikariApi,
        @MusicFallbackClient fallbackClient: OkHttpClient,
        json: Json,
    ): MusicRepository = MusicRepository(songDao, api, fallbackClient, json)

    @Provides
    @Singleton
    fun providesMusicSongDao(database: HikariDatabase): MusicSongDao = database.musicSongDao()
}
