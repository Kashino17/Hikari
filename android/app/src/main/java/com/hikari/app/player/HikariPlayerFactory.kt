package com.hikari.app.player

import android.content.Context
import androidx.annotation.OptIn as AnnotOptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Singleton
@AnnotOptIn(UnstableApi::class)
class HikariPlayerFactory @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val client: OkHttpClient,
) {
    // 500 MB on-disk chunk cache — persists across app sessions.
    // Rewatching any recently-cached video = zero Tailscale traffic, instant start.
    private val cache: SimpleCache by lazy {
        val cacheDir = File(ctx.cacheDir, "media3-cache")
        SimpleCache(
            cacheDir,
            LeastRecentlyUsedCacheEvictor(500L * 1024 * 1024),
            StandaloneDatabaseProvider(ctx),
        )
    }

    fun create(): ExoPlayer {
        val okhttpSource = OkHttpDataSource.Factory(client)
        val upstream = DefaultDataSource.Factory(ctx, okhttpSource)
        val cacheDataSource = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstream)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        val mediaSourceFactory = DefaultMediaSourceFactory(cacheDataSource)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs             */ 60_000,
                /* maxBufferMs             */ 180_000,
                /* bufferForPlaybackMs     */ 2_500,
                /* bufferForPlaybackAfterRebufferMs */ 5_000,
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            // 30s back-buffer kept decoded — instant rewind within last 30s
            .setBackBuffer(30_000, true)
            .build()

        return ExoPlayer.Builder(ctx)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .build()
            .apply {
                playWhenReady = true
                // Frame-accurate seek; negligible cost when target is in the 30s back-buffer
                setSeekParameters(SeekParameters.EXACT)
            }
    }

    fun mediaItemFor(baseUrl: String, videoId: String): MediaItem =
        MediaItem.fromUri("$baseUrl/videos/$videoId.mp4")
}
