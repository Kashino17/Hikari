package com.hikari.app.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.hikari.app.domain.model.FeedItem

@OptIn(UnstableApi::class)
@Composable
fun ReelPlayer(
    item: FeedItem,
    player: ExoPlayer,
    mediaItem: MediaItem,
    onSeen: () -> Unit,
    onToggleSave: () -> Unit,
    onLessLikeThis: () -> Unit,
) {
    var playing by remember { mutableStateOf(true) }

    DisposableEffect(item.videoId) {
        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = true
        playing = true
        onSeen()
        onDispose { player.clearMediaItems() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(item.videoId) {
                detectTapGestures(
                    onTap = {
                        playing = !playing
                        player.playWhenReady = playing
                    },
                    onDoubleTap = { onLessLikeThis() },
                    onLongPress = { onToggleSave() },
                )
            },
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    setPlayer(player)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
        ) {
            Text(
                item.channelTitle,
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                item.title,
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                "${item.category} · ${item.durationSeconds}s",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}
