package com.hikari.app.ui.feed

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.hikari.app.data.sponsor.SponsorBlockClient
import com.hikari.app.data.sponsor.SponsorSegment
import com.hikari.app.domain.model.FeedItem
import com.hikari.app.domain.repo.PlaybackRepository
import com.hikari.app.player.SponsorSkipListener

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}

/** Animated play/pause indicator shown in center when tapping. */
@Composable
private fun PlayPauseIndicator(
    playing: Boolean,
    showTrigger: Int,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(showTrigger) {
        if (showTrigger > 0) {
            visible = true
            kotlinx.coroutines.delay(650)
            visible = false
        }
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200, easing = FastOutSlowInEasing)) +
            scaleIn(initialScale = 0.6f, animationSpec = tween(200, easing = FastOutSlowInEasing)),
        exit = fadeOut(tween(180, easing = FastOutLinearInEasing)) +
            scaleOut(targetScale = 1.15f, animationSpec = tween(180, easing = FastOutLinearInEasing)),
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .background(Color.Black.copy(alpha = 0.45f), shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (playing) HikariIcons.Pause else Icons.Default.PlayArrow,
                contentDescription = if (playing) "Paused" else "Playing",
                tint = Color.White,
                modifier = Modifier.size(52.dp),
            )
        }
    }
}

@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
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
    var showTrigger by remember { mutableIntStateOf(0) }

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

    // Scrubber state
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(1L) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubPositionMs by remember { mutableLongStateOf(0L) }
    val wasPlayingBeforeScrub = remember { mutableStateOf(true) }

    LaunchedEffect(item.videoId) {
        while (true) {
            kotlinx.coroutines.delay(500)
            if (!scrubbing) {
                position = player.currentPosition
                duration = player.duration.coerceAtLeast(1L)
            }
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
                        showTrigger++
                    },
                    onDoubleTap = { onLessLikeThis() },
                    onLongPress = { onToggleSave() },
                )
            },
    ) {
        // Video surface — RESIZE_MODE_FIT preserves original aspect ratio.
        // 9:16 content fills portrait; 16:9 content letterboxes (black bars top/bottom).
        // In landscape fullscreen the player width becomes screen width, so 16:9 fills screen.
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setPlayer(player)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Play/Pause indicator centered
        Box(modifier = Modifier.align(Alignment.Center)) {
            PlayPauseIndicator(playing = playing, showTrigger = showTrigger)
        }

        // Bottom gradient overlay — goes from transparent to 65% black
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(240.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f)),
                    ),
                ),
        )

        // Bottom content: scrubber + metadata
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 12.dp),
        ) {
            // Video metadata
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                Text(
                    text = item.channelTitle,
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = item.title,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = "${item.category} · ${item.durationSeconds}s",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelMedium,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Time row
            val displayPositionMs = if (scrubbing) scrubPositionMs else position
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = formatTime(displayPositionMs),
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    text = formatTime(duration),
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelSmall,
                )
            }

            // Interactive scrubber
            Slider(
                value = displayPositionMs.toFloat(),
                onValueChange = { newVal ->
                    if (!scrubbing) {
                        wasPlayingBeforeScrub.value = player.playWhenReady
                        player.playWhenReady = false
                        scrubbing = true
                    }
                    scrubPositionMs = newVal.toLong()
                },
                onValueChangeFinished = {
                    player.seekTo(scrubPositionMs)
                    if (wasPlayingBeforeScrub.value) {
                        player.playWhenReady = true
                        playing = true
                    }
                    scrubbing = false
                },
                valueRange = 0f..duration.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
            )
        }
    }
}
