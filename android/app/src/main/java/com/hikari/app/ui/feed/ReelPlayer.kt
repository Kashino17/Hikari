package com.hikari.app.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.hikari.app.data.sponsor.SponsorBlockClient
import com.hikari.app.data.sponsor.SponsorSegment
import com.hikari.app.domain.model.FeedItem
import com.hikari.app.domain.repo.PlaybackRepository
import com.hikari.app.player.SponsorSkipListener

@OptIn(UnstableApi::class)
@Composable
fun ReelPlayer(
    item: FeedItem,
    player: ExoPlayer,
    sponsorBlock: SponsorBlockClient,
    playbackRepo: PlaybackRepository,
    onSeen: () -> Unit,
    onToggleSave: () -> Unit,
    onLessLikeThis: () -> Unit,
    onUnplayable: () -> Unit,
) {
    var playing by remember { mutableStateOf(true) }

    // A4: Player error → onUnplayable + auto-advance
    DisposableEffect(item.videoId, player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                onUnplayable()
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // B3: Restore playback position on mount, save on dispose
    DisposableEffect(item.videoId) {
        onDispose {
            val pos = player.currentPosition
            if (pos > 0L) {
                kotlinx.coroutines.runBlocking {
                    playbackRepo.savePosition(item.videoId, pos)
                }
            }
        }
    }
    LaunchedEffect(item.videoId) {
        val savedPos = playbackRepo.getPosition(item.videoId)
        if (savedPos > 0L) {
            player.seekTo(savedPos)
        }
    }

    // A1: mark seen after 3 seconds of actual playback
    var seenFired by remember(item.videoId) { mutableStateOf(false) }
    LaunchedEffect(item.videoId) {
        while (!seenFired) {
            kotlinx.coroutines.delay(500)
            val pos = player.currentPosition
            if (pos >= 3_000L) {
                seenFired = true
                onSeen()
            }
        }
    }

    // A2: SponsorBlock skip
    var segments by remember(item.videoId) { mutableStateOf<List<SponsorSegment>>(emptyList()) }
    LaunchedEffect(item.videoId) {
        segments = sponsorBlock.fetchSegments(item.videoId)
    }
    LaunchedEffect(item.videoId, segments) {
        while (true) {
            kotlinx.coroutines.delay(200)
            val pos = player.currentPosition
            val skip = SponsorSkipListener.skipTargetMs(pos, segments)
            if (skip != null && skip > pos) {
                player.seekTo(skip)
            }
        }
    }

    // B4: Progress bar state
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(1L) }
    LaunchedEffect(item.videoId) {
        while (true) {
            kotlinx.coroutines.delay(500)
            position = player.currentPosition
            duration = player.duration.coerceAtLeast(1L)
        }
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
                .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
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

        // B4: Thin progress bar at bottom
        LinearProgressIndicator(
            progress = { (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            color = Color.White,
            trackColor = Color.White.copy(alpha = 0.25f),
        )
    }
}
