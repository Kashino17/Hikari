package com.hikari.app.di

import com.hikari.app.data.api.MusicApi
import com.hikari.app.data.db.*
import com.hikari.app.domain.repo.MusicRepository
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MusicModule {
    @Provides
    @Singleton
    fun providesGson(): Gson = GsonBuilder().setLenient().create()

    @Provides
    @Singleton
    fun providesMusicApi(gson: Gson): MusicApi {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
        return Retrofit.Builder()
            .baseUrl(MusicApi.BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(MusicApi::class.java)
    }

    @Provides
    @Singleton
    fun providesMusicRepository(
        songDao: MusicSongDao,
        playlistDao: MusicPlaylistDao,
        playlistSongDao: MusicPlaylistSongDao,
        api: MusicApi,
    ): MusicRepository {
        return MusicRepository(songDao, playlistDao, playlistSongDao, api)
    }

    @Provides
    @Singleton
    fun providesMusicSongDao(database: HikariDatabase): MusicSongDao = database.musicSongDao()

    @Provides
    @Singleton
    fun providesMusicPlaylistDao(database: HikariDatabase): MusicPlaylistDao =
        database.musicPlaylistDao()

    @Provides
    @Singleton
    fun providesMusicPlaylistSongDao(database: HikariDatabase): MusicPlaylistSongDao =
        database.musicPlaylistSongDao()
}
