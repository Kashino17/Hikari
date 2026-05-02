package com.hikari.app.player

import android.content.Context
import androidx.annotation.OptIn as AnnotOptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Singleton
@AnnotOptIn(UnstableApi::class)
class HikariPlayerFactory @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val client: OkHttpClient,
) {
    fun create(): ExoPlayer {
        val okhttpSource = OkHttpDataSource.Factory(client)
        val dataSourceFactory = DefaultDataSource.Factory(ctx, okhttpSource)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs             */ 30_000,
                /* maxBufferMs             */ 120_000,
                /* bufferForPlaybackMs     */ 1_500,
                /* bufferForPlaybackAfterRebufferMs */ 3_000,
            )
            // 15s back-buffer kept decoded — instant rewind within last 15s
            .setBackBuffer(15_000, true)
            .build()

        return ExoPlayer.Builder(ctx)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .build()
            .apply {
                playWhenReady = true
                setSeekParameters(SeekParameters.CLOSEST_SYNC)
            }
    }

    fun mediaItemFor(
        baseUrl: String,
        videoId: String,
        localFilePath: String? = null,
        kind: String = "legacy",
    ): MediaItem {
        val uri = if (localFilePath != null) {
            "file://$localFilePath"
        } else {
            val path = when (kind) {
                "clip" -> "/clips/$videoId.mp4"
                else   -> "/videos/$videoId.mp4"
            }
            "$baseUrl$path"
        }
        return MediaItem.fromUri(uri)
    }
}
