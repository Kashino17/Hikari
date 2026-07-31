package com.hikari.app.di

import com.hikari.app.data.api.HikariApi
import com.hikari.app.data.db.HikariDatabase
import com.hikari.app.data.db.LocalMusicDownloadDao
import com.hikari.app.data.db.MusicPlaylistDao
import com.hikari.app.data.db.MusicPlaylistSongDao
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

/** Wie [MusicFallbackClient], aber ohne Gesamt-Timeout: lädt ganze Audiodateien
 *  von der (backend-fremden) CDN-URL. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MusicDownloadClient

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
    @MusicDownloadClient
    fun providesDownloadClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.SECONDS) // ganze Datei — kein Gesamt-Timeout
        .retryOnConnectionFailure(true)
        .build()

    @Provides
    @Singleton
    fun providesMusicRepository(
        songDao: MusicSongDao,
        playlistDao: MusicPlaylistDao,
        playlistSongDao: MusicPlaylistSongDao,
        downloadDao: LocalMusicDownloadDao,
        api: HikariApi,
        @MusicFallbackClient fallbackClient: OkHttpClient,
        json: Json,
    ): MusicRepository = MusicRepository(
        songDao, playlistDao, playlistSongDao, downloadDao, api, fallbackClient, json,
    )

    @Provides
    @Singleton
    fun providesMusicSongDao(database: HikariDatabase): MusicSongDao = database.musicSongDao()
}
