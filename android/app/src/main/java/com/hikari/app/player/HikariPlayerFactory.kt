package com.hikari.app.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Singleton
class HikariPlayerFactory @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val client: OkHttpClient,
) {
    fun create(): ExoPlayer {
        val okhttpSource = OkHttpDataSource.Factory(client)
        val dsFactory = DefaultDataSource.Factory(ctx, okhttpSource)
        return ExoPlayer.Builder(ctx)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dsFactory))
            .build()
            .apply { playWhenReady = true }
    }

    fun mediaItemFor(baseUrl: String, videoId: String): MediaItem =
        MediaItem.fromUri("$baseUrl/videos/$videoId.mp4")
}
